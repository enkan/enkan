package enkan.data;

import enkan.util.HttpDateFormat;
import enkan.util.ParsingUtils;

import java.io.Serializable;
import java.util.Date;
import java.util.regex.Pattern;

/**
 * Represents an HTTP cookie (RFC 6265).
 *
 * <p>Use {@link #create(String, String)} to build a cookie, configure
 * optional attributes, then call {@link #toHttpString()} to produce
 * the {@code Set-Cookie} header value.</p>
 *
 * @author kawasima
 */
public class Cookie implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final Pattern RE_TOKEN = Pattern.compile(ParsingUtils.RE_TOKEN);

    // RFC 6265 §4.1.1 defines:
    //   cookie-value = *cookie-octet / ( DQUOTE *cookie-octet DQUOTE )
    //   cookie-octet = %x21 / %x23-2B / %x2D-3A / %x3C-5B / %x5D-7E
    // This implementation intentionally supports only the unquoted *cookie-octet form
    // and rejects values containing DQUOTE (i.e., it does not accept the quoted form).
    private static final Pattern RE_COOKIE_VALUE = Pattern.compile("[\\x21\\x23-\\x2B\\x2D-\\x3A\\x3C-\\x5B\\x5D-\\x7E]*");

    private String name;
    private String value;
    private String domain;
    private Integer maxAge;
    private String path;
    private Date expires;
    private boolean secure;
    private boolean httpOnly;
    private String sameSite;

    /**
     * @deprecated Use {@link #create(String, String)} instead.
     */
    @Deprecated
    public Cookie() {
    }

    /**
     * Creates a cookie with the given name and value.
     *
     * @param name  the cookie name
     * @param value the cookie value
     * @return a new cookie instance
     */
    public static Cookie create(String name, String value) {
        Cookie cookie = new Cookie();
        cookie.setName(name);
        cookie.setValue(value);
        return cookie;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name == null || !RE_TOKEN.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid cookie name");
        }
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        if (value != null && !RE_COOKIE_VALUE.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid cookie value");
        }
        this.value = value;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public Integer getMaxAge() {
        return maxAge;
    }

    public void setMaxAge(Integer maxAge) {
        this.maxAge = maxAge;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Date getExpires() {
        return expires;
    }

    public void setExpires(Date expires) {
        this.expires = expires;
    }

    public boolean isSecure() {
        return secure;
    }

    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    public boolean isHttpOnly() {
        return httpOnly;
    }

    public void setHttpOnly(boolean httpOnly) {
        this.httpOnly = httpOnly;
    }

    public String getSameSite() {
        return sameSite;
    }

    public void setSameSite(String sameSite) {
        this.sameSite = sameSite;
    }

    /**
     * Serializes this cookie to a {@code Set-Cookie} header value.
     *
     * @return the cookie string in RFC 6265 format
     */
    public String toHttpString() {
        StringBuilder sb = new StringBuilder();
        String value = getValue();
        sb.append(getName()).append("=").append(value != null ? value : "");
        if (getDomain() != null) {
            sb.append("; domain=").append(getDomain());
        }
        if (getPath() != null) {
            sb.append("; path=").append(getPath());
        }
        if (getExpires() != null) {
            sb.append("; expires=").append(HttpDateFormat.RFC1123.format(getExpires()));
        }
        if (getMaxAge() != null) {
            sb.append("; max-age=").append(getMaxAge());
        }
        if (isHttpOnly()) {
            sb.append("; httponly");
        }
        if (isSecure()) {
            sb.append("; secure");
        }
        if (getSameSite() != null) {
            sb.append("; samesite=").append(getSameSite());
        }
        return sb.toString();
    }
}
