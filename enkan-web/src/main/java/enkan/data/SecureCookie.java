package enkan.data;

/**
 * A cookie with the {@code __Secure-} prefix (RFC 6265bis §4.1.3.1).
 *
 * <p>{@code __Secure-} cookies are guaranteed to be sent only over secure
 * channels ({@code Secure} attribute). Unlike {@link HostCookie}, they may
 * have a {@code Domain} attribute and an arbitrary {@code Path}.
 *
 * <p>The {@code __Secure-} prefix is automatically prepended to the cookie name
 * in the {@code Set-Cookie} header output.
 *
 * <pre>{@code
 * SecureCookie cookie = SecureCookie.create("sid", "xyz789");
 * // Set-Cookie: __Secure-sid=xyz789; secure
 * }</pre>
 *
 * @author kawasima
 */
public final class SecureCookie extends Cookie {
    private static final long serialVersionUID = 1L;

    private SecureCookie(String name, String value) {
        super(name, value);
        super.setSecure(true);
    }

    /**
     * Creates a {@code __Secure-} cookie with the given name and value.
     *
     * @param name  the cookie name (without the {@code __Secure-} prefix)
     * @param value the cookie value
     * @return a new SecureCookie instance
     */
    public static SecureCookie create(String name, String value) {
        return new SecureCookie(name, value);
    }

    /**
     * Not supported — {@code __Secure-} cookies must always be secure.
     *
     * @throws UnsupportedOperationException if {@code secure} is {@code false}
     */
    @Override
    public void setSecure(boolean secure) {
        if (!secure) {
            throw new UnsupportedOperationException("__Secure- cookies must have the Secure attribute");
        }
    }

    @Override
    public String toHttpString() {
        String original = super.toHttpString();
        String result = "__Secure-" + original;
        warnIfOversized(result);
        return result;
    }
}
