package enkan.util;

import enkan.collection.Parameters;
import enkan.exception.MisconfigurationException;

import jakarta.ws.rs.core.MediaType;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The utilities for codec.
 *
 * @author kawasima
 */
public class CodecUtils {
    private static final Pattern RE_URL_ENCODED_CHARS = Pattern.compile("(?:%[A-Za-z0-9]{2})+");
    private static final Pattern RE_URL_ENCODED_CHAR = Pattern.compile("%[A-Za-z0-9]{2}");
    private static final Pattern RE_URL_ENCODE_TARGET = Pattern.compile("[^A-Za-z0-9_~.+\\-]+");
    private static final Pattern RE_ACCEPT_FRAGMENT = Pattern.compile("^\\s*(\\*|[^()<>@,;:\"/\\[\\]?={} ]+)/(\\*|[^()<>@,;:\"/\\[\\]?={} ]+)$");


    public static byte[] parseBytes(String encodedBytes) {
        List<Byte> bytes = new ArrayList<>();
        Matcher m = RE_URL_ENCODED_CHAR.matcher(encodedBytes);

        while(m.find()) {
            bytes.add(Integer.valueOf(m.group(0).substring(1), 16).byteValue());
        }
        int len = bytes.size();
        byte[] ret = new byte[len];
        for(int i = 0; i<len; i++) {
            ret[i] = bytes.get(i);
        }
        return ret;
    }

    public static String urlEncode(String unencoded) {
        return urlEncode(unencoded, "UTF-8");
    }

    public static String urlEncode(String unencoded, String encoding) {
        try {
            Matcher m = RE_URL_ENCODE_TARGET.matcher(unencoded);

            StringBuilder sb = new StringBuilder(unencoded.length() * 2);
            while (m.find()) {
                String s = m.group(0);
                StringBuilder encodedSb = new StringBuilder();
                for (byte b : s.getBytes(encoding)) {
                    encodedSb.append("%");
                    int d = b;
                    if (d < 0) {
                        d += 256;
                    }
                    if (d < 16) {
                        encodedSb.append("0");
                    }
                    encodedSb.append(Integer.toString(d, 16));
                }
                m.appendReplacement(sb, Matcher.quoteReplacement(encodedSb.toString()));
            }
            m.appendTail(sb);
            return sb.toString();
        } catch (UnsupportedEncodingException e) {
            return unencoded;
        }
    }

    public static String urlDecode(String encoded) {
        return urlDecode(encoded, "UTF-8");
    }

    public static String urlDecode(String encoded, String encoding) {
        try {
            Matcher m = RE_URL_ENCODED_CHARS.matcher(encoded);
            StringBuilder sb = new StringBuilder(encoded.length());
            while (m.find()) {
                String chars = m.group(0);
                m.appendReplacement(sb, Matcher.quoteReplacement(new String(parseBytes(chars), encoding)));
            }
            m.appendTail(sb);
            return sb.toString();
        } catch (UnsupportedEncodingException e) {
            throw new MisconfigurationException("core.UNSUPPORTED_ENCODING", encoding, e);
        }
    }

    public static <T> String formEncode(T x) {
        return formEncode(x, "UTF-8");
    }

    public static <T> String formEncode(T x, String encoding) {
        switch (x) {
            case null -> {
                return "";
            }
            case String s -> {
                try {
                    return URLEncoder.encode(s, encoding);
                } catch (UnsupportedEncodingException e) {
                    throw new IllegalArgumentException(String.format("encoding %s is not supported", x), e);
                }
            }
            case Map<?, ?> m -> {
                return m.entrySet().stream()
                        .map(e -> {
                            if (e.getValue() instanceof Collection<?> coll) {
                                String encodedKey = formEncode(e.getKey());
                                return coll.stream()
                                        .map(v -> encodedKey + "=" + formEncode(v))
                                        .collect(Collectors.joining("&"));
                            } else {
                                return formEncode(e.getKey()) + "=" + formEncode(e.getValue());
                            }
                        })
                        .collect(Collectors.joining("&"));
            }
            default -> {
                return formEncode(x.toString(), encoding);
            }
        }
    }

    public static String formDecodeStr(String encoded) {
        return formDecodeStr(encoded, "UTF-8");
    }

    public static String formDecodeStr(String encoded, String encoding) {
        try {
            return URLDecoder.decode(encoded, encoding);
        } catch (UnsupportedEncodingException e) {
            throw new MisconfigurationException("core.UNSUPPORTED_ENCODING", encoding, e);
        } catch (IllegalArgumentException e) {
            return encoded;
        }
    }

    public static Parameters formDecode(String encoded, String encoding) {
        Parameters m = Parameters.empty();

        for(String param : encoded.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 1) {
                m.put(formDecodeStr(kv[0], encoding), null);
            } else if (kv.length == 2) {
                m.put(formDecodeStr(kv[0], encoding),
                        formDecodeStr(kv[1], encoding));
            }
        }
        return m;
    }

    /**
     * Parse from the given String contains media type.
     *
     * @param typeStr a String contains media type
     * @return MediaType object
     */
    public static MediaType parseMediaType(String typeStr) {
        Matcher typeMatcher = RE_ACCEPT_FRAGMENT.matcher(typeStr);
        if (typeMatcher.find()) {
            return new MediaType(typeMatcher.group(1), typeMatcher.group(2));
        } else {
            throw new IllegalArgumentException(typeStr);
        }
    }

    /**
     * Convert media type to a String represents the MediaType.
     * MediaType#toString requires a JAX-RS implementation.
     * To avoid that enkan requires a JAX-RS implementation, Use this method.
     *
     * @param mediaType MediaType
     * @return a String represents the MediaType
     */
    public static String printMediaType(MediaType mediaType) {
        return mediaType.getType() + "/" + mediaType.getSubtype();
    }
}
