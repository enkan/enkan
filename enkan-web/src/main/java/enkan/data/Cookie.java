package enkan.data;

import enkan.util.HttpDateFormat;

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

    // RFC 7230 §3.2.6 token = 1*tchar
    private static final Pattern RE_TOKEN = Pattern.compile("[!#$%&'\\*\\-+\\.0-9A-Z\\^_`a-z\\|~]+");

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
     * Creates a cookie with the given name and value.
     *
     * @param name  the cookie name
     * @param value the cookie value
     * @return a new cookie instance
     */
    public static Cookie create(String name, String value) {
        if (name == null || !RE_TOKEN.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid cookie name: " + name);
        }
        Cookie cookie = new Cookie();
        cookie.setName(name);
        cookie.setValue(value);
        return cookie;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
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
        sb.append(getName()).append("=").append(getValue());
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
