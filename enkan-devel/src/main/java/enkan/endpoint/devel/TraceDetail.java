package enkan.endpoint.devel;

import enkan.web.collection.Headers;
import enkan.collection.Parameters;
import net.unit8.moshas.MoshasEngine;
import net.unit8.moshas.Snippet;
import net.unit8.moshas.Template;

import static net.unit8.moshas.RenderUtils.text;

/**
 * @author kawasima
 */
public class TraceDetail extends MoshasPage {
    private final Template traceDetailPage;

    private String elapseString(Object elapse, String prefix) {
        if (!(elapse instanceof Long)) {
            return "-";
        } else {
            return prefix + " " + elapse + "ms";
        }
    }
    public TraceDetail(MoshasEngine moshas) {
        Snippet parameterSnippet = moshas.describe("templates/trace/show.html", "#parameters tbody tr#parameter", t -> {
            t.select("td.name", text("parameter", "key"));
            t.select("td.value", text("parameter", "value"));
        });

        Snippet headerSnippet = moshas.describe("templates/trace/show.html", "#headers tbody tr#header", t -> {
            t.select("td.name", text("header", "key"));
            t.select("td.value", text("header", "value"));
        });

        Snippet traceListSnippet = moshas.describe("templates/trace/show.html", "#traces > div#trace", t -> {
            t.select("th.middleware-name", text("traceEntry", "middlewareName"));
            t.select("td.inbound", (el, ctx) ->
                    el.text(elapseString(ctx.get("traceEntry", "inboundElapse"), "⬇")));
            t.select("td.outbound", (el, ctx) ->
                    el.text(elapseString(ctx.get("traceEntry", "outboundElapse"), "⬆")));
        });
        traceDetailPage = moshas.describe("templates/trace/show.html", t -> {
            t.select("#primer-link", (el, ctx) -> el.remove());
            t.select("#primer", (el, ctx) -> el.text(primer));
            t.select("table#parameters > tbody", (el, ctx) -> {
                el.empty();
                ((Parameters) ctx.get("parameters")).entrySet().forEach(
                        stel -> ctx.localScope("parameter", stel,
                                () -> el.appendChild(parameterSnippet.render(ctx)))
                );
            });
            t.select("table#headers > tbody", (el, ctx) -> {
                el.empty();
                ((Headers) ctx.get("headers")).entrySet().forEach(
                        stel -> ctx.localScope("header", stel,
                                () -> el.appendChild(headerSnippet.render(ctx)))
                );
            });
            t.select("#traces", (el, ctx) -> {
                el.empty();
                ctx.getCollection("traces").forEach(
                        stel -> ctx.localScope("traceEntry", stel,
                                () -> el.appendChild(traceListSnippet.render(ctx)))
                );
            });
        });
    }

    @Override
    public Template getTemplate() {
        return traceDetailPage;
    }
}
