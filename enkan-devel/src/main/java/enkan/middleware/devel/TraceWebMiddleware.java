package enkan.middleware.devel;

import enkan.MiddlewareChain;
import enkan.annotation.Middleware;
import enkan.web.collection.Headers;
import enkan.collection.Parameters;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.web.middleware.WebMiddleware;
import enkan.data.TraceLog;
import enkan.data.Traceable;
import enkan.endpoint.devel.TraceDetail;
import enkan.endpoint.devel.TraceList;
import enkan.endpoint.devel.TraceRouting;

import enkan.web.middleware.session.KeyValueStore;
import enkan.web.middleware.session.MemoryStore;
import enkan.util.MixinUtils;
import net.unit8.moshas.MoshasEngine;

import org.jspecify.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.Objects;

/**
 * Shows the trace logs of the requests.
 *
 * @author kawasima
 */
@Middleware(name = "traceWeb", mixins = Traceable.class)
public class TraceWebMiddleware implements WebMiddleware, Closeable {
    private final LinkedList<LogKey> idList;
    private final KeyValueStore store;
    private String mountPath = "/x-enkan/requests";
    private TraceRouting traceRouting;
    private final TraceList traceList;
    private final TraceDetail traceDetail;
    private int storeSize = 100;

    public static class ElapseTime {
        private @Nullable Long inbound;
        private @Nullable Long outbound;
        private final String middlewareName;

        public ElapseTime(String middlewareName) {
            this.middlewareName = middlewareName;
        }

        public void setInboundElapse(@Nullable Long elapse) {
            this.inbound = elapse;
        }

        public void setOutboundElapse(@Nullable Long elapse) {
            this.outbound = elapse;
        }

        public @Nullable Long getInboundElapse() {
            return inbound;
        }

        public @Nullable Long getOutboundElapse() {
            return outbound;
        }

        public String getMiddlewareName() {
            return middlewareName;
        }
    }

    public TraceWebMiddleware() {
        store = new MemoryStore();
        idList = new LinkedList<>();

        MoshasEngine moshas = new MoshasEngine();
        traceList = new TraceList(moshas);
        traceDetail = new TraceDetail(moshas);

        traceRouting = buildTraceRouting(mountPath);
    }

    private TraceRouting buildTraceRouting(String basePath) {
        TraceRouting routing = new TraceRouting(basePath);
        routing.add("/", (req, os) -> {
            synchronized (this) {
                traceList.render(os, "logs", new LinkedList<>(idList));
            }
        });
        routing.add("/[a-z0-9\\-]+", (req, os) -> {
            String reqUri = Objects.requireNonNull(req.getUri());
            String id = reqUri.substring(reqUri.lastIndexOf("/") + 1);
            RequestLog requestLog = (RequestLog) store.read(id);
            if (requestLog == null) {
                throw new TraceRouting.RouteNotFoundException();
            }

            LinkedList<ElapseTime> middlewareTraces = new LinkedList<>();

            long t = 0;
            for (TraceLog.Entry e : requestLog.inboundLog().getEntries()) {
                if (!middlewareTraces.isEmpty()) {
                    middlewareTraces.getLast().setInboundElapse(e.timestamp() - t);
                }
                t = e.timestamp();
                middlewareTraces.add(new ElapseTime(e.middleware()));
            }
            int idx = middlewareTraces.size();
            for (TraceLog.Entry e : requestLog.outboundLog().getEntries()) {
                middlewareTraces.get(--idx).setOutboundElapse(e.timestamp() - t);
                t = e.timestamp();
            }

            traceDetail.render(os,
                    "headers", requestLog.headers(),
                    "parameters", requestLog.parameters(),
                    "traces", middlewareTraces);
        });
        return routing;
    }

    @Override
    public <NNREQ, NNRES> @Nullable HttpResponse handle(HttpRequest request, MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
        String uri = request.getUri();
        if (uri != null && uri.startsWith(mountPath + "/")) {
            return traceRouting.handle(request);
        } else {
            request = MixinUtils.mixin(request, Traceable.class);
            HttpResponse response = castToHttpResponse(chain.next(request));
            Traceable requestTrace  = request;
            synchronized (this) {
                // Only record completed requests (a middleware may pass null through).
                if (response != null) {
                    if (idList.size() >= storeSize) {
                        LogKey oldestLogKey = idList.removeLast();
                        store.delete(oldestLogKey.getId());
                    }
                    idList.addFirst(new LogKey(requestTrace.getId(),
                            Objects.requireNonNull(request.getRequestMethod()),
                            Objects.requireNonNull(request.getUri())));
                    store.write(requestTrace.getId(), new RequestLog(
                            Objects.requireNonNull(request.getHeaders()),
                            Objects.requireNonNull(request.getParams()),
                            requestTrace.getTraceLog(), response.getTraceLog()));
                }
            }

            return response;
        }
    }

    public void setMountPath(String mountPath) {
        this.mountPath = mountPath;
        this.traceRouting = buildTraceRouting(mountPath);
    }

    public void close() {
        if (store instanceof Closeable closeable) {
            try {
                closeable.close();
            } catch (IOException ignore) {
                // Ignore exceptions during shutdown hook of development middleware.
            }
        }
    }

    public static class LogKey implements Serializable, Comparable<LogKey> {
        private final String id;
        private final String method;
        private final String uri;
        private final LocalDateTime dateTime;

        public LogKey(String id, String method, String uri) {
            this.id = id;
            this.method = method;
            this.uri = uri;
            this.dateTime = LocalDateTime.now();
        }

        public String getId() {
            return id;
        }

        public String getMethod() {
            return method;
        }

        public String getUri() {
            return uri;
        }

        public LocalDateTime getDateTime() {
            return dateTime;
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }

        @Override
        public boolean equals(Object another) {
            return another instanceof LogKey lk && Objects.equals(this.id, lk.getId());
        }

        @Override
        public int compareTo(LogKey another) {
            return this.dateTime.compareTo(another.getDateTime());
        }
    }

    public record RequestLog(Headers headers, Parameters parameters, TraceLog inboundLog,
                             TraceLog outboundLog) implements Serializable {
    }

    /**
     * Set the number of stored requests.
     *
     * @param storeSize the number of stored requests
     */
    public void setStoreSize(int storeSize) {
        this.storeSize = storeSize;
    }
}
