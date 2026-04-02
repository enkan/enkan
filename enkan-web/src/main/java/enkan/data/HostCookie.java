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
 * // Set-Cookie: __Host-token=abc123; path=/; httponly; secure
 * }</pre>
 *
 * @author kawasima
 */
public final class HostCookie extends Cookie {
    private static final long serialVersionUID = 1L;

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
     */
    public static HostCookie create(String name, String value) {
        return new HostCookie(name, value);
    }

    /**
     * Not supported — {@code __Host-} cookies must not have a {@code Domain} attribute.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public void setDomain(String domain) {
        throw new UnsupportedOperationException("__Host- cookies must not have a Domain attribute");
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
    }

    /**
     * Not supported — {@code __Host-} cookies must have {@code Path=/}.
     *
     * @throws IllegalArgumentException if {@code path} is not {@code "/"}
     */
    @Override
    public void setPath(String path) {
        if (!"/".equals(path)) {
            throw new IllegalArgumentException("__Host- cookies must have Path=/");
        }
    }

    @Override
    public String toHttpString() {
        String original = super.toHttpString();
        return "__Host-" + original;
    }
}
