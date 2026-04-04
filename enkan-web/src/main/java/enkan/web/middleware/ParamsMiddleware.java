package enkan.web.middleware;

import enkan.MiddlewareChain;
import enkan.annotation.Middleware;
import enkan.collection.Parameters;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.exception.FalteringEnvironmentException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import static enkan.web.util.CodecUtils.formDecode;
import static enkan.web.util.HttpRequestUtils.characterEncoding;
import static enkan.web.util.HttpRequestUtils.isUrlEncodedForm;

/**
 * Middleware that parses URL-encoded query string and form body parameters.
 *
 * <p>Query string parameters are always parsed and stored via
 * {@link enkan.data.HttpRequest#setQueryParams}.  Form body parameters
 * (i.e. {@code Content-Type: application/x-www-form-urlencoded}) are parsed
 * and stored via {@link enkan.data.HttpRequest#setFormParams}.  Both are merged
 * into {@link enkan.data.HttpRequest#setParams}.
 *
 * @author kawasima
 */
@Middleware(name = "params")
public class ParamsMiddleware implements WebMiddleware {
    protected Parameters parseParams(String urlencodedParams, String encoding) {
        return formDecode(urlencodedParams, encoding);
    }

    protected void parseQueryParams(HttpRequest request, String encoding) {
        String queryString = request.getQueryString();
        if (queryString == null) {
            request.setQueryParams(Parameters.empty());
            if (request.getParams() == null) {
                request.setParams(Parameters.empty());
            }
        } else {
            Parameters params = parseParams(queryString, encoding);
            request.setQueryParams(params);
            Parameters current = request.getParams();
            if (current == null) {
                request.setParams(params);
            } else {
                current.putAll(params);
            }
        }
    }

    protected void parseFormParams(HttpRequest request, String encoding) {
        InputStream body = request.getBody();
        if (isUrlEncodedForm(request) && body != null) {
            String sb;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(body, encoding))) {
                sb = reader.lines().collect(java.util.stream.Collectors.joining("\n"));
            } catch (IOException e) {
                throw new FalteringEnvironmentException(e);
            }
            Parameters params = parseParams(sb.toString(), encoding);
            request.setFormParams(params);
            Parameters current = request.getParams();
            if (current == null) {
                request.setParams(params);
            } else {
                current.putAll(params);
            }
        } else {
            request.setFormParams(Parameters.empty());
            if (request.getParams() == null) {
                request.setParams(Parameters.empty());
            }
        }

    }

    /**
     * Parses query string and form body parameters and populates the request.
     *
     * <p>Skips parsing if the respective parameters have already been set
     * (e.g. by a previous middleware in the chain).
     *
     * @param request the incoming HTTP request to populate
     */
    public void paramsRequest(HttpRequest request) {
        String encoding = characterEncoding(request);
        if (encoding == null) {
            encoding = "UTF-8";
        }

        Parameters formParams = request.getFormParams();
        if (formParams == null) {
            parseFormParams(request, encoding);
        }

        Parameters queryParams = request.getQueryParams();
        if (queryParams == null) {
            parseQueryParams(request, encoding);
        }
    }

    @Override
    public <NNREQ, NNRES> HttpResponse handle(HttpRequest httpRequest, MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> next) {
        paramsRequest(httpRequest);
        return castToHttpResponse(next.next(httpRequest));
    }
}
