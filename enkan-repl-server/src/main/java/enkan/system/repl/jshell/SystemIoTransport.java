package enkan.system.repl.jshell;

import enkan.system.ReplResponse;
import enkan.system.Transport;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;

/**
 * A {@link Transport} implementation that writes to {@code System.out} /
 * {@code System.err}, used as the in-JShell {@code transport} variable when
 * commands are dispatched via {@code __commands.get(name).execute(...)}.
 *
 * <p>Output written here is captured by {@link JShellIoProxy}'s line-capturing
 * stream replacement and then re-broadcast through every registered
 * {@link Transport} on the host side.
 *
 * <p>Historically this class also printed a chunk-delimiter sentinel
 * ({@code "-----------------END------------------"}) after every {@code DONE}
 * response so a consumer could split the stdout stream into per-command
 * chunks. No consumer was ever implemented — the {@link JShellIoProxy} path
 * readers only filtered it out — and in practice the delimiter leaked through
 * to REPL clients whenever the filter was bypassed by an alternate broadcast
 * route. The delimiter has been removed. Completion is now signalled purely
 * via the {@code DONE} status flag on the {@link ReplResponse}, which the host
 * already reads.
 */
public class SystemIoTransport implements Transport {
    private final BufferedReader reader;

    public SystemIoTransport() {
        reader = new BufferedReader(new InputStreamReader(System.in, Charset.defaultCharset()));
    }

    @Override
    public void send(ReplResponse response) {
        synchronized (this) {
            String out = response.getOut();
            if (out != null) {
                System.out.println(out);
                System.out.flush();
            }
            String err = response.getErr();
            if (err != null) {
                System.err.println(err);
                System.err.flush();
            }
        }
    }

    @Override
    public String recv(long timeout) {
        try {
            return reader.readLine();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
