package kotowari.middleware;

import enkan.MiddlewareChain;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.data.PrincipalAvailable;
import enkan.exception.FalteringEnvironmentException;
import enkan.exception.MisconfigurationException;
import enkan.web.middleware.WebMiddleware;
import enkan.security.UserPrincipal;
import kotowari.component.TemplateEngine;
import kotowari.data.TemplatedHttpResponse;
import kotowari.scope.ExportSetting;
import enkan.data.ConversationState;
import enkan.component.builtin.HmacEncoder;
import static kotowari.scope.ExportableScope.*;

import jakarta.enterprise.context.Conversation;
import jakarta.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Renders HTML using by template.
 *
 * @author kawasima
 */
@enkan.annotation.Middleware(name = "template", dependencies = {"contentType"})
public class RenderTemplateMiddleware implements WebMiddleware {
    @Inject
    private HmacEncoder hmacEncoder;

    @Inject
    private TemplateEngine<?> templateEngine;

    private Map<String, Function<List<?>, Object>> userFunctions = Collections.emptyMap();

    private final ExportSetting exports = ExportSetting.DEFAULT_EXPORTS;
    private static final Function<List<?>, Object> HAS_PERMISSION = arguments -> {
        if (arguments.size() == 2) {
            Object principal = arguments.get(0);
            String permission = Objects.toString(arguments.get(1));
            if (principal instanceof UserPrincipal up) {
                return up.hasPermission(permission);
            } else {
                throw new MisconfigurationException("kotowari.HAS_PERMISSION_FIRST_ARG", "hasPermission");
            }
        } else {
            throw new MisconfigurationException("kotowari.HAS_PERMISSION_WRONG_ARGS");
        }
    };

    private static final Function<List<?>, Object> HAS_ANY_PERMISSIONS = arguments -> {
        if (arguments.size() >= 2) {
            Object principal = arguments.getFirst();
            if (principal instanceof UserPrincipal up) {
                return arguments.subList(1, arguments.size())
                        .stream()
                        .anyMatch(p -> up.hasPermission(Objects.toString(p)));
            } else {
                throw new MisconfigurationException("kotowari.HAS_PERMISSION_FIRST_ARG", "hasAnyPermission");
            }
        } else {
            throw new MisconfigurationException("kotowari.HAS_ANY_PERMISSION_WRONG_ARGS");
        }
    };

    private static final Function<List<?>, Object> HAS_ALL_PERMISSIONS = arguments -> {
        if (arguments.size() >= 2) {
            Object principal = arguments.getFirst();
            if (principal instanceof UserPrincipal up) {
                return arguments.subList(1, arguments.size())
                        .stream()
                        .allMatch(p -> up.hasPermission(Objects.toString(p)));
            } else {
                throw new MisconfigurationException("kotowari.HAS_PERMISSION_FIRST_ARG", "hasAllPermission");
            }
        } else {
            throw new MisconfigurationException("kotowari.HAS_ALL_PERMISSION_WRONG_ARGS");
        }
    };

    protected void render(TemplatedHttpResponse response) {
        InputStream is = response.getBodyAsStream();
        ReadableByteChannel channel = Channels.newChannel(is);
        ByteBuffer buf = ByteBuffer.allocate(4096);
        buf.mark();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            int read;
            while ((read = channel.read(buf)) >= 0) {
                baos.write(buf.array(), 0, read);
                buf.reset();
            }
            String body = baos.toString(StandardCharsets.UTF_8);
            response.setBody(body);
        } catch (IOException ex) {
            throw new FalteringEnvironmentException(ex);
        }
    }

    @Override
    public <NNREQ, NNRES> HttpResponse handle(HttpRequest request, MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
        HttpResponse response = castToHttpResponse(chain.next(request));
        if (response instanceof TemplatedHttpResponse tres) {
            if (exports.contains(REQUEST)) {
                tres.getContext().put(exports.getExportName(REQUEST), request);
            }

            if (exports.contains(PARAMS)) {
                tres.getContext().put(exports.getExportName(PARAMS), request.getParams());
            }

            if (exports.contains(USER_PRINCIPAL)) {
                Stream.of(request)
                        .filter(PrincipalAvailable.class::isInstance)
                        .map(PrincipalAvailable.class::cast)
                        .findAny()
                        .ifPresent(principal -> tres.getContext()
                                .put(exports.getExportName(USER_PRINCIPAL), principal.getPrincipal()));
                tres.getContext().put("hasPermission", templateEngine.createFunction(HAS_PERMISSION));
                tres.getContext().put("hasAnyPermissions", templateEngine.createFunction(HAS_ANY_PERMISSIONS));
                tres.getContext().put("hasAllPermissions", templateEngine.createFunction(HAS_ALL_PERMISSIONS));
            }
            if (exports.contains(SESSION)) {
                tres.getContext().put(exports.getExportName(SESSION), request.getSession());
            }

            if (exports.contains(CONVERSATION)) {
                Conversation conversation = request.getConversation();
                if (conversation != null) {
                    if (!request.getConversation().isTransient()) {
                        String token = conversation.getId() + "$"
                                + hmacEncoder.encodeToHex(conversation.getId() + "$" + conversation.getTimeout())
                                + "$" + conversation.getTimeout();
                        tres.getContext().put("conversationToken", token);
                    }
                    tres.getContext().put(exports.getExportName(CONVERSATION), conversation);
                }
            }

            if (exports.contains(CONVERSATION_STATE)) {
                ConversationState conversationState = request.getConversationState();
                tres.getContext().put(exports.getExportName(CONVERSATION_STATE), conversationState);
            }

            userFunctions.forEach((key, value) -> tres.getContext().put(key, templateEngine.createFunction(value)));
            render(tres);
        }
        return response;
    }

    public void setUserFunctions(Map<String, Function<List<?>, Object>> userFunctions) {
        this.userFunctions = userFunctions;
    }
}
