package enkan.component.jetty;

import enkan.exception.MisconfigurationException;
import enkan.web.websocket.WebSocketHandler;
import enkan.web.websocket.WebSocketSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.lang.reflect.Field;

import static enkan.util.BeanBuilder.builder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JettyComponentTest {
    static boolean isValidationAvailable() {
        try {
            jakarta.validation.Validation.buildDefaultValidatorFactory().close();
            return true;
        } catch (Exception | NoClassDefFoundError e) {
            return false;
        }
    }

    static final WebSocketHandler NOOP_HANDLER = new WebSocketHandler() {
        @Override public void onOpen(WebSocketSession s) {}
        @Override public void onMessage(WebSocketSession s, String m) {}
        @Override public void onClose(WebSocketSession s, int code, String r) {}
        @Override public void onError(WebSocketSession s, Throwable t) {}
    };

    @Test
    @EnabledIf("isValidationAvailable")
    void parameterValidation() {
        assertThatThrownBy(() -> builder(new JettyComponent())
                .set(JettyComponent::setPort, 77777)
                .build())
                .isInstanceOf(MisconfigurationException.class);
    }

    @Test
    void addWebSocketBeforeStartRegistersHandler() throws Exception {
        JettyComponent component = new JettyComponent();
        component.addWebSocket("/ws/echo", NOOP_HANDLER);

        // Verify the handler was registered by inspecting the private map via reflection.
        Field field = JettyComponent.class.getDeclaredField("wsHandlers");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var handlers = (java.util.Map<String, WebSocketHandler>) field.get(component);
        assertThat(handlers).containsKey("/ws/echo");
    }

    @Test
    void addWebSocketAfterStartThrowsMisconfiguration() throws Exception {
        JettyComponent component = new JettyComponent();

        // Force server field to a non-null sentinel to simulate started state.
        Field serverField = JettyComponent.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(component, new org.eclipse.jetty.server.Server());

        assertThatThrownBy(() -> component.addWebSocket("/ws/echo", NOOP_HANDLER))
                .isInstanceOf(MisconfigurationException.class);
    }

    @Test
    void addWebSocketIsChainable() {
        JettyComponent component = new JettyComponent()
                .addWebSocket("/ws/a", NOOP_HANDLER)
                .addWebSocket("/ws/b", NOOP_HANDLER);
        assertThat(component).isNotNull();
    }

    @Test
    void addWebSocketWithNullPathThrowsMisconfiguration() {
        assertThatThrownBy(() -> new JettyComponent().addWebSocket(null, NOOP_HANDLER))
                .isInstanceOf(MisconfigurationException.class);
    }

    @Test
    void addWebSocketWithBlankPathThrowsMisconfiguration() {
        assertThatThrownBy(() -> new JettyComponent().addWebSocket("  ", NOOP_HANDLER))
                .isInstanceOf(MisconfigurationException.class);
    }

    @Test
    void addWebSocketWithNullHandlerThrowsMisconfiguration() {
        assertThatThrownBy(() -> new JettyComponent().addWebSocket("/ws/echo", null))
                .isInstanceOf(MisconfigurationException.class);
    }
}
