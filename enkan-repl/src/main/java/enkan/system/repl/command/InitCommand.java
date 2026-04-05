package enkan.system.repl.command;

import enkan.system.EnkanSystem;
import enkan.system.SystemCommand;
import enkan.system.Transport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * REPL command that interactively generates a new Enkan application project
 * using the Claude Code CLI.
 *
 * <p>Usage: {@code /init}
 *
 * <p>The command prompts the user for a project description and basic settings,
 * then invokes {@code claude -p} with a tailored prompt that includes Enkan
 * framework reference code. Claude generates the project files directly.
 *
 * <p>This command is a client-local command: it can be run without a REPL server
 * connection, making it usable directly from the {@code enkan-repl} CLI client.
 *
 * @author kawasima
 */
public class InitCommand implements SystemCommand {
    private static final Logger LOG = LoggerFactory.getLogger(InitCommand.class);
    private static final long serialVersionUID = 1L;

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
            generateWithClaude(transport, description, projectName, groupId, outPath);
        } catch (Exception e) {
            LOG.error("Project generation failed", e);
            transport.sendErr("Generation failed: " + e.getMessage());
        }
        return false;
    }

    @Override
    public String shortDescription() {
        return "Generate a new Enkan project using AI (Claude Code CLI)";
    }

    private void generateWithClaude(Transport transport, String description,
            String projectName, String groupId, Path outPath)
            throws IOException, InterruptedException {
        String prompt = buildPrompt(description, projectName, groupId, outPath);

        ProcessBuilder pb = new ProcessBuilder(
                "claude", "-p",
                "--output-format", "stream-json",
                "--verbose",
                "--include-partial-messages",
                "--allowedTools", "Write",
                "--add-dir", outPath.toString()
        );
        pb.directory(outPath.toFile());
        pb.redirectErrorStream(false);
        Process process = pb.start();

        // Write prompt to claude's stdin, then close it
        try (var stdin = new PrintWriter(new OutputStreamWriter(
                process.getOutputStream(), StandardCharsets.UTF_8))) {
            stdin.print(prompt);
        }

        try (var reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                streamJsonLine(transport, line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            transport.sendErr("Claude CLI exited with code " + exitCode);
            return;
        }
        transport.sendOut("\nDone! Created project at " + outPath);
        transport.sendOut("\nTo start:\n  cd " + outPath.getFileName()
                + " && mvn compile exec:exec -Pdev");
    }

    /**
     * Parses one stream-json line from Claude CLI and forwards relevant events
     * to the transport without pulling in a JSON library.
     *
     * <p>Handled event types (with --include-partial-messages):
     * <ul>
     *   <li>{@code stream_event / content_block_delta / text_delta} — real-time text</li>
     *   <li>{@code stream_event / content_block_start / tool_use} — tool invocation name</li>
     * </ul>
     */
    private void streamJsonLine(Transport transport, String json) {
        if (!json.contains("\"type\":\"stream_event\"")) return;

        // Real-time text delta: {"type":"stream_event","event":{"type":"content_block_delta",
        //   "delta":{"type":"text_delta","text":"..."}}}
        if (json.contains("\"type\":\"text_delta\"")) {
            int textIdx = json.indexOf("\"text\":\"");
            if (textIdx >= 0) {
                int start = textIdx + 8;
                int end = findJsonStringEnd(json, start);
                if (end > start) {
                    transport.sendOut(unescapeJson(json.substring(start, end)));
                }
            }
            return;
        }

        // Tool use start: {"type":"stream_event","event":{"type":"content_block_start",
        //   "content_block":{"type":"tool_use","name":"..."}}}
        if (json.contains("\"type\":\"tool_use\"")) {
            int nameIdx = json.indexOf("\"name\":\"");
            if (nameIdx >= 0) {
                int start = nameIdx + 8;
                int end = findJsonStringEnd(json, start);
                if (end > start) {
                    transport.sendOut("\n[tool] " + json.substring(start, end) + "\n");
                }
            }
        }
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

    private String buildPrompt(String description, String projectName,
            String groupId, Path outPath) {
        String basePackage = groupId + "." + projectName.replace("-", "").replace("_", "");

        var sb = new StringBuilder();
        sb.append("You are generating an Enkan web application project.\n\n");
        sb.append("## User Requirements\n");
        sb.append(description).append("\n\n");
        sb.append("## Project Settings\n");
        sb.append("- Project name: ").append(projectName).append("\n");
        sb.append("- Group ID: ").append(groupId).append("\n");
        sb.append("- Artifact ID: ").append(projectName).append("\n");
        sb.append("- Base package: ").append(basePackage).append("\n");
        sb.append("- Output directory: ").append(outPath).append("\n\n");

        sb.append("## Enkan Framework Reference\n\n");
        sb.append("""
                Enkan is a middleware-chain web framework for Java 25.
                Key components available:
                - Web servers: enkan-component-jetty (recommended, virtual threads), enkan-component-undertow
                - Database: enkan-component-doma2, enkan-component-jpa, enkan-component-jooq
                - Connection pool: enkan-component-HikariCP
                - Migration: enkan-component-flyway
                - Template: enkan-component-freemarker, enkan-component-thymeleaf
                - Serialization: enkan-component-jackson
                - Metrics: enkan-component-micrometer, enkan-component-opentelemetry

                The middleware ordering in ApplicationFactory is critical.
                Follow the exact ordering from the reference implementations.

                """);

        sb.append("## Reference Implementations\n\n");
        for (String refPath : REFERENCE_PATHS) {
            String content = readReference(refPath);
            if (content != null) {
                String filename = Path.of(refPath).getFileName().toString();
                sb.append("### ").append(filename).append("\n");
                sb.append("```java\n").append(content).append("\n```\n\n");
            }
        }

        sb.append("""
                ## Instructions
                Generate a complete, compilable Enkan project.
                Write each file using the Write tool to the output directory.

                Required files:
                1. pom.xml — Maven POM with correct enkan dependencies (parent: net.unit8.enkan:enkan-parent:0.14.2-SNAPSHOT)
                2. SystemFactory — component wiring with relationships
                3. ApplicationFactory — middleware stack with correct ordering
                4. Controller(s) — matching the user's requirements
                5. DevMain.java — dev entry point in src/dev/java with REPL and WebSocketTransportProvider
                6. Database migrations (if database is selected) in src/main/resources/db/migration/

                Follow the exact patterns from the reference implementations.
                Use the base package for all generated Java files.
                The pom.xml should include a 'dev' profile for exec:exec with DevMain.
                """);

        return sb.toString();
    }

    private String readReference(String relativePath) {
        // Try source tree first (development mode)
        Path sourcePath = Path.of(System.getProperty("user.dir")).resolve(relativePath);
        if (Files.exists(sourcePath)) {
            try {
                return Files.readString(sourcePath, StandardCharsets.UTF_8);
            } catch (IOException e) {
                LOG.warn("Failed to read reference file: {}", sourcePath, e);
            }
        }
        // Fallback to classpath resource (distribution)
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
