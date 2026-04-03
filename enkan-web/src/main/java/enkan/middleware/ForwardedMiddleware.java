package enkan.middleware;

import enkan.MiddlewareChain;
import enkan.annotation.Middleware;
import enkan.collection.Headers;
import enkan.data.HttpRequest;
import enkan.data.HttpResponse;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Middleware that applies forwarded-address information from trusted reverse proxies.
 *
 * <p>Supports both the standard {@code Forwarded} header (RFC 7239) and the legacy
 * {@code X-Forwarded-For}, {@code X-Forwarded-Proto}, and {@code X-Forwarded-Host} headers.
 * Headers are only trusted when the direct connection's remote address matches one of the
 * configured trusted proxy CIDR ranges, preventing header spoofing by untrusted clients.
 *
 * <p>When headers are trusted, the middleware updates the following request fields:
 * <ul>
 *   <li>{@code remoteAddr} — the original client IP address (port stripped if present;
 *       {@code unknown} and obfuscated identifiers per RFC 7239 §6 are ignored)</li>
 *   <li>{@code scheme} — the original request scheme ({@code http} or {@code https})</li>
 *   <li>{@code serverName} — the original host</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Default — trusts loopback only
 * app.use(new ForwardedMiddleware());
 *
 * // With a custom trusted proxy range
 * ForwardedMiddleware fw = new ForwardedMiddleware();
 * fw.setTrustedProxies(List.of("10.0.0.0/8", "172.16.0.0/12", "127.0.0.0/8", "::1/128"));
 * app.use(fw);
 * }</pre>
 *
 * @author kawasima
 */
@Middleware(name = "forwarded")
public class ForwardedMiddleware implements WebMiddleware {

    /** Pre-parsed representation of a CIDR range for efficient repeated matching. */
    private record ParsedCidr(byte[] networkBytes, int prefixLength) {}

    // volatile ensures visibility of setTrustedProxies() updates across threads
    private volatile List<ParsedCidr> parsedTrustedProxies = parseCidrs(List.of("127.0.0.0/8", "::1/128"));
    private boolean preferStandard = true;

    @Override
    public <NNREQ, NNRES> HttpResponse handle(HttpRequest request,
            MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
        if (isTrustedProxy(request.getRemoteAddr())) {
            applyForwardedHeaders(request);
        }
        return castToHttpResponse(chain.next(request));
    }

    private void applyForwardedHeaders(HttpRequest request) {
        Headers headers = request.getHeaders();
        if (headers == null) return;

        // Use getList() to correctly handle multi-value headers (RFC 7230 §3.2.2).
        // Multiple instances of the same header are joined with ", " before parsing.
        String forwarded = joinHeader(headers, "forwarded");
        String xForwardedFor = joinHeader(headers, "x-forwarded-for");
        String xForwardedProto = joinHeader(headers, "x-forwarded-proto");
        String xForwardedHost = joinHeader(headers, "x-forwarded-host");

        boolean hasStandard = forwarded != null;
        boolean hasLegacy = xForwardedFor != null || xForwardedProto != null || xForwardedHost != null;

        // When preferStandard=false but only Forwarded is present (no legacy headers),
        // fall back to RFC 7239 anyway rather than silently doing nothing.
        if (hasStandard && (preferStandard || !hasLegacy)) {
            applyRfc7239(request, forwarded);
        } else if (hasLegacy) {
            applyLegacy(request, xForwardedFor, xForwardedProto, xForwardedHost);
        }
    }

    /**
     * Returns the combined value of all instances of the given header, joined with {@code ", "},
     * or {@code null} if the header is absent. Uses {@link Headers#getList} to handle
     * multi-value headers (RFC 7230 §3.2.2) without stringifying a {@code List} as {@code [v1, v2]}.
     */
    private static String joinHeader(Headers headers, String name) {
        List<String> values = headers.getList(name);
        return values.isEmpty() ? null : String.join(", ", values);
    }

    /**
     * Parses RFC 7239 {@code Forwarded} header and updates the request.
     * Takes the leftmost entry (original client) when multiple hops are present.
     */
    private void applyRfc7239(HttpRequest request, String forwarded) {
        // Multiple proxies: "for=a, for=b" — leftmost is the original client.
        // Use quote-aware search so commas inside quoted values are not misread as separators.
        int commaIdx = indexOfUnquoted(forwarded, ',', 0);
        String firstEntry = commaIdx >= 0 ? forwarded.substring(0, commaIdx) : forwarded;

        String forValue = null;
        String protoValue = null;
        String hostValue = null;

        int start = 0;
        int len = firstEntry.length();
        while (start < len) {
            // Use quote-aware search for ';' so semicolons inside quoted values are ignored.
            int end = indexOfUnquoted(firstEntry, ';', start);
            if (end < 0) end = len;
            String part = firstEntry.substring(start, end);
            int eq = part.indexOf('=');
            if (eq >= 0) {
                String key = part.substring(0, eq).trim().toLowerCase(Locale.ROOT);
                String value = stripQuotes(part.substring(eq + 1).trim());
                switch (key) {
                    case "for" -> forValue = value;
                    case "proto" -> protoValue = value;
                    case "host" -> hostValue = value;
                }
            }
            start = end + 1;
        }

        if (forValue != null) {
            // RFC 7239 §6: "unknown" means the client address is unavailable;
            // obfuscated identifiers start with "_". Neither is a routable IP — skip.
            if ("unknown".equalsIgnoreCase(forValue) || forValue.startsWith("_")) {
                forValue = null;
            } else if (forValue.startsWith("[")) {
                // IPv6 addresses are wrapped in brackets: "[::1]:51348" → "::1", "[::1]" → "::1"
                int closingBracket = forValue.lastIndexOf(']');
                if (closingBracket > 0) {
                    forValue = forValue.substring(1, closingBracket);
                } else {
                    forValue = null; // malformed — no closing bracket, skip safely
                }
            } else {
                // IPv4 may carry a port: "192.0.2.1:51348" → "192.0.2.1"
                // Distinguish from bare IPv6 (multiple colons) by counting colons.
                int colonCount = 0;
                for (int i = 0; i < forValue.length(); i++) {
                    if (forValue.charAt(i) == ':') colonCount++;
                }
                if (colonCount == 1) {
                    forValue = forValue.substring(0, forValue.indexOf(':'));
                }
            }
        }
        if (forValue != null) {
            request.setRemoteAddr(forValue);
        }
        if ("http".equalsIgnoreCase(protoValue) || "https".equalsIgnoreCase(protoValue)) {
            request.setScheme(protoValue.toLowerCase(Locale.ROOT));
        }
        if (hostValue != null) {
            request.setServerName(hostValue);
        }
    }

    /**
     * Returns the index of the first occurrence of {@code target} in {@code value} starting
     * at {@code fromIndex} that is not inside a quoted-string (RFC 7230 §3.2.6).
     * Returns {@code -1} if no such character is found.
     */
    private static int indexOfUnquoted(String value, char target, int fromIndex) {
        boolean inQuotes = false;
        boolean escaped = false;
        for (int i = fromIndex; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (inQuotes && ch == '\\') { escaped = true; continue; }
            if (ch == '"') { inQuotes = !inQuotes; continue; }
            if (!inQuotes && ch == target) return i;
        }
        return -1;
    }

    /**
     * Applies legacy {@code X-Forwarded-*} headers to the request.
     */
    private void applyLegacy(HttpRequest request, String xFor, String xProto, String xHost) {
        if (xFor != null) {
            int commaIdx = xFor.indexOf(',');
            String firstIp = (commaIdx >= 0 ? xFor.substring(0, commaIdx) : xFor).trim();
            if (!firstIp.isEmpty()) {
                request.setRemoteAddr(firstIp);
            }
        }
        if (xProto != null) {
            String proto = xProto.trim().toLowerCase(Locale.ROOT);
            if ("http".equals(proto) || "https".equals(proto)) {
                request.setScheme(proto);
            }
        }
        if (xHost != null) {
            String host = xHost.trim();
            if (!host.isEmpty()) {
                request.setServerName(host);
            }
        }
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    /**
     * Returns {@code true} if the given IP address falls within any of the trusted proxy CIDRs.
     * Returns {@code false} for a {@code null} or non-numeric remoteAddr.
     *
     * <p>Note: {@link InetAddress#getByName} recognises numeric IP strings without performing
     * DNS resolution. The remoteAddr is expected to be a numeric IP (as provided by servlet
     * containers). Non-numeric strings (hostnames) will fail to match any CIDR.
     */
    private boolean isTrustedProxy(String remoteAddr) {
        if (remoteAddr == null) return false;
        byte[] addrBytes;
        try {
            addrBytes = normalizeToIpv4IfMapped(InetAddress.getByName(remoteAddr));
        } catch (UnknownHostException e) {
            // remoteAddr is not a parseable IP address — treat as untrusted
            return false;
        }
        for (ParsedCidr cidr : parsedTrustedProxies) {
            if (matchesCidr(addrBytes, cidr)) return true;
        }
        return false;
    }

    private boolean matchesCidr(byte[] addrBytes, ParsedCidr cidr) {
        if (addrBytes.length != cidr.networkBytes().length) return false;

        int prefix = cidr.prefixLength();

        if (addrBytes.length == 4) {
            // IPv4: use int arithmetic — zero heap allocation on the hot path
            int addr = toInt(addrBytes);
            int net  = toInt(cidr.networkBytes());
            // prefix=0 means "match any address" (e.g. 0.0.0.0/0 — trust all)
            int mask = prefix == 0 ? 0 : (0xFFFFFFFF << (32 - prefix));
            return (addr & mask) == (net & mask);
        }

        // IPv6: BigInteger handles the 128-bit arithmetic cleanly
        int bits = 128;
        BigInteger addrInt = new BigInteger(1, addrBytes);
        BigInteger cidrInt = new BigInteger(1, cidr.networkBytes());
        // prefix=0 means "match any address" (e.g. ::/0 — trust all)
        BigInteger mask = prefix == 0
                ? BigInteger.ZERO
                : BigInteger.ONE.shiftLeft(bits).subtract(BigInteger.ONE).subtract(
                        BigInteger.ONE.shiftLeft(bits - prefix).subtract(BigInteger.ONE));

        return addrInt.and(mask).equals(cidrInt.and(mask));
    }

    private static int toInt(byte[] b) {
        return ((b[0] & 0xFF) << 24) | ((b[1] & 0xFF) << 16) | ((b[2] & 0xFF) << 8) | (b[3] & 0xFF);
    }

    /**
     * If the address is an IPv4-mapped IPv6 address (::ffff:x.x.x.x), returns the 4-byte
     * IPv4 representation. Otherwise returns the original address bytes unchanged.
     */
    private static byte[] normalizeToIpv4IfMapped(InetAddress addr) {
        byte[] bytes = addr.getAddress();
        if (bytes.length == 16) {
            boolean mapped = true;
            for (int i = 0; i < 10; i++) {
                if (bytes[i] != 0) { mapped = false; break; }
            }
            if (mapped && bytes[10] == (byte) 0xFF && bytes[11] == (byte) 0xFF) {
                return new byte[]{bytes[12], bytes[13], bytes[14], bytes[15]};
            }
        }
        return bytes;
    }

    /**
     * Parses a list of CIDR strings into {@link ParsedCidr} records.
     *
     * <p>Requirements for each CIDR string:
     * <ul>
     *   <li>The address part must be a numeric IPv4 or IPv6 address — hostnames are not
     *       accepted (they would trigger DNS resolution and produce unpredictable results)</li>
     *   <li>The address part must not be empty</li>
     *   <li>The prefix length must be in the range {@code [0, addressBits]}</li>
     * </ul>
     *
     * <p>Note: Java's {@link InetAddress#getByName} normalises IPv4-mapped IPv6 notation
     * (e.g. {@code ::ffff:127.0.0.0}) to a plain {@code Inet4Address}, so the resulting
     * CIDR is treated as IPv4. An IPv6-style prefix such as {@code /104} would then exceed
     * the maximum IPv4 prefix of 32 and be rejected as out of range.
     *
     * @throws IllegalArgumentException if any CIDR string violates the above rules
     */
    private static List<ParsedCidr> parseCidrs(List<String> cidrs) {
        List<ParsedCidr> result = new ArrayList<>(cidrs.size());
        for (String cidr : cidrs) {
            int slash = cidr.indexOf('/');
            String address = slash >= 0 ? cidr.substring(0, slash) : cidr;

            if (address.isEmpty()) {
                throw new IllegalArgumentException("CIDR address part must not be empty: \"" + cidr + "\"");
            }

            // Reject hostnames before calling getByName to avoid any DNS I/O.
            // A valid numeric IP must contain at least one '.' (IPv4) or ':' (IPv6).
            if (!isNumericIp(address)) {
                throw new IllegalArgumentException(
                        "CIDR address must be a numeric IP, not a hostname: \"" + cidr + "\"");
            }

            try {
                InetAddress addr = InetAddress.getByName(address);
                byte[] networkBytes = addr.getAddress();
                int maxPrefix = networkBytes.length * 8;
                int prefixLength;
                if (slash >= 0) {
                    prefixLength = Integer.parseInt(cidr.substring(slash + 1));
                    if (prefixLength < 0 || prefixLength > maxPrefix) {
                        throw new IllegalArgumentException(
                                "Prefix length " + prefixLength + " out of range [0," + maxPrefix + "]: \"" + cidr + "\"");
                    }
                } else {
                    prefixLength = maxPrefix;
                }
                result.add(new ParsedCidr(networkBytes, prefixLength));
            } catch (UnknownHostException | NumberFormatException e) {
                throw new IllegalArgumentException("Invalid CIDR range: \"" + cidr + "\"", e);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Returns {@code true} if {@code s} looks like a numeric IPv4 or IPv6 address.
     *
     * <p>Requires at least one separator character ({@code '.'} for IPv4-like inputs or
     * {@code ':'} for IPv6-like inputs) so hexadecimal hostnames such as {@code cafe} or
     * {@code deadbeef} are rejected before reaching {@link InetAddress#getByName(String)}.
     */
    private static boolean isNumericIp(String s) {
        boolean hasColon = s.indexOf(':') >= 0;
        boolean hasDot = s.indexOf('.') >= 0;

        if (!hasColon && !hasDot) {
            return false; // neither IPv4 nor IPv6 — could be a hostname like "cafe"
        }

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (hasColon) {
                // IPv6: allow hex digits, colons, and dots (for IPv4-in-IPv6 notation)
                if ((c >= '0' && c <= '9') || c == ':' || c == '.') continue;
                if ((c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')) continue;
                return false;
            } else {
                // IPv4: allow digits and dots only
                if ((c >= '0' && c <= '9') || c == '.') continue;
                return false;
            }
        }
        return true;
    }

    /**
     * Sets the list of CIDR ranges from which forwarded headers are trusted.
     * Requests from addresses outside these ranges will not have their forwarded
     * headers applied. CIDRs are validated and pre-parsed at call time.
     *
     * @param trustedProxies list of CIDR strings, e.g. {@code List.of("10.0.0.0/8", "::1/128")}
     * @throws IllegalArgumentException if any entry is not a valid numeric CIDR or has an
     *         out-of-range prefix length
     */
    public void setTrustedProxies(List<String> trustedProxies) {
        this.parsedTrustedProxies = parseCidrs(List.copyOf(trustedProxies));
    }

    /**
     * When {@code true} (default), the standard {@code Forwarded} header (RFC 7239) takes
     * precedence over legacy {@code X-Forwarded-*} headers when both are present.
     * Set to {@code false} to prefer legacy headers.
     *
     * @param preferStandard {@code true} to prefer RFC 7239; {@code false} to prefer legacy
     */
    public void setPreferStandard(boolean preferStandard) {
        this.preferStandard = preferStandard;
    }
}
