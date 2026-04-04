package enkan.system;

import enkan.component.ComponentLifecycle;
import enkan.component.SystemComponent;
import org.crac.Context;
import org.crac.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EnkanSystemCRaCTest {

    /** Minimal in-process CRaC context for unit tests. */
    private static class TestCRaCContext extends Context<Resource> {
        private Resource registered;

        @Override
        public void register(Resource r) {
            this.registered = r;
        }

        @Override
        public void beforeCheckpoint(Context<? extends Resource> ctx) {}

        @Override
        public void afterRestore(Context<? extends Resource> ctx) {}

        void checkpoint() {
            if (registered != null) {
                try {
                    registered.beforeCheckpoint(this);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }

        void restore() {
            if (registered != null) {
                try {
                    registered.afterRestore(this);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private static class TrackingComponent extends SystemComponent<TrackingComponent> {
        final List<String> calls = new ArrayList<>();

        @Override
        protected ComponentLifecycle<TrackingComponent> lifecycle() {
            return new ComponentLifecycle<>() {
                @Override
                public void start(TrackingComponent component) {
                    component.calls.add("start");
                }

                @Override
                public void stop(TrackingComponent component) {
                    component.calls.add("stop");
                }
            };
        }
    }

    private TrackingComponent comp;
    private EnkanSystem system;
    private TestCRaCContext cracCtx;

    @BeforeEach
    void setup() {
        comp = new TrackingComponent();
        system = EnkanSystem.of("c", comp);
        cracCtx = new TestCRaCContext();
    }

    @Test
    void beforeCheckpointStopsStartedSystem() throws Exception {
        system.start();
        system.registerCrac(cracCtx);

        cracCtx.checkpoint();

        assertThat(comp.calls).containsExactly("start", "stop");
        assertThat(system.isStarted()).isFalse();
    }

    @Test
    void afterRestoreStartsStoppedSystem() throws Exception {
        system.start();
        system.registerCrac(cracCtx);

        cracCtx.checkpoint();
        cracCtx.restore();

        assertThat(comp.calls).containsExactly("start", "stop", "start");
        assertThat(system.isStarted()).isTrue();
    }

    @Test
    void beforeCheckpointIsNoopWhenAlreadyStopped() throws Exception {
        system.registerCrac(cracCtx);  // not started

        cracCtx.checkpoint();

        assertThat(comp.calls).isEmpty();
    }

    @Test
    void afterRestoreIsNoopWhenAlreadyStarted() throws Exception {
        system.start();
        system.registerCrac(cracCtx);

        cracCtx.restore();  // already started, should be no-op

        assertThat(comp.calls).containsExactly("start");
    }

    @Test
    void multipleCheckpointRestoreCyclesWork() throws Exception {
        system.start();
        system.registerCrac(cracCtx);

        cracCtx.checkpoint();
        cracCtx.restore();
        cracCtx.checkpoint();
        cracCtx.restore();

        long stopCount  = comp.calls.stream().filter("stop"::equals).count();
        long startCount = comp.calls.stream().filter("start"::equals).count();
        assertThat(stopCount).isEqualTo(2);
        assertThat(startCount).isEqualTo(3); // initial + 2 restores
    }
}
