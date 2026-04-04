package enkan.web.middleware;

import enkan.MiddlewareChain;
import enkan.annotation.Middleware;
import enkan.collection.Parameters;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.web.middleware.normalizer.Normalizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/**
 * The middleware for normalizing parameter values.
 *
 * @author kawasima
 */
@Middleware(name = "normalization", dependencies = {"params"})
public class NormalizationMiddleware implements WebMiddleware {
    private final List<NormalizationSpec<?>> normalizationSpecs;

    public NormalizationMiddleware() {
        this.normalizationSpecs = new ArrayList<>();
    }

    @SafeVarargs
    public NormalizationMiddleware(NormalizationSpec<?> spec, NormalizationSpec<?>... specs) {
        this();
        normalizationSpecs.add(spec);
        normalizationSpecs.addAll(Arrays.asList(specs));
    }

    /**
     * Creates a normalization specification.
     *
     * @param <T> the type of the value to be normalized
     * @param predicate the predicate to test parameter keys
     * @param normalizer the normalizer to apply
     * @return a new normalization specification
     */
    public static <T> NormalizationSpec<T> normalization(Predicate<String> predicate, Normalizer<T> normalizer) {
        return new NormalizationSpec<>(predicate, normalizer);
    }

    @SuppressWarnings("unchecked")
    private static <T> Object applyNormalizer(NormalizationSpec<T> spec, Object value) {
        return spec.normalizer().normalize((T) value);
    }

    /**
     * Normalizes request parameters and passes the request to the next middleware.
     *
     * @param request the request object
     * @param chain the middleware chain
     * @return the response object
     */
    @Override
    public <NNREQ, NNRES> HttpResponse handle(HttpRequest request, MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
        Parameters params = request.getParams();
        if (params != null) {
            params.keySet().forEach(key -> {
                Object obj = params.getRawType(key);
                if (obj == null) return;

                normalizationSpecs.forEach(c -> {
                    if (c.predicate().test(key) && c.normalizer().canNormalize(obj.getClass())) {
                        params.replace(key, applyNormalizer(c, obj));
                    }
                });
            });
        }
        return castToHttpResponse(chain.next(request));
    }

    /**
     * A normalization specification.
     *
     * @param <T> the type of the value to be normalized
     */
    public record NormalizationSpec<T>(Predicate<String> predicate, Normalizer<T> normalizer) {
    }
}
