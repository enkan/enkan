package enkan.component;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * Abstract base component for submitting background jobs.
 *
 * <p>This follows the Enkan component-extraction pattern (like
 * {@link WebServerComponent} or {@code TemplateEngine}): middleware injects
 * the abstract type via {@code @Inject}, and the concrete implementation
 * is chosen at system-assembly time.</p>
 *
 * <p>The default in-process implementation is {@code LocalJobExecutor},
 * which uses virtual threads.  Alternative implementations can dispatch
 * to external job queues (SQS, Redis, etc.) by extending this class.</p>
 *
 * @param <T> the concrete subclass type for type-safe lifecycle management
 * @author kawasima
 */
public abstract class JobExecutor<T extends JobExecutor<T>> extends SystemComponent<T> {

    /**
     * Submits a value-returning task for execution.
     *
     * @param <R>  the result type
     * @param task the task to execute
     * @return a Future representing the pending result
     */
    public abstract <R> Future<R> submit(Callable<R> task);

    /**
     * Submits a fire-and-forget task for execution.
     *
     * @param task the task to execute
     * @return a Future representing the pending completion
     */
    public abstract Future<?> submit(Runnable task);
}
