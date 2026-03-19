package enkan.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MimeTypeUtilsTest {

    @Test
    void htmlExtensionReturnsMimeType() {
        assertThat(MimeTypeUtils.extMimeType("index.html")).isEqualTo("text/html");
    }

    @Test
    void jsonExtensionReturnsMimeType() {
        assertThat(MimeTypeUtils.extMimeType("data.json")).isEqualTo("application/javascript");
    }

    @Test
    void cssExtensionReturnsMimeType() {
        assertThat(MimeTypeUtils.extMimeType("style.css")).isEqualTo("text/css");
    }

    @Test
    void jsExtensionReturnsMimeType() {
        assertThat(MimeTypeUtils.extMimeType("app.js")).isEqualTo("text/javascript");
    }

    @Test
    void pngExtensionReturnsMimeType() {
        assertThat(MimeTypeUtils.extMimeType("image.png")).isEqualTo("image/png");
    }

    @Test
    void unknownExtensionReturnsNull() {
        assertThat(MimeTypeUtils.extMimeType("file.xyz123")).isNull();
    }

    @Test
    void filenameWithNoExtensionReturnsNull() {
        assertThat(MimeTypeUtils.extMimeType("README")).isNull();
    }

    @Test
    void filenameEndingWithDotReturnsNull() {
        assertThat(MimeTypeUtils.extMimeType("file.")).isNull();
    }

    @Test
    void filenameWithMultipleDotsUsesLastExtension() {
        assertThat(MimeTypeUtils.extMimeType("archive.tar.gz")).isEqualTo("application/gzip");
    }

    @Test
    void pdfExtensionReturnsMimeType() {
        assertThat(MimeTypeUtils.extMimeType("doc.pdf")).isEqualTo("application/pdf");
    }

    @Test
    void svgExtensionReturnsMimeType() {
        assertThat(MimeTypeUtils.extMimeType("icon.svg")).isEqualTo("image/svg+xml");
    }
}
