package enkan.web.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultHttpResponseTest {
    @Test
    void getBodyAsStreamFromString() throws IOException {
        HttpResponse response = HttpResponse.of("hello");
        try (InputStream in = response.getBodyAsStream()) {
            assertThat(in).isNotNull();
            assertThat(new String(in.readAllBytes())).isEqualTo("hello");
        }
    }

    @Test
    void getBodyAsStreamFromInputStream() throws IOException {
        byte[] data = "stream body".getBytes();
        HttpResponse response = HttpResponse.of(new ByteArrayInputStream(data));
        try (InputStream in = response.getBodyAsStream()) {
            assertThat(new String(in.readAllBytes())).isEqualTo("stream body");
        }
    }

    @Test
    void getBodyAsStreamFromFile(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("body.txt");
        Files.writeString(file, "file body");

        HttpResponse response = HttpResponse.of(file.toFile());
        // Must return an open stream — the bug was that try-with-resources closed
        // the stream before it was returned to the caller.
        try (InputStream in = response.getBodyAsStream()) {
            assertThat(in).isNotNull();
            assertThat(new String(in.readAllBytes())).isEqualTo("file body");
        }
    }

    @Test
    void getBodyAsStreamWhenEmpty() throws IOException {
        HttpResponse response = HttpResponse.of("");
        response.setBody((String) null);
        try (InputStream in = response.getBodyAsStream()) {
            assertThat(in).isNotNull();
            assertThat(in.readAllBytes()).isEmpty();
        }
    }
}
