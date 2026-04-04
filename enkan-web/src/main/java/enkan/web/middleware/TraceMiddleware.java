package enkan.web.middleware;

import enkan.DecoratorMiddleware;
import enkan.MiddlewareChain;
import enkan.web.data.HttpResponse;
import enkan.data.TraceLog;
import enkan.data.Traceable;
import enkan.util.MixinUtils;

/**
 * Adds trace log to the response.
 * @author kawasima
 */
@enkan.annotation.Middleware(name = "trace", mixins = Traceable.class)
public class TraceMiddleware<REQ, RES> implements DecoratorMiddleware<REQ, RES> {
    private boolean enabled = true;

    @Override
    public <NNREQ, NNRES> RES handle(REQ req, MiddlewareChain<REQ, RES, NNREQ, NNRES> chain) {
        if (req != null) {
            req = MixinUtils.mixin(req, Traceable.class);
        }
        RES res = chain.next(req);
        if (enabled && req instanceof Traceable t && res instanceof HttpResponse httpRes) {
            TraceLog log = t.getTraceLog();
            log.getEntries()
                    .forEach(entry -> httpRes.getHeaders().put("X-Enkan-Trace",
                            entry.timestamp() + ":" + entry.middleware()));
        }

        return res;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
