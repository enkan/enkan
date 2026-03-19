package enkan.middleware;

import enkan.MiddlewareChain;
import enkan.annotation.Middleware;
import enkan.collection.Parameters;
import enkan.data.HttpRequest;
import enkan.data.HttpResponse;
import enkan.exception.FalteringEnvironmentException;
import enkan.exception.MisconfigurationException;
import enkan.middleware.multipart.MultipartParser;
import enkan.util.ThreadingUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static enkan.util.HttpRequestUtils.contentLength;

/**
 * Middleware for handling multipart form data.
 * It parses the body of the request and puts the parameters into the request object.
 * It also deletes temporary files after processing the request.
 * The temporary files are stored in the request object as well.
 * If you want to use this middleware, you must add the {@link enkan.middleware.ParamsMiddleware} before it.
 * <p>
 * This middleware is not thread-safe.
 *
 * <p>Size limits (all in bytes, -1 means unlimited):
 * <ul>
 *   <li>{@code maxTotalSize} — maximum total Content-Length (default 10 MB)</li>
 *   <li>{@code maxFileSize} — maximum size per uploaded file (default 5 MB)</li>
 *   <li>{@code maxFormFieldSize} — maximum size per non-file form field (default 64 KB)</li>
 * </ul>
 *
 * @author kawasima
 */
@Middleware(name = "multipartParams", dependencies = {"params"})
public class MultipartParamsMiddleware implements WebMiddleware {
    private long maxTotalSize = 10L * 1024 * 1024;       // 10 MB
    private long maxFileSize = 5L * 1024 * 1024;          // 5 MB
    private long maxFormFieldSize = 64L * 1024;            // 64 KB
    /**
     * Deletes temporary files.
     *
     * @param multipartParams the parameters extracted from the multipart form data
     */
    protected void deleteTempFile(Parameters multipartParams) {
        multipartParams.keySet().stream()
                .filter(k -> {
                    Object v = multipartParams.getIn(k);
                    return v instanceof Parameters p && p.getIn("tempfile") instanceof File;
                })
                .forEach(k -> {
                    Optional<Path> tempFile = ThreadingUtils.some((File) multipartParams.getIn(k, "tempfile"),
                            File::toPath);
                    tempFile.ifPresent(f -> {
                        try {
                            Files.deleteIfExists(f);
                        } catch (IOException ex) {
                            throw new FalteringEnvironmentException(ex);
                        }
                    });
                });
    }

    /**
     * Extracts multipart form data from the request.
     *
     * @param request the request object
     * @return the parameters extracted from the multipart form data
     */
    protected Parameters extractMultipart(HttpRequest request) {
        Long length = contentLength(request);
        if (maxTotalSize >= 0 && length != null && length > maxTotalSize) {
            throw new MisconfigurationException("web.MULTIPART_TOO_LARGE", maxTotalSize);
        }
        try {
            return MultipartParser.parse(request.getBody(), length,
                    request.getHeaders().get("content-type"), 16384,
                    maxFileSize, maxFormFieldSize);
        } catch (IOException e) {
            throw new FalteringEnvironmentException(e);
        }
    }

    public void setMaxTotalSize(long maxTotalSize) {
        this.maxTotalSize = maxTotalSize;
    }

    public void setMaxFileSize(long maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    public void setMaxFormFieldSize(long maxFormFieldSize) {
        this.maxFormFieldSize = maxFormFieldSize;
    }

    /**
     * Handles the request.
     *
     * @param request   A request object
     * @param chain A chain of middlewares
     * @return A response object
     * @param <NNREQ> the type of the next request object
     * @param <NNRES> the type of the next response object
     */
    @Override
    public <NNREQ, NNRES> HttpResponse handle(HttpRequest request, MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
        Parameters multipartParams = extractMultipart(request);
        request.getParams().putAll(multipartParams);
        try {
            return castToHttpResponse(chain.next(request));
        } finally {
            deleteTempFile(multipartParams);
        }

    }
}
