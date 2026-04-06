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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.regex.Pattern;

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
    private static final Pattern HEADING_PATTERN = Pattern.compile("^\\s{0,3}#{1,6}\\s+");
    private static final int MAX_GENERATION_ATTEMPTS = 2;

    /** Shared HTTP client reused for all outgoing requests within one command invocation. */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    /** LLM API configuration resolved once per {@link #execute} call from env vars or system properties. */
    private String apiUrl;
    private String apiKey;
    private String model;

    /**
     * Reference files fetched from GitHub, shared between planning and generation phases.
     * {@code null} means "not yet attempted"; an empty map means "fetched but everything failed"
     * (so offline runs do not re-hit the network on every prompt).
     */
    private Map<String, String> referenceCache = null;

    private static final String[] FORBIDDEN_MARKERS = {
            "springframework",
            "@springbootapplication",
            "springapplication.run",
            "org.springframework.boot"
    };

    private static final String GITHUB_RAW_BASE =
            "https://raw.githubusercontent.com/enkan/enkan/master/";

    private static final String[] REFERENCE_URLS = {
        GITHUB_RAW_BASE + "kotowari-example/src/main/java/kotowari/example/ExampleSystemFactory.java",
        GITHUB_RAW_BASE + "kotowari-example/src/main/java/kotowari/example/ExampleApplicationFactory.java",
        GITHUB_RAW_BASE + "kotowari-example/src/dev/java/kotowari/example/DevMain.java",
        GITHUB_RAW_BASE + "kotowari-example/pom.xml",
    };

    private static class PromptAbortedException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    // ANSI color helpers
    private static final String RESET  = "\u001B[0m";
    private static final String BOLD   = "\u001B[1m";
    private static final String CYAN   = "\u001B[36m";
    private static final String GREEN  = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED    = "\u001B[31m";
    private static final String DIM    = "\u001B[2m";

    private static String section(String title) {
        return "\n" + CYAN + BOLD + "── " + title + " " + DIM + "─".repeat(Math.max(0, 50 - title.length())) + RESET + "\n";
    }

    @Override
    public boolean execute(EnkanSystem system, Transport transport, String... args) {
        transport.sendOut(BOLD + CYAN + "\n  Enkan Project Generator" + RESET + "\n");

        this.apiUrl = config("ENKAN_AI_API_URL", "enkan.ai.apiUrl", DEFAULT_API_URL);
        this.apiKey = config("ENKAN_AI_API_KEY", "enkan.ai.apiKey", "");
        this.model  = config("ENKAN_AI_MODEL",   "enkan.ai.model",  DEFAULT_MODEL);

        if (apiKey.isBlank()) {
            transport.sendErr("LLM API key is not configured. Set ENKAN_AI_API_KEY or enkan.ai.apiKey.");
            return false;
        }
        try {
            verifyApiReachable(apiUrl, apiKey);
        } catch (Exception e) {
            LOG.warn("LLM connectivity check failed: {}", apiUrl, e);
            transport.sendErr("Unable to connect to LLM API (" + apiUrl + "): " + safeMessage(e));
            return false;
        }

        try {
            transport.sendOut(section("Project Setup"));
            String description = askRequired(transport,
                    CYAN + "?" + RESET + " What kind of application do you want to build? ");
            String projectName = askWithDefault(transport,
                    CYAN + "?" + RESET + " Project name", inferProjectName(description));
            String groupId = askWithDefault(transport,
                    CYAN + "?" + RESET + " Group ID", "com.example");
            String outputDir = askWithDefault(transport,
                    CYAN + "?" + RESET + " Output directory", "./" + projectName);
            String approvedPlan = reviewPlanInteractively(transport, description, projectName, groupId, outputDir);
            if (approvedPlan == null) {
                transport.sendErr("Init cancelled.");
                return false;
            }

            Path outPath = Path.of(outputDir).toAbsolutePath();
            transport.sendOut(section("Generating") + DIM + "  " + outPath + RESET + "\n");
            Files.createDirectories(outPath);
            generateWithApi(transport, mergeRequirements(description, approvedPlan), projectName, groupId, outPath);
        } catch (PromptAbortedException ignored) {
            transport.sendErr("Init cancelled.");
        } catch (Exception e) {
            LOG.error("Project generation failed", e);
            transport.sendErr("Generation failed: " + safeMessage(e));
        }
        return false;
    }

    @Override
    public String shortDescription() {
        return "Generate a new Enkan project using AI";
    }

    String reviewPlanInteractively(Transport transport, String description,
            String projectName, String groupId, String outputDir) {
        String plan;
        try {
            plan = generatePlanWithApi(description, projectName, groupId, outputDir, transport);
        } catch (Exception e) {
            transport.sendErr("Failed to create initial plan: " + safeMessage(e));
            return null;
        }
        if (plan == null || plan.isBlank()) {
            transport.sendErr("Failed to create initial plan.");
            return null;
        }

        while (true) {
            transport.sendOut(section("Plan Draft") + renderMarkdownForTerminal(plan) + "\n");
            String feedback = askRequired(transport,
                    "Plan feedback (say 'yes' to proceed, or describe changes, 'cancel' to abort) > ");
            if (isCancelInput(feedback)) {
                return null;
            }
            if (isApprovalInput(feedback)) {
                return plan;
            }
            try {
                String revised = revisePlanWithApi(description, projectName, groupId, outputDir,
                        plan, feedback, transport);
                if (revised == null || revised.isBlank()) {
                    transport.sendErr("Plan revision returned empty result. Please try rephrasing.");
                } else {
                    plan = revised;
                }
            } catch (Exception e) {
                transport.sendErr("Failed to revise plan: " + safeMessage(e));
            }
        }
    }

    String generatePlanWithApi(String description, String projectName, String groupId, String outputDir,
            Transport transport)
            throws IOException, InterruptedException {
        LOG.info("Generating init plan via LLM");
        String systemPrompt = buildPlannerSystemPrompt();
        String userPrompt = "Requirements: " + description + "\n"
                + "Project name: " + projectName + "\n"
                + "Group ID: " + groupId + "\n"
                + "Output directory: " + outputDir + "\n";
        return requestChatCompletion(systemPrompt, userPrompt, "Planning", transport);
    }

    String revisePlanWithApi(String description, String projectName, String groupId, String outputDir,
            String currentPlan, String feedback, Transport transport)
            throws IOException, InterruptedException {
        LOG.info("Revising init plan via LLM");
        String systemPrompt = buildPlannerSystemPrompt();
        String userPrompt = "Original requirements: " + description + "\n"
                + "Project name: " + projectName + "\n"
                + "Group ID: " + groupId + "\n"
                + "Output directory: " + outputDir + "\n\n"
                + "Current plan:\n" + currentPlan + "\n\n"
                + "User feedback:\n" + feedback;
        return requestChatCompletion(systemPrompt, userPrompt, "Revising", transport);
    }

    /**
     * Builds the system prompt for the planning phase, including Enkan reference
     * examples so the planner understands the framework before proposing a plan.
     */
    private String buildPlannerSystemPrompt() {
        var sys = new StringBuilder();
        sys.append("""
                You are an Enkan project planner.
                Enkan is a middleware-chain web framework for Java 25 — NOT Spring Boot.
                NEVER plan to use Spring Boot, Spring Framework, @SpringBootApplication, or org.springframework.*.

                Study the following Enkan reference files before creating a plan:

                """);

        getOrFetchReferenceCache().forEach((filename, content) -> {
            sys.append("## ").append(filename).append("\n");
            sys.append("```\n").append(content).append("\n```\n\n");
        });

        sys.append("""
                Create an implementation plan (no code), concise and concrete.
                Include:
                1) Goal summary
                2) Key architecture choices (which Enkan components to use)
                3) File plan (relative paths)
                4) Risks/assumptions
                """);
        return sys.toString();
    }

    String mergeRequirements(String description, String approvedPlan) {
        return description + "\n\nApproved plan:\n" + approvedPlan;
    }

    void generateWithApi(Transport transport, String description,
            String projectName, String groupId, Path outPath)
            throws IOException, InterruptedException {
        String basePackage = groupId + "." + projectName.replace("-", "").replace("_", "");
        String systemFactoryClass = capitalize(projectName.replace("-", "").replace("_", "")) + "SystemFactory";

        writeFixedTemplates(outPath, basePackage, systemFactoryClass, projectName, groupId);

        String[] prompts = buildPrompt(description, projectName, groupId, outPath);
        String currentUserPrompt = prompts[1];
        String fullResponse = "";
        for (int attempt = 1; attempt <= MAX_GENERATION_ATTEMPTS; attempt++) {
            fullResponse = requestChatCompletion(prompts[0], currentUserPrompt, "Generating", transport);
            if (!containsForbiddenFramework(fullResponse)) {
                break;
            }
            if (attempt < MAX_GENERATION_ATTEMPTS) {
                transport.sendErr("Detected non-Enkan output (Spring markers). Retrying with stricter constraints...");
                currentUserPrompt = currentUserPrompt + "\n\nIMPORTANT: Previous output used Spring Boot."
                        + " Regenerate strictly with Enkan only. Do not include any Spring classes/imports.";
            }
        }
        if (containsForbiddenFramework(fullResponse)) {
            throw new IOException("Generated output contains Spring Boot markers. Please retry with more explicit Enkan requirements.");
        }
        int written = writeGeneratedFiles(outPath, fullResponse.toString(), transport);
        transport.sendOut(GREEN + BOLD + "\n✓ Done!" + RESET + " Created " + written + " file(s) at " + DIM + outPath + RESET + "\n");
        if (!compileAndFix(transport, outPath)) {
            return;
        }
        launchAndConnect(transport, outPath);
    }

    private void writeFixedTemplates(Path outPath, String basePackage, String systemFactoryClass,
            String projectName, String groupId) throws IOException {
        String basePath = basePackage.replace('.', '/');

        Path devMain = outPath.resolve("src/dev/java/" + basePath + "/DevMain.java");
        Files.createDirectories(devMain.getParent());
        Files.writeString(devMain, devMainTemplate(basePackage, systemFactoryClass), StandardCharsets.UTF_8);

        Path pom = outPath.resolve("pom.xml");
        Files.createDirectories(pom.getParent());
        Files.writeString(pom, pomTemplate(groupId, projectName, basePackage), StandardCharsets.UTF_8);

        Path sf = outPath.resolve("src/main/java/" + basePath + "/" + systemFactoryClass + ".java");
        Files.createDirectories(sf.getParent());
        Files.writeString(sf, systemFactoryTemplate(basePackage, systemFactoryClass, projectName), StandardCharsets.UTF_8);
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String devMainTemplate(String basePackage, String systemFactoryClass) {
        return "package " + basePackage + ";\n\n"
                + "import enkan.system.command.JsonRequestCommand;\n"
                + "import enkan.system.command.SqlCommand;\n"
                + "import enkan.system.devel.command.AutoResetCommand;\n"
                + "import enkan.system.devel.command.CompileCommand;\n"
                + "import enkan.system.repl.JShellRepl;\n"
                + "import enkan.system.repl.ReplBoot;\n"
                + "import enkan.system.repl.websocket.WebSocketTransportProvider;\n"
                + "import kotowari.system.KotowariCommandRegister;\n\n"
                + "public class DevMain {\n"
                + "    public static void main(String[] args) {\n"
                + "        JShellRepl repl = new JShellRepl(" + systemFactoryClass + ".class.getName());\n"
                + "        new ReplBoot(repl)\n"
                + "                .register(new KotowariCommandRegister())\n"
                + "                .register(r -> {\n"
                + "                    r.registerCommand(\"sql\", new SqlCommand());\n"
                + "                    r.registerCommand(\"jsonRequest\", new JsonRequestCommand());\n"
                + "                    r.registerLocalCommand(\"autoreset\", new AutoResetCommand(repl));\n"
                + "                    r.registerLocalCommand(\"compile\", new CompileCommand());\n"
                + "                })\n"
                + "                .transport(new WebSocketTransportProvider(3001))\n"
                + "                .onReady(\"/start\")\n"
                + "                .start();\n"
                + "    }\n"
                + "}\n";
    }

    private static String pomTemplate(String groupId, String projectName, String basePackage) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n"
                + "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
                + "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n"
                + "    <modelVersion>4.0.0</modelVersion>\n"
                + "    <parent>\n"
                + "        <groupId>net.unit8.enkan</groupId>\n"
                + "        <artifactId>enkan-parent</artifactId>\n"
                + "        <version>" + enkanVersion() + "</version>\n"
                + "    </parent>\n"
                + "    <groupId>" + groupId + "</groupId>\n"
                + "    <artifactId>" + projectName + "</artifactId>\n"
                + "    <version>0.1.0-SNAPSHOT</version>\n"
                + "    <packaging>jar</packaging>\n\n"
                + "    <dependencies>\n"
                + "        <dependency>\n"
                + "            <groupId>net.unit8.enkan</groupId>\n"
                + "            <artifactId>enkan-system</artifactId>\n"
                + "            <version>${enkan.version}</version>\n"
                + "        </dependency>\n"
                + "        <dependency>\n"
                + "            <groupId>net.unit8.enkan</groupId>\n"
                + "            <artifactId>enkan-repl-server</artifactId>\n"
                + "            <version>${enkan.version}</version>\n"
                + "        </dependency>\n"
                + "        <dependency>\n"
                + "            <groupId>net.unit8.enkan</groupId>\n"
                + "            <artifactId>kotowari</artifactId>\n"
                + "            <version>${enkan.version}</version>\n"
                + "        </dependency>\n"
                + "        <dependency>\n"
                + "            <groupId>net.unit8.enkan</groupId>\n"
                + "            <artifactId>enkan-component-jetty</artifactId>\n"
                + "            <version>${enkan.version}</version>\n"
                + "        </dependency>\n"
                + "        <dependency>\n"
                + "            <groupId>net.unit8.enkan</groupId>\n"
                + "            <artifactId>enkan-component-jackson</artifactId>\n"
                + "            <version>${enkan.version}</version>\n"
                + "        </dependency>\n"
                + "    </dependencies>\n\n"
                + "    <profiles>\n"
                + "        <profile>\n"
                + "            <id>dev</id>\n"
                + "            <activation><activeByDefault>true</activeByDefault></activation>\n"
                + "            <build>\n"
                + "                <plugins>\n"
                + "                    <plugin>\n"
                + "                        <groupId>org.codehaus.mojo</groupId>\n"
                + "                        <artifactId>exec-maven-plugin</artifactId>\n"
                + "                        <configuration>\n"
                + "                            <executable>${java.home}/bin/java</executable>\n"
                + "                            <workingDirectory>${project.basedir}</workingDirectory>\n"
                + "                            <arguments>\n"
                + "                                <argument>-classpath</argument>\n"
                + "                                <classpath/>\n"
                + "                                <argument>" + basePackage + ".DevMain</argument>\n"
                + "                            </arguments>\n"
                + "                        </configuration>\n"
                + "                    </plugin>\n"
                + "                    <plugin>\n"
                + "                        <groupId>org.codehaus.mojo</groupId>\n"
                + "                        <artifactId>build-helper-maven-plugin</artifactId>\n"
                + "                        <version>3.6.1</version>\n"
                + "                        <executions>\n"
                + "                            <execution>\n"
                + "                                <id>add-dev-source</id>\n"
                + "                                <phase>generate-sources</phase>\n"
                + "                                <goals><goal>add-source</goal></goals>\n"
                + "                                <configuration>\n"
                + "                                    <sources><source>src/dev/java</source></sources>\n"
                + "                                </configuration>\n"
                + "                            </execution>\n"
                + "                        </executions>\n"
                + "                    </plugin>\n"
                + "                </plugins>\n"
                + "            </build>\n"
                + "            <dependencies>\n"
                + "                <dependency>\n"
                + "                    <groupId>net.unit8.enkan</groupId>\n"
                + "                    <artifactId>enkan-devel</artifactId>\n"
                + "                    <version>${enkan.version}</version>\n"
                + "                </dependency>\n"
                + "            </dependencies>\n"
                + "        </profile>\n"
                + "    </profiles>\n"
                + "</project>\n";
    }

    private static String systemFactoryTemplate(String basePackage, String systemFactoryClass, String projectName) {
        String appFactoryClass = capitalize(projectName.replace("-", "").replace("_", "")) + "ApplicationFactory";
        return "package " + basePackage + ";\n\n"
                + "import enkan.config.EnkanSystemFactory;\n"
                + "import enkan.system.EnkanSystem;\n"
                + "import enkan.component.ApplicationComponent;\n"
                + "import enkan.component.WebServerComponent;\n"
                + "import enkan.component.builtin.HmacEncoder;\n"
                + "import enkan.component.jackson.JacksonBeansConverter;\n"
                + "import enkan.component.jetty.JettyComponent;\n"
                + "import enkan.Env;\n\n"
                + "import static enkan.component.ComponentRelationship.component;\n"
                + "import static enkan.util.BeanBuilder.builder;\n\n"
                + "public class " + systemFactoryClass + " implements EnkanSystemFactory {\n"
                + "    @Override\n"
                + "    public EnkanSystem create() {\n"
                + "        return EnkanSystem.of(\n"
                + "                \"hmac\", new HmacEncoder(),\n"
                + "                \"jackson\", new JacksonBeansConverter(),\n"
                + "                \"app\", new ApplicationComponent<>(\"" + basePackage + "." + appFactoryClass + "\"),\n"
                + "                \"http\", builder(new JettyComponent())\n"
                + "                        .set(WebServerComponent::setPort, Env.getInt(\"PORT\", 3000))\n"
                + "                        .build()\n"
                + "        ).relationships(\n"
                + "                component(\"http\").using(\"app\"),\n"
                + "                component(\"app\").using(\"jackson\", \"hmac\")\n"
                + "        );\n"
                + "    }\n"
                + "}\n";
    }

    private static final int MAX_FIX_ATTEMPTS = 3;

    /**
     * Runs {@code mvn compile} in the generated project directory.
     * If it fails, sends the errors to the LLM and applies the fixes by
     * overwriting the affected files. Repeats until compilation succeeds
     * or the attempt limit is reached.
     *
     * @return true if compilation eventually succeeded, false if the user
     *         should abort
     */
    private boolean compileAndFix(Transport transport, Path outPath) {
        for (int attempt = 1; attempt <= MAX_FIX_ATTEMPTS; attempt++) {
            transport.sendOut(section("Compiling") + DIM + "  mvn compile" + RESET + "\n");
            String errors = runMvnCompile(outPath);
            if (errors == null) {
                transport.sendOut(GREEN + BOLD + "✓ Compilation succeeded." + RESET + "\n");
                return true;
            }
            transport.sendOut(YELLOW + "⚠ Compilation errors (attempt " + attempt + "/" + MAX_FIX_ATTEMPTS + "):" + RESET + "\n");
            transport.sendOut(DIM + errors + RESET + "\n");
            if (attempt == MAX_FIX_ATTEMPTS) break;

            transport.sendOut(CYAN + "  Asking AI to fix errors..." + RESET + "\n");
            String fixResponse;
            try {
                fixResponse = requestChatCompletion(
                        buildFixSystemPrompt(), buildFixUserPrompt(errors, outPath),
                        "Fixing", transport);
            } catch (Exception e) {
                transport.sendErr("Failed to get fix from AI: " + safeMessage(e));
                break;
            }
            try {
                writeGeneratedFiles(outPath, fixResponse, transport);
            } catch (IOException e) {
                transport.sendErr("Failed to write fixes: " + safeMessage(e));
                break;
            }
        }
        transport.sendErr("Compilation failed. Fix the errors manually and run /connect once the server is up.");
        return false;
    }

    /**
     * Runs {@code mvn compile -Pdev} in the given directory.
     *
     * @return null if compilation succeeded, or the error output if it failed
     */
    private String runMvnCompile(Path outPath) {
        try {
            Process proc = new ProcessBuilder("mvn", "compile", "-Pdev", "--no-transfer-progress")
                    .directory(outPath.toFile())
                    .redirectErrorStream(true)
                    .start();
            String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = proc.waitFor();
            if (exitCode == 0) return null;
            // Extract only the ERROR lines to keep the prompt compact
            return output.lines()
                    .filter(l -> l.startsWith("[ERROR]"))
                    .collect(java.util.stream.Collectors.joining("\n"));
        } catch (IOException | InterruptedException e) {
            return "Failed to run mvn compile: " + safeMessage(e);
        }
    }

    private static String buildFixSystemPrompt() {
        return """
                You are an expert Enkan framework developer.
                Fix the Java compilation errors shown by the user.
                Output ONLY the corrected files using EXACTLY this format:

                ### relative/path/to/File.java
                ```java
                // corrected file content
                ```

                Do NOT output files that do not need changes.
                Do NOT include any explanation outside the file blocks.
                """;
    }

    private static String buildFixUserPrompt(String errors, Path outPath) {
        var sb = new StringBuilder();
        sb.append("Fix these Maven compilation errors in the project at ").append(outPath).append(":\n\n");
        sb.append(errors).append("\n\n");
        // Include the content of each affected file so the LLM has context
        errors.lines()
                .map(line -> extractFileFromError(line, outPath))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .forEach(file -> {
                    try {
                        String content = Files.readString(file, StandardCharsets.UTF_8);
                        String rel = outPath.relativize(file).toString().replace('\\', '/');
                        sb.append("### ").append(rel).append("\n```java\n").append(content).append("\n```\n\n");
                    } catch (IOException ignored) {}
                });
        return sb.toString();
    }

    private static Path extractFileFromError(String errorLine, Path outPath) {
        // [ERROR] /absolute/path/to/File.java:[line,col] message
        if (!errorLine.startsWith("[ERROR]")) return null;
        int bracket = errorLine.indexOf(":[");
        if (bracket < 0) return null;
        String pathPart = errorLine.substring("[ERROR]".length(), bracket).trim();
        try {
            Path p = Path.of(pathPart);
            if (Files.exists(p) && p.startsWith(outPath)) return p;
        } catch (Exception ignored) {}
        return null;
    }

    private void launchAndConnect(Transport transport, Path outPath) {
        String answer = askWithDefault(transport, CYAN + "?" + RESET + " Start and connect to the generated app?", "Y");
        if (!answer.equalsIgnoreCase("y") && !answer.equalsIgnoreCase("yes")) {
            transport.sendOut("To start:\n  cd " + outPath.getFileName()
                    + " && mvn compile exec:exec -Pdev\n");
            return;
        }
        transport.sendOut(YELLOW + "⚡ Starting app" + RESET + " (mvn compile exec:exec -Pdev)...\n");
        try {
            Process appProcess = new ProcessBuilder("mvn", "compile", "exec:exec", "-Pdev")
                    .directory(outPath.toFile())
                    .inheritIO()
                    .start();
            LOG.info("Started app process (pid={})", appProcess.pid());
            transport.startSpinner("Waiting for REPL server");
            int port = waitForPort(outPath, 60);
            transport.stopSpinner();
            if (port < 0) {
                transport.sendErr("Timed out waiting for REPL server. Connect manually with /connect <port>");
                return;
            }
            transport.requestConnect(port);
        } catch (IOException e) {
            transport.sendErr("Failed to start app: " + e.getMessage());
        }
    }

    private int waitForPort(Path outPath, int timeoutSeconds) {
        Path[] candidates = {
            outPath.resolve(".enkan-repl-port"),
            Path.of(System.getProperty("user.home"), ".enkan-repl-port")
        };
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            for (Path p : candidates) {
                try {
                    if (Files.exists(p)) {
                        int port = Integer.parseInt(Files.readString(p).trim());
                        if (port >= 1 && port <= 65535) return port;
                    }
                } catch (IOException | NumberFormatException ignored) {}
            }
            try { Thread.sleep(500); } catch (InterruptedException e) { break; }
        }
        return -1;
    }

    String requestChatCompletion(String systemPrompt, String userPrompt, String spinnerLabel, Transport transport)
            throws IOException, InterruptedException {
        if (transport != null) transport.startSpinner(spinnerLabel);
        try {
            String requestBody = buildRequestBody(model, systemPrompt, userPrompt, true);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl + "/chat/completions"))
                    .version(HttpClient.Version.HTTP_1_1)
                    .timeout(Duration.ofMinutes(10))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<java.io.InputStream> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                String body = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                throw new IOException("API error " + response.statusCode() + ": " + body);
            }

            var fullResponse = new StringBuilder(32768);
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String chunk = extractSseContent(line);
                    if (chunk != null) fullResponse.append(chunk);
                }
            }
            return fullResponse.toString();
        } finally {
            if (transport != null) transport.stopSpinner();
        }
    }

    static String renderMarkdownForTerminal(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        String[] lines = markdown.split("\\R", -1);
        StringBuilder out = new StringBuilder();
        boolean inFence = false;
        for (String rawLine : lines) {
            String line = rawLine;
            String trimmed = line.trim();
            if (trimmed.startsWith("```")) {
                inFence = !inFence;
                continue;
            }
            if (!inFence && HEADING_PATTERN.matcher(line).find()) {
                line = HEADING_PATTERN.matcher(line).replaceFirst("").trim();
            } else if (!inFence && trimmed.startsWith("- [ ] ")) {
                line = line.replaceFirst("- \\[ \\] ", "[ ] ");
            } else if (!inFence && trimmed.startsWith("- [x] ")) {
                line = line.replaceFirst("- \\[x\\] ", "[x] ");
            } else if (!inFence && trimmed.startsWith("* ")) {
                line = line.replaceFirst("\\* ", "- ");
            }
            out.append(line).append('\n');
        }
        return out.toString().trim();
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
    String extractJsonStringField(String data, String key, String excludeKey) {
        int searchFrom = 0;
        while (true) {
            int idx = data.indexOf(key, searchFrom);
            if (idx < 0) return null;
            if (excludeKey != null) {
                int excludeStart = idx - (excludeKey.length() - key.length());
                if (excludeStart >= 0 && data.regionMatches(excludeStart, excludeKey, 0, excludeKey.length())) {
                    searchFrom = idx + key.length();
                    continue;
                }
            }
            int start = idx + key.length();
            int end = findJsonStringEnd(data, start);
            if (end < 0 || end == start) return null;
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
    int writeGeneratedFiles(Path outPath, String response, Transport transport)
            throws IOException {
        Path normalizedOut = outPath.normalize();
        int count = 0;
        String[] lines = response.split("\n");
        String currentFile = null;
        var codeLines = new StringBuilder();
        boolean inBlock = false;

        for (String line : lines) {
            if (!inBlock) {
                String path = extractFilePath(line);
                if (path != null) {
                    currentFile = isFixedTemplateFile(path) ? null : path;
                    if (currentFile != null) {
                        transport.sendOut(GREEN + "  ✓" + RESET + " " + currentFile + "\n");
                    }
                } else if (currentFile != null && (line.startsWith("```"))) {
                    inBlock = true;
                    codeLines.setLength(0);
                }
            } else {
                if (line.startsWith("```")) {
                    if (currentFile != null && codeLines.length() > 0) {
                        Path filePath = outPath.resolve(currentFile).normalize();
                        if (!filePath.startsWith(normalizedOut)) {
                            LOG.warn("Skipping path that escapes project root: {}", currentFile);
                        } else {
                            Files.createDirectories(filePath.getParent());
                            Files.writeString(filePath, codeLines.toString(), StandardCharsets.UTF_8);
                            count++;
                        }
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
    static String extractFilePath(String line) {
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

    static boolean isFixedTemplateFile(String path) {
        String normalized = path.replace('\\', '/');
        String filename = normalized.substring(normalized.lastIndexOf('/') + 1);
        return filename.equals("pom.xml")
                || filename.equals("DevMain.java")
                || filename.equals("Main.java")
                || filename.endsWith("SystemFactory.java");
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

    static String unescapeJson(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(++i);
                switch (next) {
                    case 'n'  -> sb.append('\n');
                    case 'r'  -> sb.append('\r');
                    case 't'  -> sb.append('\t');
                    case '"'  -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    default   -> { sb.append('\\'); sb.append(next); }
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private String buildRequestBody(String model, String systemPrompt, String userPrompt, boolean stream) {
        return "{\"model\":" + toJsonString(model) + ","
                + "\"stream\":" + stream + ","
                + "\"messages\":["
                + "{\"role\":\"system\",\"content\":" + toJsonString(systemPrompt) + "},"
                + "{\"role\":\"user\",\"content\":" + toJsonString(userPrompt) + "}"
                + "]}";
    }

    private static String toJsonString(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"'  -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default   -> sb.append(c);
            }
        }
        return sb.append('"').toString();
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

        getOrFetchReferenceCache().forEach((filename, content) -> {
            sys.append("## ").append(filename).append("\n");
            sys.append("```java\n").append(content).append("\n```\n\n");
        });

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
                This generator is strictly for Enkan.
                NEVER use Spring Boot, Spring Framework, @SpringBootApplication, SpringApplication, or org.springframework.*.
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
                The following files are already written — do NOT generate them:
                - pom.xml (including the 'dev' profile)
                - src/dev/java/.../DevMain.java
                - src/main/java/.../*SystemFactory.java (skeleton already written)
                - src/main/java/.../Main.java (no Main class needed — DevMain handles startup)

                Required files to generate:
                1. ApplicationFactory — middleware stack matching the reference ordering
                2. Controller(s) — matching the requirements
                3. Any domain/model/DAO classes needed

                IMPORTANT: EnkanSystemFactory is an interface — NEVER instantiate it directly with `new EnkanSystemFactory()`.
                """);

        return new String[]{sys.toString(), user.toString()};
    }

    private Map<String, String> getOrFetchReferenceCache() {
        if (referenceCache == null) {
            referenceCache = fetchAllReferences();
        }
        return referenceCache;
    }

    /**
     * Reads the Enkan version from this jar's manifest ({@code Implementation-Version}).
     * Returns {@code "UNKNOWN"} when running outside a packaged jar (e.g. tests, IDE).
     * An "UNKNOWN" marker in the generated {@code pom.xml} makes manifest-read failures
     * obvious, rather than silently pinning a value that will be stale after the next release.
     */
    static String enkanVersion() {
        String v = InitCommand.class.getPackage().getImplementationVersion();
        return (v != null && !v.isBlank()) ? v : "UNKNOWN";
    }

    /**
     * Fetches all reference URLs in parallel and returns them as a filename → content map.
     * Results preserve the declaration order of {@link #REFERENCE_URLS}. Any future that
     * completes exceptionally is logged and skipped (so a single failure does not abort
     * the whole init run).
     */
    private Map<String, String> fetchAllReferences() {
        List<CompletableFuture<Map.Entry<String, String>>> futures = new ArrayList<>();
        for (String url : REFERENCE_URLS) {
            futures.add(fetchReferenceAsync(url));
        }
        Map<String, String> cache = new LinkedHashMap<>();
        for (CompletableFuture<Map.Entry<String, String>> f : futures) {
            try {
                Map.Entry<String, String> entry = f.join();
                if (entry != null) {
                    cache.put(entry.getKey(), entry.getValue());
                }
            } catch (CompletionException | CancellationException e) {
                LOG.warn("Reference fetch future failed unexpectedly", e);
            }
        }
        return cache;
    }

    private CompletableFuture<Map.Entry<String, String>> fetchReferenceAsync(String url) {
        String filename = url.substring(url.lastIndexOf('/') + 1);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .version(HttpClient.Version.HTTP_1_1)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        return httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .handle((res, ex) -> {
                    if (ex != null) {
                        LOG.warn("Failed to fetch reference: {}", url, ex);
                        return null;
                    }
                    if (res.statusCode() != 200) {
                        LOG.warn("Failed to fetch reference (HTTP {}): {}", res.statusCode(), url);
                        return null;
                    }
                    return Map.entry(filename, res.body());
                });
    }

    void verifyApiReachable(String apiUrl, String apiKey) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .version(HttpClient.Version.HTTP_1_1)
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + apiKey)
                .GET()
                .build();
        // Any HTTP response means connectivity is OK (2xx/4xx/5xx all reachable).
        httpClient.send(request, HttpResponse.BodyHandlers.discarding());
    }

    private static String config(String envVar, String sysProp, String defaultValue) {
        String val = System.getenv(envVar);
        if (val != null && !val.isBlank()) return val;
        val = System.getProperty(sysProp);
        if (val != null && !val.isBlank()) return val;
        return defaultValue;
    }

    private String safeMessage(Throwable t) {
        String message = t.getMessage();
        return (message == null || message.isBlank()) ? t.getClass().getSimpleName() : message;
    }

    static boolean isApprovalInput(String input) {
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("y")
                || normalized.equals("yes")
                || normalized.equals("ok")
                || normalized.equals("okay")
                || normalized.equals("go")
                || normalized.equals("proceed")
                || normalized.equals("進めて")
                || normalized.equals("お願いします")
                || normalized.equals("承認");
    }

    static boolean isCancelInput(String input) {
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("cancel")
                || normalized.equals("quit")
                || normalized.equals("exit")
                || normalized.equals("stop")
                || normalized.equals("中止")
                || normalized.equals("キャンセル")
                || normalized.equals("やめる");
    }

    static boolean containsForbiddenFramework(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String marker : FORBIDDEN_MARKERS) {
            if (lower.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private String askRequired(Transport transport, String prompt) {
        transport.sendPrompt(prompt);
        String answer = transport.recv();
        while (answer != null && answer.isBlank()) {
            transport.sendPrompt(RED + "(required)" + RESET + " " + prompt);
            answer = transport.recv();
        }
        if (answer == null) {
            throw new PromptAbortedException();
        }
        return answer.trim();
    }

    private String askWithDefault(Transport transport, String label, String defaultValue) {
        transport.sendPrompt(label + " [" + defaultValue + "]: ");
        String answer = transport.recv();
        if (answer == null) {
            throw new PromptAbortedException();
        }
        return answer.isBlank() ? defaultValue : answer.trim();
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
