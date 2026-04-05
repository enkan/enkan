package enkan.system.repl.command;

import enkan.system.EnkanSystem;
import enkan.system.SystemCommand;
import enkan.system.Transport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * REPL command that interactively generates a new Enkan application project
 * using an OpenAI-compatible chat completion API (Anthropic, OpenAI, LM Studio, Ollama, etc.).
 *
 * <p>Usage: {@code /init}
 *
 * <p>Configuration via environment variables or system properties:
 * <ul>
 *   <li>{@code ENKAN_AI_API_URL} / {@code enkan.ai.apiUrl} — base URL (default: {@code https://api.anthropic.com/v1})</li>
 *   <li>{@code ENKAN_AI_API_KEY} / {@code enkan.ai.apiKey} — API key</li>
 *   <li>{@code ENKAN_AI_MODEL} / {@code enkan.ai.model} — model name (default: {@code claude-sonnet-4-5})</li>
 * </ul>
 *
 * <p>This command is a client-local command: it can be run without a REPL server
 * connection, making it usable directly from the {@code enkan-repl} CLI client.
 *
 * @author kawasima
 */
public class InitCommand implements SystemCommand {
    private static final Logger LOG = LoggerFactory.getLogger(InitCommand.class);
    private static final long serialVersionUID = 1L;

    private static final String DEFAULT_API_URL = "https://api.anthropic.com/v1";
    private static final String DEFAULT_MODEL = "claude-sonnet-4-5";

    private static final String[] REFERENCE_PATHS = {
        "kotowari-example/src/main/java/kotowari/example/ExampleSystemFactory.java",
        "kotowari-example/src/main/java/kotowari/example/ExampleApplicationFactory.java",
        "kotowari-example/src/dev/java/kotowari/example/DevMain.java",
        "kotowari-example/pom.xml"
    };


    @Override
    public boolean execute(EnkanSystem system, Transport transport, String... args) {
        transport.sendOut("\n=== Enkan Project Generator (AI) ===\n");

        String description = askRequired(transport,
                "What kind of application do you want to build?\n> ");
        String projectName = askWithDefault(transport,
                "Project name", inferProjectName(description));
        String groupId = askWithDefault(transport, "Group ID", "com.example");
        String outputDir = askWithDefault(transport,
                "Output directory", "./" + projectName);

        Path outPath = Path.of(outputDir).toAbsolutePath();
        transport.sendOut("\nGenerating project at " + outPath + " ...\n");

        try {
            Files.createDirectories(outPath);
            generateWithApi(transport, description, projectName, groupId, outPath);
        } catch (Exception e) {
            LOG.error("Project generation failed", e);
            transport.sendErr("Generation failed: " + e.getMessage());
        }
        return false;
    }

    @Override
    public String shortDescription() {
        return "Generate a new Enkan project using AI";
    }

    private void generateWithApi(Transport transport, String description,
            String projectName, String groupId, Path outPath)
            throws IOException, InterruptedException {
        String apiUrl = config("ENKAN_AI_API_URL", "enkan.ai.apiUrl", DEFAULT_API_URL);
        String apiKey = config("ENKAN_AI_API_KEY", "enkan.ai.apiKey", "");
        String model  = config("ENKAN_AI_MODEL",   "enkan.ai.model",  DEFAULT_MODEL);

        String[] prompts = buildPrompt(description, projectName, groupId, outPath);
        String requestBody = buildRequestBody(model, prompts[0], prompts[1], true);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl + "/chat/completions"))
                .version(HttpClient.Version.HTTP_1_1)
                .timeout(Duration.ofMinutes(10))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<java.io.InputStream> response = client.send(request,
                HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            String body = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
            transport.sendErr("API error " + response.statusCode() + ": " + body);
            return;
        }

        var fullResponse = new StringBuilder();
        try (var reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String chunk = extractSseContent(line);
                if (chunk != null) {
                    transport.sendOut(chunk);
                    fullResponse.append(chunk);
                }
            }
        }
        int written = writeGeneratedFiles(outPath, fullResponse.toString(), transport);
        transport.sendOut("\nDone! Created " + written + " file(s) at " + outPath);
        transport.sendOut("\nTo start:\n  cd " + outPath.getFileName()
                + " && mvn compile exec:exec -Pdev\n");
    }

    /**
     * Extracts the delta content text from one SSE line.
     * Prefers {@code content} over {@code reasoning_content}; falls back to
     * {@code reasoning_content} when models (e.g. thinking models) only emit
     * reasoning tokens and never produce a separate {@code content} field.
     *
     * @return the unescaped text, or {@code null} if the line has no text delta
     */
    private String extractSseContent(String line) {
        if (!line.startsWith("data: ")) return null;
        String data = line.substring(6).trim();
        if (data.equals("[DONE]")) return null;

        // Try "content":"..." first (non-reasoning answer)
        String content = extractJsonStringField(data, "\"content\":\"", "\"reasoning_content\"");
        if (content != null) return content;

        // Fall back to "reasoning_content":"..." for thinking-only models
        return extractJsonStringField(data, "\"reasoning_content\":\"", null);
    }

    /**
     * Extracts a JSON string value identified by {@code key} from {@code data},
     * skipping any occurrence that is actually part of {@code excludeKey}.
     */
    private String extractJsonStringField(String data, String key, String excludeKey) {
        int searchFrom = 0;
        while (true) {
            int idx = data.indexOf(key, searchFrom);
            if (idx < 0) return null;
            if (excludeKey != null) {
                // Check if this match is actually inside excludeKey
                int excludeStart = idx - (excludeKey.length() - key.length());
                if (excludeStart >= 0 && data.regionMatches(excludeStart, excludeKey, 0, excludeKey.length())) {
                    searchFrom = idx + key.length();
                    continue;
                }
            }
            int start = idx + key.length();
            int end = findJsonStringEnd(data, start);
            if (end <= start) return null;
            return unescapeJson(data.substring(start, end));
        }
    }

    /**
     * Parses the full response text for fenced code blocks and writes each one
     * to a file under {@code outPath}.
     *
     * <p>Expected format produced by the model:
     * <pre>
     * ### path/to/File.java
     * ```java
     * ...code...
     * ```
     * </pre>
     *
     * @return number of files written
     */
    private int writeGeneratedFiles(Path outPath, String response, Transport transport)
            throws IOException {
        int count = 0;
        String[] lines = response.split("\n");
        String currentFile = null;
        var codeLines = new StringBuilder();
        boolean inBlock = false;

        for (String line : lines) {
            if (!inBlock) {
                // Detect file path header: ### some/path/File.java or **`path`**
                String path = extractFilePath(line);
                if (path != null) {
                    currentFile = path;
                } else if (currentFile != null && (line.startsWith("```"))) {
                    inBlock = true;
                    codeLines.setLength(0);
                }
            } else {
                if (line.startsWith("```")) {
                    // End of code block — write the file
                    if (currentFile != null && codeLines.length() > 0) {
                        Path filePath = outPath.resolve(currentFile);
                        Files.createDirectories(filePath.getParent());
                        Files.writeString(filePath, codeLines.toString(), StandardCharsets.UTF_8);
                        transport.sendOut("\n  [write] " + currentFile);
                        count++;
                    }
                    inBlock = false;
                    currentFile = null;
                } else {
                    codeLines.append(line).append("\n");
                }
            }
        }
        return count;
    }

    /**
     * Extracts a relative file path from a markdown header line.
     * Handles formats like {@code ### src/main/java/Foo.java} or {@code **src/main/java/Foo.java**}.
     */
    private static String extractFilePath(String line) {
        // ### path/to/file
        if (line.startsWith("### ")) {
            String candidate = line.substring(4).trim();
            if (looksLikePath(candidate)) return candidate;
        }
        // **path/to/file** or **`path/to/file`**
        if (line.startsWith("**") && line.endsWith("**")) {
            String inner = line.substring(2, line.length() - 2).replace("`", "").trim();
            if (looksLikePath(inner)) return inner;
        }
        return null;
    }

    private static boolean looksLikePath(String s) {
        return !s.contains(" ") && !s.startsWith("http")
                && (s.contains("/") || s.contains("."));
    }

    /** Returns the index just before the closing {@code "} of a JSON string value. */
    private static int findJsonStringEnd(String s, int start) {
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') { i++; continue; }
            if (c == '"') return i;
        }
        return -1;
    }

    private static String unescapeJson(String s) {
        return s.replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private String buildRequestBody(String model, String systemPrompt, String userPrompt, boolean stream) {
        return "{\"model\":\"" + model + "\","
                + "\"stream\":" + stream + ","
                + "\"messages\":["
                + "{\"role\":\"system\",\"content\":" + toJsonString(systemPrompt) + "},"
                + "{\"role\":\"user\",\"content\":" + toJsonString(userPrompt) + "}"
                + "]}";
    }

    private static String toJsonString(String s) {
        return "\"" + s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") + "\"";
    }

    /**
     * Builds the prompt split into [systemPrompt, userMessage].
     * Keeping reference code in the system prompt reduces the thinking budget
     * consumed on the user turn.
     */
    private String[] buildPrompt(String description, String projectName,
            String groupId, Path outPath) {
        String basePackage = groupId + "." + projectName.replace("-", "").replace("_", "");

        // --- System prompt: framework knowledge + output format ---
        var sys = new StringBuilder();
        sys.append("""
                You are an expert Enkan framework developer.
                Enkan is a middleware-chain web framework for Java 25.

                Available components:
                - Web servers: enkan-component-jetty (recommended, virtual threads), enkan-component-undertow
                - Database: enkan-component-doma2, enkan-component-jpa, enkan-component-jooq
                - Connection pool: enkan-component-HikariCP
                - Migration: enkan-component-flyway
                - Template: enkan-component-freemarker, enkan-component-thymeleaf
                - Serialization: enkan-component-jackson
                - Metrics: enkan-component-micrometer, enkan-component-opentelemetry

                The middleware ordering in ApplicationFactory is critical — follow the reference exactly.

                """);

        for (String refPath : REFERENCE_PATHS) {
            String filename = Path.of(refPath).getFileName().toString();
            String content = readReference(refPath);
            if (content != null) {
                sys.append("## ").append(filename).append("\n");
                sys.append("```java\n").append(content).append("\n```\n\n");
            }
        }

        sys.append("""
                Output EVERY file using EXACTLY this format — no exceptions:

                ### relative/path/to/File.java
                ```java
                // file content here
                ```

                For pom.xml:
                ### pom.xml
                ```xml
                <!-- content -->
                ```

                Do NOT include any explanation outside the file blocks.
                """);

        // --- User message: project-specific requirements ---
        var user = new StringBuilder();
        user.append("Generate a complete, compilable Enkan project with these settings:\n\n");
        user.append("- Project name: ").append(projectName).append("\n");
        user.append("- Group ID: ").append(groupId).append("\n");
        user.append("- Artifact ID: ").append(projectName).append("\n");
        user.append("- Base package: ").append(basePackage).append("\n\n");
        user.append("Requirements: ").append(description).append("\n\n");
        user.append("""
                Required files:
                1. pom.xml — parent: net.unit8.enkan:enkan-parent:0.14.2-SNAPSHOT, include 'dev' profile
                2. SystemFactory — component wiring
                3. ApplicationFactory — middleware stack matching the reference ordering
                4. Controller(s) — matching the requirements
                5. src/dev/java/.../DevMain.java — with REPL and WebSocketTransportProvider
                """);

        return new String[]{sys.toString(), user.toString()};
    }

    private String readReference(String relativePath) {
        Path sourcePath = Path.of(System.getProperty("user.dir")).resolve(relativePath);
        if (Files.exists(sourcePath)) {
            try {
                return Files.readString(sourcePath, StandardCharsets.UTF_8);
            } catch (IOException e) {
                LOG.warn("Failed to read reference file: {}", sourcePath, e);
            }
        }
        String resourceName = "enkan/scaffold/reference/" + Path.of(relativePath).getFileName();
        try (var is = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            if (is != null) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            LOG.warn("Failed to read classpath resource: {}", resourceName, e);
        }
        return null;
    }

    private static String config(String envVar, String sysProp, String defaultValue) {
        String val = System.getenv(envVar);
        if (val != null && !val.isBlank()) return val;
        val = System.getProperty(sysProp);
        if (val != null && !val.isBlank()) return val;
        return defaultValue;
    }

    private String askRequired(Transport transport, String prompt) {
        transport.sendPrompt(prompt);
        String answer = transport.recv();
        while (answer == null || answer.isBlank()) {
            transport.sendPrompt("(required) " + prompt);
            answer = transport.recv();
        }
        return answer.trim();
    }

    private String askWithDefault(Transport transport, String label, String defaultValue) {
        transport.sendPrompt(label + " [" + defaultValue + "]: ");
        String answer = transport.recv();
        return (answer == null || answer.isBlank()) ? defaultValue : answer.trim();
    }

    static String inferProjectName(String description) {
        String lower = description.toLowerCase();
        String cleaned = lower.replaceAll("\\b(a|an|the|for|with|and|or|that|which|using|build|create|make|want|to|i)\\b", "")
                .replaceAll("[^a-z0-9\\s]", "")
                .trim()
                .replaceAll("\\s+", "-");
        if (cleaned.isEmpty() || cleaned.length() > 30) {
            return "my-app";
        }
        String[] parts = cleaned.split("-");
        int limit = Math.min(parts.length, 3);
        return String.join("-", java.util.Arrays.copyOf(parts, limit));
    }
}
