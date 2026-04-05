package enkan.web.signature;

import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.web.util.sf.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds the signature base string per RFC 9421 §2.5.
 *
 * <p>The signature base is a canonical string representation of the covered
 * components and signature metadata, used as the input to the signing and
 * verification algorithms.
 *
 * @author kawasima
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9421#section-2.5">RFC 9421 §2.5</a>
 */
public final class SignatureBaseBuilder {

    private SignatureBaseBuilder() {}

    /**
     * Builds the signature base for a request signature.
     *
     * @param request    the HTTP request
     * @param components the ordered covered components
     * @param params     the signature metadata parameters (alg, keyid, created, etc.)
     * @return the signature base string
     */
    public static String buildSignatureBase(HttpRequest request,
                                            List<SignatureComponent> components,
                                            SfParameters params) {
        return buildSignatureBase(request, null, components, params);
    }

    /**
     * Builds the signature base for a response signature.
     *
     * @param request    the original request (for {@code ;req} components)
     * @param response   the HTTP response (for {@code @status})
     * @param components the ordered covered components
     * @param params     the signature metadata parameters
     * @return the signature base string
     */
    public static String buildSignatureBase(HttpRequest request,
                                            HttpResponse response,
                                            List<SignatureComponent> components,
                                            SfParameters params) {
        StringBuilder sb = new StringBuilder();
        for (SignatureComponent component : components) {
            String serializedId = serializeComponentId(component);
            String value = resolveComponentValue(request, response, component);
            sb.append(serializedId).append(": ").append(value).append('\n');
        }
        sb.append("\"@signature-params\": ").append(serializeSignatureParams(components, params));
        return sb.toString();
    }

    /**
     * Resolves the value of a single component from the request/response.
     */
    static String resolveComponentValue(HttpRequest request, HttpResponse response,
                                        SignatureComponent component) {
        if (component.isDerived()) {
            return resolveDerived(request, response, component);
        }
        return resolveHeader(request, response, component);
    }

    /**
     * Serializes the {@code @signature-params} line value.
     *
     * <p>Produces an SF inner-list serialization of the covered component
     * identifiers with the signature metadata parameters.
     */
    static String serializeSignatureParams(List<SignatureComponent> components,
                                           SfParameters params) {
        List<SfItem> items = components.stream()
                .map(c -> new SfItem(new SfValue.SfString(c.name()), c.parameters()))
                .toList();
        SfInnerList innerList = new SfInnerList(items, params);
        return StructuredFields.serializeList(new SfList(List.of(innerList)));
    }

    // -------------------------------------------------------------------------
    // Derived components (§2.2)
    // -------------------------------------------------------------------------

    private static String resolveDerived(HttpRequest request, HttpResponse response,
                                         SignatureComponent component) {
        return switch (component.name()) {
            case "@method" -> request.getRequestMethod().toUpperCase(Locale.ROOT);
            case "@path" -> {
                String uri = request.getUri();
                yield (uri == null || uri.isEmpty()) ? "/" : uri;
            }
            case "@query" -> {
                String qs = request.getQueryString();
                yield "?" + (qs != null ? qs : "");
            }
            case "@scheme" -> request.getScheme().toLowerCase(Locale.ROOT);
            case "@authority" -> resolveAuthority(request);
            case "@target-uri" -> resolveTargetUri(request);
            case "@request-target" -> {
                String path = request.getUri();
                if (path == null || path.isEmpty()) path = "/";
                String qs = request.getQueryString();
                yield qs != null ? path + "?" + qs : path;
            }
            case "@query-param" -> resolveQueryParam(request, component);
            case "@status" -> {
                if (response == null) {
                    throw new IllegalArgumentException("@status requires a response context");
                }
                yield String.valueOf(response.getStatus());
            }
            default -> throw new IllegalArgumentException("Unknown derived component: " + component.name());
        };
    }

    private static String resolveAuthority(HttpRequest request) {
        String host = request.getServerName().toLowerCase(Locale.ROOT);
        int port = request.getServerPort();
        String scheme = request.getScheme();
        boolean defaultPort = ("http".equals(scheme) && port == 80)
                || ("https".equals(scheme) && port == 443)
                || port <= 0;
        return defaultPort ? host : host + ":" + port;
    }

    private static String resolveTargetUri(HttpRequest request) {
        String scheme = request.getScheme().toLowerCase(Locale.ROOT);
        String authority = resolveAuthority(request);
        String path = request.getUri();
        if (path == null || path.isEmpty()) path = "/";
        String qs = request.getQueryString();
        return scheme + "://" + authority + path + (qs != null ? "?" + qs : "");
    }

    private static String resolveQueryParam(HttpRequest request, SignatureComponent component) {
        String paramName = component.nameParam();
        if (paramName == null) {
            throw new IllegalArgumentException("@query-param requires ;name parameter");
        }
        String qs = request.getQueryString();
        if (qs == null) {
            throw new IllegalArgumentException("Query parameter '" + paramName + "' not found (no query string)");
        }
        // Parse query string to find the named parameter
        for (String pair : qs.split("&")) {
            int eq = pair.indexOf('=');
            String name = eq >= 0 ? URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8) : pair;
            if (paramName.equals(name)) {
                return eq >= 0 ? URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8) : "";
            }
        }
        throw new IllegalArgumentException("Query parameter '" + paramName + "' not found");
    }

    // -------------------------------------------------------------------------
    // Header components (§2.1)
    // -------------------------------------------------------------------------

    private static String resolveHeader(HttpRequest request, HttpResponse response,
                                        SignatureComponent component) {
        // ;req flag: resolve from request even when signing a response
        Object headerObj;
        if (component.isReq() && response != null) {
            headerObj = request.getHeaders().get(component.name());
        } else if (response != null && !component.isReq()) {
            headerObj = response.getHeaders().get(component.name());
        } else {
            headerObj = request.getHeaders().get(component.name());
        }

        if (headerObj == null) {
            throw new IllegalArgumentException("Header '" + component.name() + "' not found");
        }

        String raw = normalizeHeaderValue(headerObj);

        if (component.isSf()) {
            return canonicalizeSf(raw);
        }
        String keyParam = component.keyParam();
        if (keyParam != null) {
            return extractDictionaryMember(raw, keyParam);
        }
        if (component.isBs()) {
            throw new UnsupportedOperationException(";bs parameter is not yet supported");
        }
        return raw;
    }

    /**
     * Normalizes a header value: joins multiple values with ", " and trims.
     */
    private static String normalizeHeaderValue(Object headerObj) {
        if (headerObj instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(list.get(i).toString().strip());
            }
            return sb.toString();
        }
        return headerObj.toString().strip();
    }

    /**
     * Canonicalizes an SF value by parsing and re-serializing.
     */
    private static String canonicalizeSf(String raw) {
        // Try dictionary first, then list, then item
        try {
            return StructuredFields.serializeDictionary(StructuredFields.parseDictionary(raw));
        } catch (SfParseException ignored) {}
        try {
            return StructuredFields.serializeList(StructuredFields.parseList(raw));
        } catch (SfParseException ignored) {}
        return StructuredFields.serializeItem(StructuredFields.parseItem(raw));
    }

    /**
     * Extracts a single member from an SF Dictionary and serializes it.
     */
    private static String extractDictionaryMember(String raw, String key) {
        SfDictionary dict = StructuredFields.parseDictionary(raw);
        SfMember member = dict.members().get(key);
        if (member == null) {
            throw new IllegalArgumentException("Dictionary member '" + key + "' not found");
        }
        // Serialize the member as a single-entry dictionary, then strip the key prefix
        Map<String, SfMember> single = new LinkedHashMap<>();
        single.put(key, member);
        String serialized = StructuredFields.serializeDictionary(new SfDictionary(single));
        // Result is "key=value" or "key=(inner-list)"; strip "key="
        int eq = serialized.indexOf('=');
        return eq >= 0 ? serialized.substring(eq + 1) : serialized;
    }

    /**
     * Serializes a component identifier as an SF Item string.
     * E.g. {@code "@method"} or {@code "content-type";key="dict-member"}.
     */
    private static String serializeComponentId(SignatureComponent component) {
        SfItem item = new SfItem(new SfValue.SfString(component.name()), component.parameters());
        return StructuredFields.serializeItem(item);
    }
}
