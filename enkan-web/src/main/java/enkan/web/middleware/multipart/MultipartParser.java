package enkan.web.middleware.multipart;

import enkan.collection.Parameters;
import enkan.exception.FalteringEnvironmentException;
import enkan.web.util.CodecUtils;
import enkan.util.SearchUtils;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parse multipart request.
 *
 * @author kawasima
 */
public class MultipartParser {
    private static final int DEFAULT_BUFFER_SIZE = 16384;

    private static final String EOL = "\r\n";
    // Matches a quoted parameter value, handling backslash-escaped characters inside quotes.
    private static final Pattern QUOTED_BOUNDARY = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");
    // Matches an unquoted boundary token (RFC 2046 bchars + bcharsnospace); comma is allowed.
    private static final Pattern UNQUOTED_BOUNDARY = Pattern.compile("([^\\s;\"]+)");
    private static final Pattern TOKEN = Pattern.compile("[^\\s()<>,;:\\\\\"/\\[\\]?=]+");
    private static final Pattern CONDISP = Pattern.compile("Content-Disposition:\\s*" + TOKEN.pattern() + "\\s*", Pattern.CASE_INSENSITIVE);
    private static final Pattern VALUE = Pattern.compile("\"(?:\\\\\"|[^\"])*\"|" + TOKEN.pattern());
    private static final Pattern BROKEN_QUOTED = Pattern.compile(String.format("^%s.*;\\sfilename=\"(.*?)\"(?:\\s*$|\\s*;\\s*%s=)", CONDISP, TOKEN), Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern BROKEN_UNQUOTED = Pattern.compile(String.format("^%s.*;\\sfilename=(%s)", CONDISP, TOKEN), Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern MULTIPART_CONTENT_TYPE = Pattern.compile("Content-Type: (.*)" + EOL, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern MULTIPART_CONTENT_DISPOSITION = Pattern.compile("Content-Disposition:.*\\s+name=(" + VALUE.pattern() + ")", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern MULTIPART_CONTENT_ID = Pattern.compile("Content-ID:\\s*([^" + EOL + "]*)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    private static final Pattern ATTRIBUTE_CHAR = Pattern.compile("[^ \\t\\v\\n\\r)(><@,;:\\\\\"/\\[\\]?='*%]");
    private static final Pattern ATTRIBUTE = Pattern.compile(ATTRIBUTE_CHAR.pattern() + "+");
    private static final Pattern SECTION = Pattern.compile("\\*[0-9]+/");
    private static final Pattern REGULAR_PARAMETER_NAME = Pattern.compile(ATTRIBUTE.pattern() + "(?:" + SECTION.pattern() + ")?");
    private static final Pattern REGULAR_PARAMETER = Pattern.compile(String.format("(%s)=(%s)", REGULAR_PARAMETER_NAME.pattern(), VALUE.pattern()));
    private static final Pattern EXTENDED_OTHER_NAME = Pattern.compile(ATTRIBUTE.pattern() + "\\*[1-9][0-9]*\\*");
    private static final Pattern EXTENDED_OTHER_VALUE = Pattern.compile("%[0-9a-fA-F]{2}|" + ATTRIBUTE_CHAR.pattern());
    private static final Pattern EXTENDED_OTHER_PARAMETER = Pattern.compile(String.format("(%s)=((?:%s)*)", EXTENDED_OTHER_NAME.pattern(), EXTENDED_OTHER_VALUE.pattern()));
    private static final Pattern EXTENDED_INITIAL_NAME = Pattern.compile(ATTRIBUTE + "(?:\\*0)?\\*");
    private static final Pattern EXTENDED_INITIAL_VALUE = Pattern.compile("[a-zA-Z0-9\\-]*'[a-zA-Z0-9\\-]*'(?:" + EXTENDED_OTHER_VALUE.pattern() + ")*");
    private static final Pattern EXTENDED_INITIAL_PARAMETER = Pattern.compile(String.format("(%s)=(%s)", EXTENDED_INITIAL_NAME.pattern(), EXTENDED_INITIAL_VALUE.pattern()));
    private static final Pattern EXTENDED_PARAMETER = Pattern.compile(EXTENDED_INITIAL_PARAMETER.pattern() + "|" + EXTENDED_OTHER_PARAMETER.pattern());
    private static final Pattern DISPPARM = Pattern.compile(String.format(";\\s*(?:%s|%s)\\s*", REGULAR_PARAMETER.pattern(), EXTENDED_PARAMETER.pattern()));
    private static final Pattern RFC2183 = Pattern.compile(String.format("^%s(%s)+$", CONDISP.pattern(), DISPPARM.pattern()), Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    private static final BiFunction<String, String, File> TEMPFILE_FACTORY = (filename, contentType) -> {
        int idx = filename.indexOf('.');
        String extName = (idx >= 0 && idx < filename.length() - 1) ? filename.substring(idx) : "";
        try {
            return File.createTempFile("EnkanMultipart", extName);
        } catch (IOException e) {
            throw new FalteringEnvironmentException(e);
        }
    };


    private enum ParseState {
        FAST_FORWARD, CONSUME_TOKEN, MIME_HEAD, MIME_BODY, DONE
    }

    private enum BoundaryState {
        BOUNDARY, END_BOUNDARY, EMPTY
    }

    private final ByteBuffer buf;
    private final String boundary;
    private ParseState state;
    private int mimeIndex;
    private final MultipartCollector collector;

    public MultipartParser(String boundary, int bufferSize) {
        this(boundary, bufferSize, -1, -1);
    }

    public MultipartParser(String boundary, int bufferSize, long maxFileSize, long maxFormFieldSize) {
        this.boundary = "--" + boundary;
        buf = ByteBuffer.allocate(bufferSize);
        state = ParseState.FAST_FORWARD;
        collector = new MultipartCollector(TEMPFILE_FACTORY, maxFileSize, maxFormFieldSize);
    }

    /**
     * Feeds a chunk of bytes into the parser and advances the parse state.
     *
     * @param src the byte array containing the data to process
     * @param len the number of bytes to read from {@code src}
     * @throws IOException if the stream ends unexpectedly ({@link java.io.EOFException})
     *                     or an I/O error occurs during parsing
     */
    public void onRead(byte[] src, int len) throws IOException {
        if (len == 0) throw new EOFException();
        buf.put(src, 0, len);
        buf.flip();
        runParser();
    }

    /**
     * Returns the parsed multipart parameters after parsing is complete.
     *
     * @return a {@link Parameters} map containing all parsed form fields and file parts
     */
    public Parameters result() {
        Parameters params = Parameters.empty();
        collector.stream()
                .forEach(part -> params.putAll(part.getData()));
        return params;
    }

    /**
     * Extracts the multipart boundary string from a Content-Type header value.
     *
     * <p>Parses the header as a media-type followed by semicolon-separated parameters,
     * locating the {@code boundary} parameter case-insensitively. Both quoted-string
     * values (including backslash-escaped characters inside quotes, per RFC 2046 §5.1)
     * and unquoted token values are supported. Parameters may appear in any order.</p>
     *
     * @param contentType the value of the {@code Content-Type} header
     * @return the boundary string, or {@code null} if not found or {@code contentType} is {@code null}
     */
    public static String parseBoundary(String contentType) {
        if (contentType == null) return null;
        // Must start with multipart/ media type.
        if (!contentType.trim().toLowerCase(java.util.Locale.ROOT).startsWith("multipart/")) return null;

        // Split on ';' and scan each parameter for a boundary attribute.
        String[] parts = contentType.split(";");
        for (int i = 1; i < parts.length; i++) {
            String param = parts[i].trim();
            int eq = param.indexOf('=');
            if (eq <= 0) continue;
            String attrName = param.substring(0, eq).trim();
            if (!"boundary".equalsIgnoreCase(attrName)) continue;
            String value = param.substring(eq + 1).trim();
            if (value.startsWith("\"")) {
                // Quoted string: unescape backslash sequences (RFC 2046 quoted-pair: \X -> X for any X).
                // A leading '"' that does not form a valid quoted-string is treated as malformed.
                Matcher quoted = QUOTED_BOUNDARY.matcher(value);
                if (quoted.matches()) {
                    return quoted.group(1).replaceAll("\\\\(.)", "$1");
                }
                // Malformed quoted-string — skip rather than falling through to unquoted.
            } else {
                // Unquoted token.
                Matcher unquoted = UNQUOTED_BOUNDARY.matcher(value);
                if (unquoted.find()) {
                    return unquoted.group(1);
                }
            }
        }
        return null;
    }

    /**
     * Parses a multipart request body and returns all parts as a parameter map.
     *
     * <p>If {@code contentLength} is {@code 0} or no boundary is found in
     * {@code contentType}, an empty {@link Parameters} is returned immediately.
     * A {@code bufferSize} of {@code 0} uses the default buffer size
     * ({@value DEFAULT_BUFFER_SIZE} bytes).
     *
     * @param in            the request body input stream
     * @param contentLength the value of the {@code Content-Length} header, or {@code null} if unknown
     * @param contentType   the value of the {@code Content-Type} header
     * @param bufferSize    the read buffer size in bytes; {@code 0} uses the default
     * @return a {@link Parameters} map of all parsed multipart parts
     * @throws IOException if an I/O error occurs while reading the stream
     */
    public static Parameters parse(InputStream in, Long contentLength, String contentType, int bufferSize) throws IOException {
        return parse(in, contentLength, contentType, bufferSize, -1, -1);
    }

    /**
     * Parses a multipart request body with per-part size limits.
     *
     * @param in               the request body input stream
     * @param contentLength    the value of the {@code Content-Length} header, or {@code null} if unknown
     * @param contentType      the value of the {@code Content-Type} header
     * @param bufferSize       the read buffer size in bytes; {@code 0} uses the default
     * @param maxFileSize      maximum bytes per uploaded file; {@code -1} for unlimited
     * @param maxFormFieldSize maximum bytes per non-file form field; {@code -1} for unlimited
     * @return a {@link Parameters} map of all parsed multipart parts
     * @throws IOException if an I/O error occurs while reading the stream
     */
    public static Parameters parse(InputStream in, Long contentLength, String contentType, int bufferSize,
                                   long maxFileSize, long maxFormFieldSize) throws IOException {
        if (contentLength != null && contentLength == 0) return Parameters.empty();
        String boundary = parseBoundary(contentType);
        if (boundary == null) return Parameters.empty();

        if (bufferSize == 0) bufferSize = DEFAULT_BUFFER_SIZE;
        byte[] buffer = new byte[bufferSize];
        MultipartParser parser = new MultipartParser(boundary, bufferSize, maxFileSize, maxFormFieldSize);
        try {
            int readed = in.read(buffer);
            parser.onRead(buffer, readed);

            while (parser.state != ParseState.DONE) {
                readed = in.read(buffer, 0, parser.buf.remaining());
                parser.onRead(buffer, readed);
            }
        } catch (Exception e) {
            parser.collector.cleanup();
            throw e;
        }

        return parser.result();
    }

    private void runParser() throws IOException {
        while (true) {
            switch (state) {
                case FAST_FORWARD:
                    if (handleFastForward()) return;
                    break;
                case CONSUME_TOKEN:
                    if (handleConsumeToken()) return;
                    break;
                case MIME_HEAD:
                    if (handleMimeHead()) return;
                    break;
                case MIME_BODY:
                    if (handleMimeBody()) return;
                    break;
                case DONE:
                    return;
            }
        }
    }

    private boolean handleFastForward() {
        if (consumeBoundary() != BoundaryState.EMPTY) {
            state = ParseState.MIME_HEAD;
            return false;
        }

        return true;
    }

    private boolean handleConsumeToken() {
        BoundaryState tok = consumeBoundary();
        if (tok == BoundaryState.END_BOUNDARY || tok == BoundaryState.EMPTY) {
            state = ParseState.DONE;
        } else {
            state = ParseState.MIME_HEAD;
        }

        return false;
    }

    private boolean handleMimeHead() throws IOException {
        buf.mark();
        while (buf.hasRemaining()) {
            if (buf.get() == '\r'
                    && buf.hasRemaining() && buf.get() == '\n'
                    && buf.hasRemaining() && buf.get() == '\r'
                    && buf.hasRemaining() && buf.get() == '\n') {
                int end = buf.position() - 4;
                buf.reset();
                int start = buf.position();
                byte[] headBuf = new byte[end - start + 2];
                buf.get(headBuf);
                String head = new String(headBuf, StandardCharsets.UTF_8);
                // Read two CR+LF.
                for (int i=0; i<2; i++) buf.get();

                String contentType = null;
                Matcher contentTypeMatcher = MULTIPART_CONTENT_TYPE.matcher(head);
                if (contentTypeMatcher.find()) {
                    contentType = contentTypeMatcher.group(1);
                }

                String name = null;
                Matcher contentDispositionMatcher = MULTIPART_CONTENT_DISPOSITION.matcher(head);
                if (contentDispositionMatcher.find()) {
                    name = contentDispositionMatcher.group(1).replaceAll("\"(.*)\"", "$1");
                } else {
                    Matcher contentIdMatcher = MULTIPART_CONTENT_ID.matcher(head);
                    if (contentIdMatcher.find()) {
                        name = contentIdMatcher.group(1);
                    }
                }

                String filename = getFilename(head);

                collector.onMimeHead(mimeIndex, head, filename, contentType, name);
                state = ParseState.MIME_BODY;
                return false;
            }
        }
        return true;
    }

    private boolean handleMimeBody() throws IOException {
        byte[] boundaryBytes = boundary.getBytes(StandardCharsets.ISO_8859_1);
        byte[] sought = new byte[boundaryBytes.length + 2];
        sought[0] = '\r';
        sought[1] = '\n';
        System.arraycopy(boundaryBytes, 0, sought, 2, boundaryBytes.length);

        int idx = SearchUtils.kmp(buf, sought);

        int len = idx < 0 ? buf.remaining() : idx - buf.position();
        byte[] content = new byte[len];
        buf.get(content, 0, len);
        collector.onMimeBody(mimeIndex, content);

        // boundary is not found
        if (idx < 0) {
            buf.clear();
            return true;
        }
        // partial boundary is found
        else if (idx + sought.length + 2 > buf.limit()) {
            buf.position(idx);
            buf.compact();
            buf.flip();
            return true;
        }
        // full boundary is found
        else {
            buf.position(idx + 2);
            buf.compact();
            buf.flip();
            collector.onMimeFinish(mimeIndex);
            mimeIndex += 1;
            state = ParseState.CONSUME_TOKEN;
            return false;
        }
    }

    private BoundaryState consumeBoundary() {
        while (true) {
            //noinspection StatementWithEmptyBody
            while (buf.hasRemaining() && buf.get() == '\n');
            if (buf.hasRemaining()) {
                buf.position(buf.position() - 1);
            }
            buf.mark();
            while (buf.hasRemaining()) {
                byte b = buf.get();
                if (b == '\n') break;
            }
            int end = buf.position() - 1;
            buf.reset();

            byte[] boundaryBuf = new byte[end - buf.position()];
            buf.get(boundaryBuf);
            String readedBoundary = new String(boundaryBuf, StandardCharsets.ISO_8859_1).trim();
            buf.get();

            if (readedBoundary.equals(boundary)) {
                return BoundaryState.BOUNDARY;
            } else if (readedBoundary.equals(boundary + "--")) {
                return BoundaryState.END_BOUNDARY;
            } else if (!buf.hasRemaining()) {
                return BoundaryState.EMPTY;
            }
        }
    }

    private String getFilename(String head) {
        String filename = null;
        Matcher rfc2183Matcher = RFC2183.matcher(head);
        Matcher brokenQuotedMatcher = BROKEN_QUOTED.matcher(head);
        Matcher brokenUnQuotedMatcher = BROKEN_UNQUOTED.matcher(head);

        if (rfc2183Matcher.find()) {
            Map<String, String> params = new HashMap<>();
            Matcher disparmMatchr = DISPPARM.matcher(head);
            while (disparmMatchr.find()) {
                int cnt = disparmMatchr.groupCount();
                for (int i=1; i<cnt; i+=2) {
                    if (disparmMatchr.group(i) != null) {
                        params.put(disparmMatchr.group(i), disparmMatchr.group(i + 1));
                    }
                }
            }

            if (params.containsKey("filename")) {
                filename = params.get("filename").replaceAll("^\"(.*)\"$", "$1");
            } else if (params.containsKey("filename*")) {
                String[] tokens = params.get("filename*").split("'", 3);
                filename = tokens[2];
                if (Charset.isSupported(tokens[0])) {
                    filename = CodecUtils.urlDecode(filename, tokens[0]);
                }
            }
        } else if (brokenQuotedMatcher.find()) {
            filename = brokenQuotedMatcher.group(1);
        } else if (brokenUnQuotedMatcher.find()) {
            filename = brokenUnQuotedMatcher.group(1);
        }

        if (filename == null) return null;

        filename = CodecUtils.urlDecode(filename);
        if (!filename.matches("[^\"]")) {
            filename = filename.replaceAll("\\\\(.)", "$1");
        }

        return filename;
    }
}
