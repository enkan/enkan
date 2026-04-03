package enkan.component;

import enkan.exception.MisconfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;

/**
 * Default in-process {@link JobExecutor} that uses virtual threads.
 *
 * <p>Each submitted task runs on its own virtual thread via
 * {@link Executors#newThreadPerTaskExecutor}.  This makes it suitable for
 * both short-lived background jobs and long-lived streaming connections
 * (SSE, WebSocket) where a virtual thread blocks cheaply.</p>
 *
 * <p>Register in the system and wire to middleware:</p>
 * <pre>{@code
 * EnkanSystem.of(
 *     "jobExecutor", new LocalJobExecutor(),
 *     "app",         new ApplicationComponent<>(...),
 *     "http",        new JettyComponent()
 * ).relationships(
 *     component("app").using("jobExecutor"),
 *     component("http").using("app")
 * );
 * }</pre>
 *
 * @author kawasima
 */
public class LocalJobExecutor extends JobExecutor<LocalJobExecutor> implements HealthCheckable {
    private static final Logger LOG = LoggerFactory.getLogger(LocalJobExecutor.class);

    private String name = "enkan-job";
    private long shutdownTimeoutMs = 30_000;

    private volatile ExecutorService executor;
    private volatile boolean stopping = false;

    private final LongAdder submittedCount = new LongAdder();
    private final LongAdder completedCount = new LongAdder();

    @Override
    public <R> Future<R> submit(Callable<R> task) {
        ExecutorService exec = this.executor;
        if (exec == null || exec.isShutdown()) {
            throw new RejectedExecutionException("LocalJobExecutor is not running");
        }
        Future<R> future = exec.submit(() -> {
            try {
                return task.call();
            } finally {
                completedCount.increment();
            }
        });
        submittedCount.increment();
        return future;
    }

    @Override
    public Future<?> submit(Runnable task) {
        ExecutorService exec = this.executor;
        if (exec == null || exec.isShutdown()) {
            throw new RejectedExecutionException("LocalJobExecutor is not running");
        }
        Future<?> future = exec.submit(() -> {
            try {
                task.run();
            } finally {
                completedCount.increment();
            }
        });
        submittedCount.increment();
        return future;
    }

    /**
     * Returns the number of tasks submitted since start.
     */
    public long getSubmittedCount() {
        return submittedCount.sum();
    }

    /**
     * Returns the number of tasks that have completed since start.
     */
    public long getCompletedCount() {
        return completedCount.sum();
    }

    @Override
    public HealthStatus health() {
        if (stopping) return HealthStatus.STOPPING;
        if (executor == null || executor.isShutdown()) return HealthStatus.DOWN;
        return HealthStatus.UP;
    }

    @Override
    protected ComponentLifecycle<LocalJobExecutor> lifecycle() {
        return new ComponentLifecycle<>() {
            @Override
            public void start(LocalJobExecutor component) {
                if (component.executor == null) {
                    component.executor = Executors.newThreadPerTaskExecutor(
                            Thread.ofVirtual().name(component.name + "-", 0).factory());
                    LOG.info("LocalJobExecutor started (name={})", component.name);
                }
            }

            @Override
            public void stop(LocalJobExecutor component) {
                if (component.executor != null) {
                    component.stopping = true;
                    try {
                        component.executor.shutdown();
                        LOG.info("LocalJobExecutor shutting down (timeout={}ms)", component.shutdownTimeoutMs);
                        if (!component.executor.awaitTermination(
                                component.shutdownTimeoutMs, TimeUnit.MILLISECONDS)) {
                            LOG.warn("Tasks did not complete within {}ms, forcing shutdown",
                                    component.shutdownTimeoutMs);
                            component.executor.shutdownNow();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        component.executor.shutdownNow();
                    } finally {
                        component.executor = null;
                        component.stopping = false;
                    }
                }
            }
        };
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setShutdownTimeoutMs(long shutdownTimeoutMs) {
        if (shutdownTimeoutMs < 0) {
            throw new MisconfigurationException("core.INVALID_ARGUMENT", "shutdownTimeoutMs", shutdownTimeoutMs);
        }
        this.shutdownTimeoutMs = shutdownTimeoutMs;
    }
}
