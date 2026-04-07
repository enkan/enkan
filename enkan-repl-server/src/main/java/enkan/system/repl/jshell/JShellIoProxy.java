package enkan.system.repl.jshell;

import enkan.system.ReplResponse;
import enkan.system.Transport;
import enkan.system.repl.ZmqServerTransport;
import org.zeromq.ZFrame;
import org.zeromq.ZMQ;

import java.io.*;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Proxies input/output operations for a JShell environment.
 *
 * <p>
 * This class manages communication between JShell and ZeroMQ transports,
 * redirecting standard output and error streams to connected clients.
 * </p>
 *
 * <p>
 * JShell snippet output is captured via piped streams.  Application-level
 * output ({@code System.out}/{@code System.err}) is captured by replacing
 * the global streams with a line-buffered {@link LineCapturingOutputStream}
 * that enqueues complete lines into a {@link BlockingQueue}, which is safe
 * for concurrent writes from arbitrary threads (e.g. Flyway, HikariCP).
 * </p>
 *
 * @author kawasima
 */
public class JShellIoProxy {
    /** PrintStream for capturing JShell snippet output */
    private final PrintStream out;
    private final PrintStream err;
    private final BufferedReader outReader;
    private final BufferedReader errReader;
    private final Map<ZFrame, ZmqServerTransport> transports;
    /** Generic transports keyed by an arbitrary identifier (e.g. WebSocket session ID). */
    private final Map<Object, Transport> extraTransports = new ConcurrentHashMap<>();
    private final ExecutorService ioThreadPool;
    private final PrintStream originalOut;
    private final PrintStream originalErr;
    /** Queues for System.out/err line capture */
    private final BlockingQueue<String> sysOutQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> sysErrQueue = new LinkedBlockingQueue<>();
    private final LineCapturingOutputStream sysOutCapture;
    private final LineCapturingOutputStream sysErrCapture;

    /**
     * An OutputStream that buffers bytes and, on each newline, enqueues
     * the complete line into a {@link BlockingQueue}.  All output is also
     * forwarded to an original stream so that server-side console output
     * is preserved.
     *
     * <p>Thread-safe: multiple threads may write concurrently.</p>
     */
    private static class LineCapturingOutputStream extends OutputStream {
        private final OutputStream original;
        private final BlockingQueue<String> queue;
        private final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        private volatile boolean closed;

        LineCapturingOutputStream(OutputStream original, BlockingQueue<String> queue) {
            this.original = original;
            this.queue = queue;
        }

        @Override
        public synchronized void write(int b) throws IOException {
            original.write(b);
            if (closed) return;
            if (b == '\n') {
                flushLine();
            } else {
                buf.write(b);
            }
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) throws IOException {
            original.write(b, off, len);
            if (closed) return;
            for (int i = off; i < off + len; i++) {
                if (b[i] == '\n') {
                    flushLine();
                } else {
                    buf.write(b[i]);
                }
            }
        }

        private void flushLine() {
            String line = buf.toString();
            buf.reset();
            // Strip trailing \r for Windows-style line endings
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            queue.offer(line);
        }

        @Override
        public synchronized void flush() throws IOException {
            original.flush();
        }

        @Override
        public void close() throws IOException {
            closed = true;
            // Do not close the original stream
        }
    }

    public JShellIoProxy() {
        transports = new ConcurrentHashMap<>();
        ioThreadPool = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("enkan-io-proxy");
            return t;
        });
        originalOut = System.out;
        originalErr = System.err;
        PipedOutputStream outPipe = new PipedOutputStream();
        PipedOutputStream errPipe = new PipedOutputStream();
        try {
            outReader = new BufferedReader(new InputStreamReader(new PipedInputStream(outPipe)));
            out = new PrintStream(outPipe);
            errReader = new BufferedReader(new InputStreamReader(new PipedInputStream(errPipe)));
            err = new PrintStream(errPipe);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        // Replace System.out/err with line-capturing streams
        sysOutCapture = new LineCapturingOutputStream(originalOut, sysOutQueue);
        sysErrCapture = new LineCapturingOutputStream(originalErr, sysErrQueue);
        System.setOut(new PrintStream(sysOutCapture, true));
        System.setErr(new PrintStream(sysErrCapture, true));
    }

    public PrintStream forJShellPrintStream() {
        return out;
    }

    public PrintStream forJShellErrorStream() {
        return err;
    }

    public ZmqServerTransport listen(ZMQ.Socket socket, ZFrame clientAddress) {
        return transports.computeIfAbsent(clientAddress,
                addr -> new ZmqServerTransport(socket, addr));
    }

    public void unlisten(ZFrame clientAddress) {
        ZmqServerTransport transport = transports.remove(clientAddress);
        transport.close();
    }

    /**
     * Register a generic (non-ZMQ) transport for broadcast.
     *
     * @param key       a unique identifier (e.g. WebSocket session ID)
     * @param transport the transport to register
     */
    public void register(Object key, Transport transport) {
        extraTransports.put(key, transport);
    }

    /**
     * Unregister a generic transport.
     *
     * @param key the identifier used when registering
     */
    public void unregister(Object key) {
        extraTransports.remove(key);
    }

    private void broadcast(ReplResponse response) {
        transports.values().forEach(t -> t.send(response));
        extraTransports.values().forEach(t -> t.send(response));
    }

    public void start() {
        // JShell snippet stdout
        ioThreadPool.submit(() -> {
            while(!Thread.currentThread().isInterrupted()) {
                try {
                    String line = outReader.readLine();
                    if (line == null) break;
                    broadcast(ReplResponse.withOut(line));
                } catch (IOException e) {
                    broadcast(ReplResponse.withErr(e.getMessage()));
                    break;
                }
            }
        });

        // JShell snippet stderr
        ioThreadPool.submit(() -> {
            while(!Thread.currentThread().isInterrupted()) {
                try {
                    String line = errReader.readLine();
                    if (line == null) break;
                    broadcast(ReplResponse.withErr(line));
                } catch (IOException e) {
                    broadcast(ReplResponse.withErr(e.getMessage()));
                    break;
                }
            }
        });

        // Application stdout (SLF4J, System.out.println from components, etc.)
        ioThreadPool.submit(() -> {
            while(!Thread.currentThread().isInterrupted()) {
                try {
                    String line = sysOutQueue.poll(1, TimeUnit.SECONDS);
                    if (line == null) continue;
                    broadcast(ReplResponse.withOut(line));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        // Application stderr
        ioThreadPool.submit(() -> {
            while(!Thread.currentThread().isInterrupted()) {
                try {
                    String line = sysErrQueue.poll(1, TimeUnit.SECONDS);
                    if (line == null) continue;
                    broadcast(ReplResponse.withErr(line));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    public void stop() {
        // Mark capture streams as closed so subsequent writes go only to
        // the original streams (no queueing) — prevents shutdown-time blocking.
        sysOutCapture.closed = true;
        sysErrCapture.closed = true;
        // Restore original System streams
        System.setOut(originalOut);
        System.setErr(originalErr);
        ioThreadPool.shutdown();
        try {
            if (!ioThreadPool.awaitTermination(3, java.util.concurrent.TimeUnit.SECONDS)) {
                ioThreadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            ioThreadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        try {
            out.close();
            err.close();
            outReader.close();
            errReader.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
