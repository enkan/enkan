package enkan.web.middleware.multipart;

import enkan.collection.Parameters;
import enkan.exception.MisconfigurationException;
import enkan.util.ThreadingUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Optional;

import static enkan.web.middleware.multipart.MultipartParser.parseBoundary;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author kawasima
 */
class MultipartParserTest {

    // --- parseBoundary unit tests (RFC 2046 §5.1) ---

    @Test
    void parseBoundaryUnquoted() {
        assertThat(parseBoundary("multipart/form-data; boundary=AaB03x"))
                .isEqualTo("AaB03x");
    }

    @Test
    void parseBoundaryQuoted() {
        assertThat(parseBoundary("multipart/form-data; boundary=\"simple boundary\""))
                .isEqualTo("simple boundary");
    }

    @Test
    void parseBoundaryQuotedWithEscapedQuote() {
        // RFC 2046 §5.1: quoted-string allows backslash-escaped characters.
        assertThat(parseBoundary("multipart/form-data; boundary=\"foo\\\"bar\""))
                .isEqualTo("foo\"bar");
    }

    @Test
    void parseBoundaryParameterAfterBoundary() {
        // boundary= is not required to be the last parameter.
        assertThat(parseBoundary("multipart/form-data; charset=utf-8; boundary=abc"))
                .isEqualTo("abc");
    }

    @Test
    void parseBoundaryWhitespaceAroundEquals() {
        // Optional whitespace around '=' in parameter (OWS).
        assertThat(parseBoundary("multipart/form-data; boundary = abc"))
                .isEqualTo("abc");
    }

    @Test
    void parseBoundaryMalformedQuotedString() {
        // A leading '"' without a closing '"' is malformed; should return null, not fall through to unquoted.
        assertThat(parseBoundary("multipart/form-data; boundary=\"unclosed")).isNull();
    }

    @Test
    void parseBoundaryNotMultipart() {
        assertThat(parseBoundary("text/plain; boundary=abc")).isNull();
    }

    @Test
    void parseBoundaryNull() {
        assertThat(parseBoundary(null)).isNull();
    }
    private Optional<String> getFileContents(Parameters params, String parameterName) {
        return ThreadingUtils.some(((File) params.getIn(parameterName, "tempfile")).toPath(),
                Files::readAllLines,
                slist -> String.join("", slist));
    }

    @Test
    void testIE() throws IOException {
        InputStream is = getClass().getResourceAsStream("/multipart/ie");
        Parameters params = MultipartParser.parse(is, null, "multipart/form-data; boundary=AaB03x", 0);
        Optional<String> content = getFileContents(params, "files");
        assertThat(content)
                .isPresent()
                .contains("contents");
        Files.deleteIfExists(((File) params.getIn("files", "tempfile")).toPath());
    }

    @Test
    void testContentTypeAndNoFilename() throws IOException {
        InputStream is = getClass().getResourceAsStream("/multipart/contentTypeAndNoFilename");
        Parameters params = MultipartParser.parse(is, null, "multipart/form-data; boundary=AaB03x", 0);
        assertThat(params.get("text")).isEqualTo("contents");
    }

    @Test
    void testEmpty() throws IOException {
        InputStream is = getClass().getResourceAsStream("/multipart/empty");
        Parameters params = MultipartParser.parse(is, null, "multipart/form-data; boundary=AaB03x", 0);
        assertThat(params.get("submit-name")).isEqualTo("Larry");
        Optional<String> content = getFileContents(params, "files");
        assertThat(content)
                .isPresent()
                .contains("");
        Files.deleteIfExists(((File) params.getIn("files", "tempfile")).toPath());
    }

    @Test
    void testFilenameWithEncodedWords() throws IOException {
        InputStream is = getClass().getResourceAsStream("/multipart/filenameWithEncodedWords");
        Parameters params = MultipartParser.parse(is, null, "multipart/form-data; boundary=AaB03x", 0);
        Optional<String> content = getFileContents(params, "files");
        assertThat(content)
                .isPresent()
                .contains("contents");
        assertThat(params.getIn("files", "filename")).isEqualTo("файл");
        Files.deleteIfExists(((File) params.getIn("files", "tempfile")).toPath());
    }

    @Test
    void testFilenameWithEscapeQuotes() throws IOException {
        InputStream is = getClass().getResourceAsStream("/multipart/filenameWithEscapedQuotes");
        Parameters params = MultipartParser.parse(is, null, "multipart/form-data; boundary=AaB03x", 0);
        Optional<String> content = getFileContents(params, "files");
        assertThat(content)
                .isPresent()
                .contains("contents");
        assertThat(params.getIn("files", "filename"))
                .isEqualTo("escape \"quotes");
        assertThat(params.getIn("files", "type"))
                .isEqualTo("application/octet-stream");
        assertThat(params.getIn("files", "name"))
                .isEqualTo("files");
        Files.deleteIfExists(((File) params.getIn("files", "tempfile")).toPath());
    }

    @Test
    void testFilenameWithEscapeQuotesAndModificationParam() throws IOException {
        InputStream is = getClass().getResourceAsStream("/multipart/filenameWithEscapedQuotesAndModificationParam");
        Parameters params = MultipartParser.parse(is, null, "multipart/form-data; boundary=AaB03x", 0);
        Optional<String> content = getFileContents(params, "files");
        assertThat(content)
                .isPresent()
                .contains("contents");
        assertThat(params.getIn("files", "filename"))
                .isEqualTo("\"human\" genome.jpeg");
        assertThat(params.getIn("files", "type"))
                .isEqualTo("image/jpeg");
        assertThat(params.getIn("files", "name"))
                .isEqualTo("files");
        Files.deleteIfExists(((File) params.getIn("files", "tempfile")).toPath());
    }

    @Test
    void testFilenameWithPercentEscapedQuotes() throws IOException {
        InputStream is = getClass().getResourceAsStream("/multipart/filenameWithPercentEscapedQuotes");
        Parameters params = MultipartParser.parse(is, null, "multipart/form-data; boundary=AaB03x", 0);
        Optional<String> content = getFileContents(params, "files");
        assertThat(content)
                .isPresent()
                .contains("contents");
        assertThat(params.getIn("files", "filename"))
                .isEqualTo("escape \"quotes");
        assertThat(params.getIn("files", "type"))
                .isEqualTo("application/octet-stream");
        assertThat(params.getIn("files", "name"))
                .isEqualTo("files");
        Files.deleteIfExists(((File) params.getIn("files", "tempfile")).toPath());
    }

    @Test
    void testFilenameWithSingleQuote() throws IOException {
        InputStream is = getClass().getResourceAsStream("/multipart/filenameWithSingleQuote");
        Parameters params = MultipartParser.parse(is, null, "multipart/form-data; boundary=AaB03x", 0);
        Optional<String> content = getFileContents(params, "files");
        assertThat(content)
                .isPresent()
                .contains("contents");
        assertThat(params.getIn("files", "filename"))
                .isEqualTo("bob's flowers.jpg");
        Files.deleteIfExists(((File) params.getIn("files", "tempfile")).toPath());
    }

    @Test
    void testFilenameWithUnescapedPercentages() throws IOException {
        InputStream is = getClass().getResourceAsStream("/multipart/filenameWithUnescapedPercentages");
        Parameters params = MultipartParser.parse(is, null, "multipart/form-data; boundary=----WebKitFormBoundary2NHc7OhsgU68l3Al", 0);
        Optional<String> content = getFileContents(params, "document[attachment]");
        assertThat(content)
                .isPresent()
                .contains("contents");
        assertThat(params.getIn("document[attachment]", "filename"))
                .isEqualTo("100% of a photo.jpeg");
        assertThat(params.getIn("document[attachment]", "type"))
                .isEqualTo("image/jpeg");
        assertThat(params.getIn("document[attachment]", "name"))
                .isEqualTo("document[attachment]");
        Files.deleteIfExists(((File) params.getIn("document[attachment]", "tempfile")).toPath());
    }

    // --- Size limit tests ---

    @Test
    void fileSizeLimitRejectsOversizedUpload() {
        InputStream is = getClass().getResourceAsStream("/multipart/ie");
        // "contents" is 8 bytes; set limit to 4 to trigger rejection
        assertThatThrownBy(() ->
                MultipartParser.parse(is, null, "multipart/form-data; boundary=AaB03x", 0, 4, -1)
        ).isInstanceOf(MisconfigurationException.class);
    }

    @Test
    void formFieldSizeLimitRejectsOversizedField() {
        InputStream is = getClass().getResourceAsStream("/multipart/contentTypeAndNoFilename");
        // "contents" is 8 bytes; set form field limit to 4
        assertThatThrownBy(() ->
                MultipartParser.parse(is, null, "multipart/form-data; boundary=AaB03x", 0, -1, 4)
        ).isInstanceOf(MisconfigurationException.class);
    }

    @Test
    void unlimitedSizeAllowsLargeUpload() throws IOException {
        InputStream is = getClass().getResourceAsStream("/multipart/ie");
        Parameters params = MultipartParser.parse(is, null, "multipart/form-data; boundary=AaB03x", 0, -1, -1);
        Optional<String> content = getFileContents(params, "files");
        assertThat(content).isPresent().contains("contents");
        Files.deleteIfExists(((File) params.getIn("files", "tempfile")).toPath());
    }
}
