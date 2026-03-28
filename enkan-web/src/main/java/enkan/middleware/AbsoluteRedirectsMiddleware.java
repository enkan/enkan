package enkan.middleware;

import enkan.MiddlewareChain;
import enkan.annotation.Middleware;
import enkan.data.HttpRequest;
import enkan.data.HttpResponse;
import enkan.util.ThreadingUtils;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static enkan.util.HttpRequestUtils.requestUrl;

/**
 * Converts relative redirect URLs in responses to absolute URLs.
 *
 * <p>When a redirect response (status 201, 301, 302, 303, or 307) is returned
 * by a downstream handler, this middleware rewrites the {@code Location} header
 * to an absolute URL by resolving it against the original request URL.
 * If the {@code Location} value is already an absolute URL (i.e. has a scheme),
 * it is left unchanged.
 *
 * @author kawasima
 */
@Middleware(name = "absoluteRedirect")
public class AbsoluteRedirectsMiddleware implements WebMiddleware {
    private static final Set<Integer> REDIRECT_STATUS = new HashSet<>(Arrays.asList(201, 301, 302, 303, 307, 308));

    /**
     * Returns {@code true} if the response status is one of the redirect codes
     * handled by this middleware.
     *
     * @param response the HTTP response to inspect
     * @return {@code true} if the response is a redirect
     */
    protected boolean isRedirectResponse(HttpResponse response) {
        return REDIRECT_STATUS.contains(response.getStatus());
    }

    /**
     * Rewrites the given response header value to an absolute URL if it is relative.
     *
     * @param response the HTTP response whose header will be updated
     * @param header   the header name (e.g. {@code "location"})
     * @param request  the original HTTP request used to resolve the base URL
     */
    protected void updateHeader(HttpResponse response, String header, HttpRequest request) {
        ThreadingUtils.some(response.getHeaders().get(header), Object::toString)
                .ifPresent(url -> response.getHeaders().replace(header, absoluteUrl(url, request)));
    }

    /**
     * Returns {@code true} if the given string is an absolute URL, i.e. it has a scheme
     * such as {@code http} or {@code https}.
     *
     * @param s the string to test
     * @return {@code true} if {@code s} is an absolute URL
     */
    protected boolean isUrl(String s) {
        try {
            return URI.create(s).isAbsolute();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Returns the absolute URL for the given location.
     * If {@code location} is already absolute it is returned as-is;
     * otherwise it is resolved relative to the request URL.
     *
     * @param location the redirect location (may be relative or absolute)
     * @param request  the original HTTP request
     * @return the absolute URL string
     * @throws IllegalArgumentException if the location or request URL is malformed
     */
    protected String absoluteUrl(String location, HttpRequest request) {
        if (isUrl(location)) {
            return location;
        } else {
            try {
                return URI.create(requestUrl(request)).resolve(location).toURL().toString();
            } catch (MalformedURLException | IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        String.format("wrong location %s or request url %s.", location, requestUrl(request)), e);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <NNREQ, NNRES> HttpResponse handle(HttpRequest request, MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
        HttpResponse response = castToHttpResponse(chain.next(request));
        if (isRedirectResponse(response)) {
            updateHeader(response, "location", request);
        }
        return response;
    }
}
