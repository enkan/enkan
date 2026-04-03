package enkan.component;

import enkan.exception.MisconfigurationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;

class LocalJobExecutorTest {

    private LocalJobExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new LocalJobExecutor();
        executor.setName("test-job");
        executor.lifecycle().start(executor);
    }

    @AfterEach
    void tearDown() {
        executor.lifecycle().stop(executor);
    }

    @Test
    void healthIsUpAfterStart() {
        assertThat(executor.health()).isEqualTo(HealthStatus.UP);
    }

    @Test
    void healthIsDownBeforeStart() {
        LocalJobExecutor fresh = new LocalJobExecutor();
        assertThat(fresh.health()).isEqualTo(HealthStatus.DOWN);
    }

    @Test
    void healthIsDownAfterStop() {
        LocalJobExecutor e = new LocalJobExecutor();
        e.lifecycle().start(e);
        e.lifecycle().stop(e);
        assertThat(e.health()).isEqualTo(HealthStatus.DOWN);
    }

    @Test
    void submitCallableReturnsResult() throws Exception {
        Future<String> future = executor.submit(() -> "hello");
        assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo("hello");
    }

    @Test
    void submitRunnableCompletes() throws Exception {
        AtomicBoolean ran = new AtomicBoolean(false);
        Future<?> future = executor.submit(() -> ran.set(true));
        future.get(5, TimeUnit.SECONDS);
        assertThat(ran).isTrue();
    }

    @Test
    void submittedAndCompletedCountsAreTracked() throws Exception {
        int n = 5;
        Future<?>[] futures = new Future<?>[n];
        for (int i = 0; i < n; i++) {
            futures[i] = executor.submit(() -> {});
        }
        for (Future<?> f : futures) {
            f.get(5, TimeUnit.SECONDS);
        }

        assertThat(executor.getSubmittedCount()).isEqualTo(n);
        assertThat(executor.getCompletedCount()).isEqualTo(n);
    }

    @Test
    void gracefulShutdownDrainsTasks() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        AtomicBoolean completed = new AtomicBoolean(false);

        executor.submit(() -> {
            started.countDown();
            try {
                finish.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            completed.set(true);
        });

        started.await(5, TimeUnit.SECONDS);
        // Release the task so shutdown can drain it
        finish.countDown();

        executor.setShutdownTimeoutMs(5_000);
        executor.lifecycle().stop(executor);

        assertThat(completed).isTrue();
        assertThat(executor.health()).isEqualTo(HealthStatus.DOWN);
    }

    @Test
    void submitAfterStopThrowsRejectedExecution() {
        LocalJobExecutor e = new LocalJobExecutor();
        e.lifecycle().start(e);
        e.lifecycle().stop(e);
        assertThatThrownBy(() -> e.submit(() -> "too late"))
                .isInstanceOf(RejectedExecutionException.class);
    }

    @Test
    void submitBeforeStartThrowsRejectedExecution() {
        LocalJobExecutor fresh = new LocalJobExecutor();
        assertThatThrownBy(() -> fresh.submit(() -> {}))
                .isInstanceOf(RejectedExecutionException.class);
    }

    @Test
    void negativeShutdownTimeoutThrowsMisconfiguration() {
        assertThatThrownBy(() -> executor.setShutdownTimeoutMs(-1))
                .isInstanceOf(MisconfigurationException.class);
    }
}
