package enkan.middleware.devel;

import enkan.MiddlewareChain;
import enkan.annotation.Middleware;
import enkan.web.collection.Headers;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.exception.MisconfigurationException;
import enkan.exception.UnreachableException;

import enkan.web.middleware.WebMiddleware;
import enkan.web.util.HttpResponseUtils;
import net.unit8.moshas.MoshasEngine;
import net.unit8.moshas.Snippet;
import net.unit8.moshas.Template;
import net.unit8.moshas.context.Context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.stream.Collectors;

import static enkan.util.BeanBuilder.builder;
import static net.unit8.moshas.RenderUtils.text;

/**
 * Middleware to render stacktrace.
 * @author kawasima
 */
@Middleware(name = "stacktrace")
public class StacktraceMiddleware implements WebMiddleware {
    private static final Logger LOG = LoggerFactory.getLogger(StacktraceMiddleware.class);

    private final MoshasEngine moshas = new MoshasEngine();

    private String primer;
    {
        try (InputStream is = getClass().getResourceAsStream("/css/primer.css");
             InputStreamReader isReader = new InputStreamReader(Objects.requireNonNull(is), StandardCharsets.ISO_8859_1);
            BufferedReader reader = new BufferedReader(isReader)) {
            primer = reader.lines().collect(Collectors.joining());
        } catch (Exception e) {
            primer = "";
        }
    }

    /**
     * Represents the page is rendered by the moshas engine.
     */
    private final Snippet stackTraceElementSnippet = moshas.describe("templates/stacktrace.html", ".trace > table > tbody > tr", s -> {
        s.select("td.source", (el, ctx) ->
                el.text(ctx.getString("stackTraceElement", "fileName") +
                        ":" +
                        ctx.getString("stackTraceElement", "lineNumber")));
        s.select("td.method", (el, ctx) ->
                el.text(ctx.getString("stackTraceElement", "className") +
                        "." +
                        ctx.getString("stackTraceElement", "methodName")));
    });

    /**
     * Renders HTML using by template.
     * @param template Template
     * @param args Arguments
     * @return HttpResponse
     */
    protected HttpResponse render(Template template, Object... args) {
        StringWriter sw = new StringWriter();
        Context ctx = new Context();
        for (int i = 0; i < args.length; i += 2) {
            ctx.setVariable(Objects.toString(args[i], ""), args[i+1]);
        }
        template.render(ctx, sw);
        HttpResponse response = HttpResponse.of(sw.toString());
        HttpResponseUtils.contentType(response, "text/html");
        return response;
    }

    /**
     * Casts the response to HttpResponse.
     * @param ex Exception
     * @return HttpResponse
     */
    protected HttpResponse htmlUnreachableExResponse(UnreachableException ex) {
        Template template = moshas.describe("templates/unreachable.html", t -> {});
        return builder(render(template))
                .set(HttpResponse::setStatus, 500)
                .set(HttpResponse::setHeaders, Headers.of("Content-Type", "text/html; charset=UTF-8"))
                .build();
    }

    /**
     * Casts the response to HttpResponse.
     * @param ex Exception
     * @param request HttpRequest
     * @return HttpResponse
     */
    protected HttpResponse htmlMisconfigExResponse(MisconfigurationException ex, HttpRequest request) {
        String primer = this.primer;
        Template template = moshas.describe("templates/misconfiguration.html", t -> {
            t.select("#primer", (el, ctx) -> el.text(primer));
            t.select(".problem", text("exception", "problem"));
            t.select(".solution", text("exception", "solution"));
            t.select(".trace table tbody", (el, ctx) -> {
                el.empty();
                ctx.getCollection("exception", "stackTrace").forEach(
                        stel -> ctx.localScope("stackTraceElement", stel,
                                () -> el.appendChild(stackTraceElementSnippet.render(ctx))));
            });
            t.select(".request .uri", text("request", "uri"));
            t.select(".request .server-name", text("request", "serverName"));
            t.select(".request .server-port", text("request", "serverPort"));
            t.select(".request .remote-addr", text("request", "remoteAddr"));
            t.select(".request .parameters", text("request", "params"));
        });
        return builder(render(template, "exception", ex, "request", request))
                .set(HttpResponse::setStatus, 500)
                .set(HttpResponse::setHeaders, Headers.of("Content-Type", "text/html; charset=UTF-8"))
                .build();
    }

    /**
     * Casts the response to HttpResponse.
     * @param ex Exception
     * @return HttpResponse
     */
    protected HttpResponse htmlExResponse(Throwable ex) {
        Template template = moshas.describe("templates/stacktrace.html", t -> {
            t.select("#class-name", (el, ctx) -> el.text(
                    ctx.get("exception").getClass().getName()));
            t.select(".message", (el, ctx) -> el.text(ctx.getString("exception","message")));
            t.select(".trace table tbody", (el, ctx) -> {
                el.empty();
                ctx.getCollection("exception", "stackTrace").forEach(
                        stel -> ctx.localScope("stackTraceElement", stel,
                                () -> el.appendChild(stackTraceElementSnippet.render(ctx))));
            });
        });

        return builder(render(template, "exception", ex))
                .set(HttpResponse::setStatus, 500)
                .set(HttpResponse::setHeaders, Headers.of("Content-Type", "text/html; charset=UTF-8"))
                .build();
    }

    /**
     * Casts the response to HttpResponse.
     * @param request HttpRequest
     * @param ex Throwable
     * @return HttpResponse
     */
    protected HttpResponse exResponse(HttpRequest request, Throwable ex) {
        String accept = request.getHeaders().get("accept");
        if (accept != null && accept.stripLeading().regionMatches(true, 0, "text/javascript", 0, "text/javascript".length())) {
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            return builder(HttpResponse.of(sw.toString()))
                    .set(HttpResponse::setStatus, 500)
                    .set(HttpResponse::setHeaders, Headers.of("Content-Type", "text/javascript"))
                    .build();
        } else {
            if (ex instanceof UnreachableException ue) {
                return htmlUnreachableExResponse(ue);
            } else if (ex instanceof MisconfigurationException me) {
                return htmlMisconfigExResponse(me, request);
            } else {
                return htmlExResponse(ex);
            }

        }
    }

    /**
     * Casts the response to HttpResponse.
     * @param request   A request object
     * @param chain A chain of middlewares
     * @return HttpResponse
     * @param <NNREQ> A request object
     * @param <NNRES> A response object
     */
    @Override
    public <NNREQ, NNRES> HttpResponse handle(HttpRequest request, MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
        try {
            return castToHttpResponse(chain.next(request));
        } catch (Throwable t) {
            LOG.error("Unhandled exception", t);
            return exResponse(request, t);
        }
    }
}
