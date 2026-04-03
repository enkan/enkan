package enkan.data;

/**
 * A cookie with the {@code __Host-} prefix (RFC 6265bis §4.1.3.2).
 *
 * <p>{@code __Host-} cookies are guaranteed to be:
 * <ul>
 *   <li>Sent only over secure channels ({@code Secure} attribute)</li>
 *   <li>Scoped to the exact host (no {@code Domain} attribute)</li>
 *   <li>Available on all paths ({@code Path=/})</li>
 * </ul>
 *
 * <p>The {@code __Host-} prefix is automatically prepended to the cookie name
 * in the {@code Set-Cookie} header output.
 *
 * <pre>{@code
 * HostCookie cookie = HostCookie.create("token", "abc123");
 * // Set-Cookie: __Host-token=abc123; path=/; secure
 * }</pre>
 *
 * @author kawasima
 */
public final class HostCookie extends Cookie {
    private static final long serialVersionUID = 1L;

    private static final String PREFIX = "__Host-";

    private HostCookie(String name, String value) {
        super(name, value);
        super.setSecure(true);
        super.setPath("/");
    }

    /**
     * Creates a {@code __Host-} cookie with the given name and value.
     *
     * @param name  the cookie name (without the {@code __Host-} prefix)
     * @param value the cookie value
     * @return a new HostCookie instance
     * @throws IllegalArgumentException if the name already starts with {@code __Host-}
     */
    public static HostCookie create(String name, String value) {
        if (name != null && name.startsWith(PREFIX)) {
            throw new IllegalArgumentException(
                    "Name must not include the __Host- prefix; it is added automatically");
        }
        return new HostCookie(name, value);
    }

    /**
     * Parses a {@code __Host-} cookie received in a request {@code Cookie} header.
     * The prefix is stripped from the raw name to produce the application-facing name.
     *
     * @param rawName the cookie name as sent by the browser (including {@code __Host-} prefix)
     * @param value   the cookie value
     * @return a new HostCookie instance with the prefix stripped
     */
    public static HostCookie parse(String rawName, String value) {
        String name = rawName.startsWith(PREFIX) ? rawName.substring(PREFIX.length()) : rawName;
        return new HostCookie(name, value);
    }

    /**
     * Not supported — {@code __Host-} cookies must not have a {@code Domain} attribute.
     * Setting {@code null} is permitted (no-op since domain is always null).
     *
     * @throws UnsupportedOperationException if {@code domain} is not {@code null}
     */
    @Override
    public void setDomain(String domain) {
        if (domain != null) {
            throw new UnsupportedOperationException("__Host- cookies must not have a Domain attribute");
        }
        super.setDomain(null);
    }

    /**
     * Not supported — {@code __Host-} cookies must always be secure.
     *
     * @throws UnsupportedOperationException if {@code secure} is {@code false}
     */
    @Override
    public void setSecure(boolean secure) {
        if (!secure) {
            throw new UnsupportedOperationException("__Host- cookies must have the Secure attribute");
        }
        super.setSecure(true);
    }

    /**
     * Not supported — {@code __Host-} cookies must have {@code Path=/}.
     *
     * @throws IllegalArgumentException if {@code path} is not {@code "/"}
     */
    @Override
    public void setPath(String path) {
        if (path == null || !"/".equals(path)) {
            throw new IllegalArgumentException("__Host- cookies must have Path=/");
        }
        super.setPath("/");
    }

    @Override
    public String toHttpString() {
        String result = PREFIX + buildHttpString();
        warnIfOversized(result);
        return result;
    }
}
