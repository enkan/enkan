package enkan.web.middleware;

import enkan.MiddlewareChain;
import enkan.annotation.Middleware;
import enkan.web.collection.Headers;
import enkan.component.builtin.HmacEncoder;
import enkan.data.ConversationState;
import enkan.web.data.DefaultConversation;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.web.middleware.session.KeyValueStore;
import enkan.web.middleware.session.MemoryStore;

import jakarta.enterprise.context.Conversation;
import jakarta.inject.Inject;
import java.util.Objects;
import java.util.function.Function;

import static enkan.util.BeanBuilder.builder;
import static enkan.util.ThreadingUtils.some;

/**
 * Middleware that creates or restores a long-running conversation.
 *
 * <p>A conversation is a server-side state store (backed by a {@link KeyValueStore})
 * that spans multiple requests.  Each non-GET request must carry a signed
 * conversation token (by default in the {@code __conversation-token} form
 * parameter) whose HMAC signature is verified by an injected
 * {@link enkan.component.builtin.HmacEncoder}.
 *
 * <p>The token format is {@code <id>$<hmac>$<expires>}, where {@code expires}
 * is a Unix epoch millisecond timestamp ({@code -1} means no expiry).
 *
 * @author kawasima
 */
@Middleware(name = "conversation")
public class ConversationMiddleware implements WebMiddleware {
    private KeyValueStore store;
    private Function<HttpRequest, String> readTokenFunc;

    @Inject
    private HmacEncoder hmacEncoder;

    private static final java.util.regex.Pattern TOKEN_SPLIT = java.util.regex.Pattern.compile("\\$");

    private static final Function<HttpRequest, String> DEFAULT_READ_TOKEN_FUNC = request ->
            some(request, HttpRequest::getFormParams,
                    p -> p.get("__conversation-token"),
                    Objects::toString).orElse(null);

    public ConversationMiddleware() {
        store = new MemoryStore();
        readTokenFunc = DEFAULT_READ_TOKEN_FUNC;
    }

    private boolean isGetRequest(HttpRequest request) {
        String method = request.getRequestMethod();
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method);
    }

    private ConversationToken parseToken(String token) {
        if (token == null) {
            return new ConversationToken(null, "invalid", "0");
        }
        String[] tokens = TOKEN_SPLIT.split(token.trim(), 3);

        // If token contains two '$' characters, it's invalid.
        if (tokens.length != 3) {
            return new ConversationToken(null, "invalid", "0");
        }
        return new ConversationToken(tokens[0], tokens[1], tokens[2]);
    }

    @Override
    public <NNREQ, NNRES> HttpResponse handle(HttpRequest request, MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
        ConversationToken token = parseToken(readTokenFunc.apply(request));

        if (!isGetRequest(request) && !token.isValid()) {
            return builder(HttpResponse.of("Invalid conversation."))
                    .set(HttpResponse::setHeaders, Headers.of("Content-Type", "text/plain"))
                    .set(HttpResponse::setStatus, 403)
                    .build();
        }

        Conversation conversation;
        if (token.id == null) {
            conversation = new DefaultConversation();
        } else {
            conversation = new DefaultConversation(token.id);
            ConversationState state = (ConversationState) store.read(token.id);
            request.setConversationState(state);
        }
        request.setConversation(conversation);

        HttpResponse response = castToHttpResponse(chain.next(request));
        if (conversation.isTransient()) {
            if (conversation.getId() != null) {
                store.delete(conversation.getId());
            }
        } else if (response.getConversationState() != null) {
            store.write(conversation.getId(), response.getConversationState());
        }
        return response;
    }

    private class ConversationToken {
        private final String id;
        private final String hash;
        private long expires;

        ConversationToken(String id, String hash, String expires) {
            this.id = id;
            this.hash = hash;
            try {
                this.expires = Long.parseLong(expires);
            } catch (Exception e) {
                this.expires = 0;
            }
        }

        boolean isValid() {
            return id != null && Objects.equals(hash, hmacEncoder.encodeToHex(id + "$" + expires))
                    && (expires < 0 || System.currentTimeMillis() < expires);
        }

        @Override
        public String toString() {
            return id + "$" + hash + "$" + expires;
        }
    }

    public void setStore(KeyValueStore store) {
        this.store = store;
    }

    public void setReadTokenFunc(Function<HttpRequest, String> readTokenFunc) {
        this.readTokenFunc = readTokenFunc;
    }
}
