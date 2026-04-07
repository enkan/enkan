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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * REPL command that interactively generates a new Enkan application project using an
 * OpenAI-compatible chat completion API. Works with providers that expose the OpenAI
 * {@code /chat/completions} protocol (OpenAI, Anthropic's OpenAI-compatible endpoint,
 * LM Studio, Ollama, vLLM, etc.).
 *
 * <p>Usage: {@code /init}
 *
 * <p>Configuration via environment variables or system properties:
 * <ul>
 *   <li>{@code ENKAN_AI_API_URL} / {@code enkan.ai.apiUrl} — base URL for the
 *       OpenAI-compatible endpoint. If the URL already ends with {@code /chat/completions}
 *       it is used verbatim; otherwise {@code /chat/completions} is appended.
 *       Default: {@code https://api.anthropic.com/v1} (Anthropic's OpenAI-compatible
 *       endpoint; see <a href="https://docs.anthropic.com/en/api/openai-sdk">docs</a>).</li>
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
    String apiUrl;
    String apiKey;
    String model;

    /**
     * Reference files fetched from GitHub, shared between planning and generation phases.
     * {@code null} means "not yet attempted"; an empty map means "fetched but everything failed"
     * (so offline runs do not re-hit the network on every prompt).
     */
    private Map<String, String> referenceCache = null;

    /**
     * Cached result of {@link #loadInitReference()}. Loaded once on first use; reused
     * across the planning, generation, and fix-loop phases so classpath resources are
     * not re-read on every prompt build.
     */
    private Map<String, String> initReferenceCache = null;

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

    /**
     * Canonical copy-paste-ready patterns shipped with enkan-repl-client as classpath
     * resources under {@code /init-reference/}. These are the single source of truth
     * for what the generator expects the LLM to produce. They load synchronously on
     * first use (no network), so the generator works fully offline and is not at the
     * mercy of transient GitHub fetch failures.
     *
     * <p>Order matters — the list roughly follows a narrative flow (overview →
     * imports → routes → controller → domain → decoder → migration → component
     * specifics) so the prompt reads top-to-bottom for the LLM.
     *
     * <p>To add a new reference file: create it under
     * {@code src/main/resources/init-reference/} and append its path here.
     */
    private static final String[] INIT_REFERENCE_RESOURCES = {
        "/init-reference/README.md",
        "/init-reference/components/overview.md",
        "/init-reference/imports.md",
        "/init-reference/patterns/routes.md",
        "/init-reference/patterns/controller.md",
        "/init-reference/patterns/domain-record.md",
        "/init-reference/patterns/raoh-decoder.md",
        "/init-reference/patterns/flyway-migration.md",
        "/init-reference/components/jooq.md",
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
            // Do not pass 'e' itself to the logger — some HttpClient implementations include
            // request headers (including Authorization) in the exception cause chain.
            // Log class name + message only; the stack trace is intentionally omitted.
            String errSummary = e.getClass().getSimpleName()
                    + (e.getMessage() != null ? ": " + e.getMessage() : "");
            LOG.warn("LLM connectivity check failed: {} — {}", apiUrl, errSummary);
            transport.sendErr("Unable to connect to LLM API (" + apiUrl + "): " + safeMessage(e));
            return false;
        }

        // Pre-flight: fail fast BEFORE asking the user for project details, so they do
        // not waste time answering prompts for a generation run that can't finish.
        if (!verifyMavenAvailable(transport)) {
            return false;
        }
        if (resolveEnkanVersionOrAbort(transport) == null) {
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
            if (!SAFE_COORD_PATTERN.matcher(groupId).matches()) {
                transport.sendErr("Group ID '" + groupId + "' contains invalid characters. Use only alphanumerics, dots, hyphens, and underscores.");
                return false;
            }
            if (!SAFE_ARTIFACT_PATTERN.matcher(projectName).matches()) {
                transport.sendErr("Project name '" + projectName + "' contains invalid characters. Use only alphanumerics, hyphens, and underscores (no dots).");
                return false;
            }
            String outputDir = askWithDefault(transport,
                    CYAN + "?" + RESET + " Output directory", "./" + projectName);
            String approvedPlan = reviewPlanInteractively(transport, description, projectName, groupId, outputDir);
            if (approvedPlan == null) {
                transport.sendErr("Init cancelled.");
                return false;
            }

            if (outputDir.contains("..")) {
                transport.sendErr("Output directory must not contain '..' segments.");
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
     * Builds the system prompt for the planning phase. The planner sees the same
     * default-stack constraints as the generator (jOOQ + Raoh + HikariCP + Flyway + H2)
     * so that the plan it produces matches what the generator will actually emit —
     * otherwise the plan promises one stack and the code uses another.
     */
    String buildPlannerSystemPrompt() {
        var sys = new StringBuilder();
        sys.append("""
                You are an Enkan project planner.
                Enkan is a middleware-chain web framework for Java 25 — NOT Spring Boot.
                NEVER plan to use Spring Boot, Spring Framework, Doma2, JPA/Hibernate, or Lombok
                unless the user *explicitly* asks for them in the requirements.

                DEFAULT STACK (use this unless the user asks for something else):
                  - Web: Jetty (virtual threads) + Kotowari MVC + Jackson JSON
                  - Persistence: HikariCP + jOOQ DSLContext + Flyway migrations + H2 in-memory
                  - Row → domain decoding: Raoh (net.unit8.raoh.jooq.JooqRecordDecoders)

                FIXED (already generated — do NOT mention them in the file plan):
                  - pom.xml
                  - src/dev/java/.../DevMain.java
                  - src/main/java/.../*SystemFactory.java
                  - src/main/java/.../*ApplicationFactory.java
                  - src/main/java/.../jaxrs/JsonBodyReader.java and JsonBodyWriter.java

                TO GENERATE (plan these):
                  - src/main/java/.../RoutesDef.java — exposes `public static Routes routes()`
                    (called by the fixed ApplicationFactory, must live in the base package)
                  - Controllers (one per resource, injected with DSLContext via request extension)
                  - Domain records / Raoh decoders
                  - src/main/resources/db/migration/V1__<name>.sql — H2-compatible DDL

                == CANONICAL PATTERNS (authoritative — copy these verbatim) ==
                These are the single source of truth for API shapes. Older examples
                you may remember from training (Doma2, JPA, Spring) are WRONG for
                this stack. When in doubt, copy from these files.

                """);

        loadInitReference().forEach((path, content) -> {
            sys.append("### ").append(path).append("\n");
            sys.append(content).append("\n\n");
        });

        sys.append("""
                == LEGACY REFERENCE (architectural context only) ==
                The files below are fetched from the kotowari-example project. They
                use Doma2, NOT jOOQ — read them to understand the middleware
                ordering and component wiring, but NEVER import from
                kotowari.example.* and NEVER copy Doma2 code.

                """);

        getOrFetchReferenceCache().forEach((filename, content) -> {
            sys.append("#### ").append(filename).append(" (uses Doma2 — substitute jOOQ)\n");
            sys.append("```\n").append(content).append("\n```\n\n");
        });

        sys.append("""
                Create an implementation plan (no code), concise and concrete.
                Include:
                1) Goal summary
                2) Chosen stack (confirm default or note the requested deviation and why)
                3) Schema (tables and columns, H2 DDL style)
                4) File plan — only files that still need to be written by the generator
                   (omit the FIXED files listed above)
                5) Risks / assumptions
                """);
        return sys.toString();
    }

    String mergeRequirements(String description, String approvedPlan) {
        return description + "\n\nApproved plan:\n" + approvedPlan;
    }

    void generateWithApi(Transport transport, String description,
            String projectName, String groupId, Path outPath)
            throws IOException, InterruptedException {
        String basePackage = groupId + "." + normalizeProjectName(projectName);
        String systemFactoryClass = capitalize(normalizeProjectName(projectName)) + "SystemFactory";
        String appFactoryClass = capitalize(normalizeProjectName(projectName)) + "ApplicationFactory";
        String enkanVersion = resolveEnkanVersionOrAbort(transport);
        if (enkanVersion == null) {
            return;
        }

        writeFixedTemplates(outPath, basePackage, systemFactoryClass, projectName, groupId, enkanVersion);

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
        int written = writeGeneratedFiles(outPath, fullResponse.toString(), appFactoryClass, systemFactoryClass, transport);
        transport.sendOut(GREEN + BOLD + "\n✓ Done!" + RESET + " Created " + written + " file(s) at " + DIM + outPath + RESET + "\n");

        List<String> issues = validateGeneration(outPath, fullResponse, basePackage, description);
        if (!issues.isEmpty()) {
            transport.sendOut(YELLOW + "⚠ Validation issues:" + RESET + "\n");
            issues.forEach(i -> transport.sendOut(DIM + "  - " + i + RESET + "\n"));
            // The fix loop will pick these up via the first compile attempt; a broken
            // import or a missing controller surfaces quickly via javac and gets
            // forwarded to the LLM with the same machinery. No separate fix path needed.
        }

        if (!compileAndFix(transport, outPath, appFactoryClass, systemFactoryClass)) {
            return;
        }
        launchAndConnect(transport, outPath);
    }

    /**
     * Packages that the generator is never allowed to produce code importing from,
     * regardless of what the user asks for. Spring is always forbidden; the others are
     * conditionally allowed via {@link #hasUserRequestedAlternative} (see
     * {@link #importAllowlistViolations}).
     */
    private static final String[] ALWAYS_FORBIDDEN_IMPORT_PREFIXES = {
            "org.springframework.",
            "lombok.",
    };
    private static final String[] CONDITIONALLY_FORBIDDEN_IMPORT_PREFIXES = {
            "org.seasar.doma.",
            "jakarta.persistence.",
            "javax.persistence.",
    };

    /**
     * Fast post-generation checks that would otherwise only surface as cryptic
     * {@code mvn compile} errors. Returns a list of human-readable issues; an empty
     * list means the generation looks structurally sound.
     *
     * <p>Checks performed:
     * <ol>
     *   <li>{@code RoutesDef.java} exists in the base package.</li>
     *   <li>Every {@code FooController.class} reference inside {@code RoutesDef}
     *       has a matching {@code FooController.java} file somewhere under
     *       {@code outPath}.</li>
     *   <li>No generated source imports a forbidden package (Spring, Lombok; also
     *       Doma/JPA unless the user requested them).</li>
     *   <li>At least one Flyway migration exists in
     *       {@code src/main/resources/db/migration/}.</li>
     *   <li>The {@code ### MANIFEST} block declared by the LLM (if any) matches the
     *       files actually written.</li>
     * </ol>
     */
    List<String> validateGeneration(Path outPath, String llmResponse, String basePackage, String description) {
        List<String> issues = new ArrayList<>();
        String basePath = basePackage.replace('.', '/');
        Path routesDef = outPath.resolve("src/main/java/" + basePath + "/RoutesDef.java");

        // 1. RoutesDef present
        if (!Files.exists(routesDef)) {
            issues.add("Missing " + outPath.relativize(routesDef)
                    + " — the fixed ApplicationFactory calls RoutesDef.routes() and will not compile without it.");
        } else {
            // 2. Controller references
            String routesContent;
            try {
                routesContent = Files.readString(routesDef, StandardCharsets.UTF_8);
                var m = Pattern.compile("\\b(\\w+Controller)\\.class").matcher(routesContent);
                Set<String> mentioned = new LinkedHashSet<>();
                while (m.find()) mentioned.add(m.group(1));
                for (String simpleName : mentioned) {
                    boolean found;
                    try (var stream = Files.walk(outPath.resolve("src/main/java"))) {
                        found = stream
                                .filter(p -> p.getFileName().toString().equals(simpleName + ".java"))
                                .findFirst()
                                .isPresent();
                    } catch (IOException e) {
                        found = false;
                    }
                    if (!found) {
                        issues.add("RoutesDef references " + simpleName + ".class but no "
                                + simpleName + ".java was generated.");
                    }
                }
            } catch (IOException e) {
                issues.add("Could not read RoutesDef.java: " + safeMessage(e));
            }
        }

        // 3. Import allowlist
        issues.addAll(importAllowlistViolations(outPath, description));

        // 4. Flyway migration present
        Path migrations = outPath.resolve("src/main/resources/db/migration");
        if (Files.isDirectory(migrations)) {
            try (var stream = Files.list(migrations)) {
                boolean anySql = stream.anyMatch(p -> p.getFileName().toString().endsWith(".sql"));
                if (!anySql) {
                    issues.add("No Flyway migration at " + outPath.relativize(migrations)
                            + "/V1__*.sql — schema will be empty and controllers will fail at first query.");
                }
            } catch (IOException e) {
                issues.add("Could not scan migration directory: " + safeMessage(e));
            }
        }

        // 5. Manifest comparison (advisory — manifest is optional)
        var manifest = parseManifest(llmResponse);
        if (!manifest.isEmpty()) {
            Path normalizedOut = outPath.normalize();
            for (String declared : manifest) {
                if (isFixedTemplateFile(declared)) continue;
                Path p = outPath.resolve(declared).normalize();
                if (!p.startsWith(normalizedOut)) {
                    issues.add("Manifest contains a path that escapes the project root: " + declared);
                    continue;
                }
                if (!Files.exists(p)) {
                    issues.add("Manifest promises " + declared + " but it was not written.");
                }
            }
        }

        return issues;
    }

    /**
     * Scans all generated {@code .java} files under {@code src/main/java} for forbidden
     * imports. Spring and Lombok are always forbidden. Doma/JPA are forbidden unless
     * the user's description explicitly requested them (allowing an escape hatch).
     */
    List<String> importAllowlistViolations(Path outPath, String description) {
        List<String> issues = new ArrayList<>();
        boolean allowJpa = hasUserRequestedAlternative(description, "jpa", "hibernate", "jakarta.persistence");
        boolean allowDoma = hasUserRequestedAlternative(description, "doma2", "doma ");

        Path srcRoot = outPath.resolve("src/main/java");
        if (!Files.isDirectory(srcRoot)) return issues;
        try (var stream = Files.walk(srcRoot)) {
            stream.filter(p -> p.toString().endsWith(".java"))
                  .forEach(p -> {
                      try {
                          String content = Files.readString(p, StandardCharsets.UTF_8);
                          String rel = outPath.relativize(p).toString().replace('\\', '/');
                          content.lines()
                                  .filter(line -> line.startsWith("import "))
                                  .forEach(line -> {
                                      String raw = line.substring("import ".length()).trim();
                                      // Strip the optional "static " keyword only when it appears
                                      // at the very start of the remainder (after "import "),
                                      // so package names containing the word "static" are not mangled.
                                      String imp = raw.startsWith("static ") ? raw.substring("static ".length()) : raw;
                                      if (imp.endsWith(";")) imp = imp.substring(0, imp.length() - 1);
                                      for (String forbidden : ALWAYS_FORBIDDEN_IMPORT_PREFIXES) {
                                          if (imp.startsWith(forbidden)) {
                                              issues.add(rel + " imports forbidden package: " + imp);
                                          }
                                      }
                                      for (String forbidden : CONDITIONALLY_FORBIDDEN_IMPORT_PREFIXES) {
                                          if (!imp.startsWith(forbidden)) continue;
                                          boolean allowed = (forbidden.contains("doma") && allowDoma)
                                                  || (forbidden.contains("persistence") && allowJpa);
                                          if (!allowed) {
                                              issues.add(rel + " imports " + imp
                                                      + " — use the default jOOQ+Raoh stack instead.");
                                          }
                                      }
                                  });
                      } catch (IOException e) {
                          issues.add("Could not read " + outPath.relativize(p) + " for import check: " + safeMessage(e));
                      }
                  });
        } catch (IOException e) {
            issues.add("Could not walk source tree for import check: " + safeMessage(e));
        }
        return issues;
    }

    /**
     * Parses the optional {@code ### MANIFEST} block the generation prompt asks the
     * LLM to emit. Returns an empty list if no manifest is present — the check is
     * advisory, not required, so older prompt versions and LLMs that ignore the
     * instruction still work.
     */
    static List<String> parseManifest(String response) {
        List<String> result = new ArrayList<>();
        if (response == null) return result;
        String[] lines = response.split("\\R", -1);
        boolean inManifest = false;
        boolean inBlock = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (!inManifest) {
                if (trimmed.equalsIgnoreCase("### MANIFEST")) inManifest = true;
                continue;
            }
            if (!inBlock) {
                if (trimmed.startsWith("```")) {
                    inBlock = true;
                }
                continue;
            }
            if (trimmed.startsWith("```")) break;
            if (!trimmed.isEmpty()) result.add(trimmed);
        }
        return result;
    }

    /**
     * Writes all fixed (deterministic) template files. These files are never generated
     * or modified by the LLM — they encode the parts of the project that must be
     * correct regardless of the user's requirements:
     *
     * <ul>
     *   <li>{@code pom.xml} — standalone POM with the default dependency set.</li>
     *   <li>{@code DevMain.java} — REPL boot entry point.</li>
     *   <li>{@code *SystemFactory.java} — HikariCP/Flyway/jOOQ/Jetty wiring.</li>
     *   <li>{@code *ApplicationFactory.java} — middleware stack with jOOQ transaction
     *       support and JSON SerDes. Routes are delegated to {@code Routes.define(...)}
     *       inside this class; the LLM generates controllers only.</li>
     *   <li>{@code JsonBodyReader.java} / {@code JsonBodyWriter.java} — required by the
     *       SerDes middleware because {@code kotowari} ships only the
     *       {@code ToStringBodyWriter}. Copied verbatim from {@code kotowari-example}.</li>
     * </ul>
     *
     * @param enkanVersion resolved Enkan version to bake into the POM (must not be blank).
     */
    private void writeFixedTemplates(Path outPath, String basePackage, String systemFactoryClass,
            String projectName, String groupId, String enkanVersion) throws IOException {
        String basePath = basePackage.replace('.', '/');
        String appFactoryClass = capitalize(normalizeProjectName(projectName)) + "ApplicationFactory";

        Path devMain = outPath.resolve("src/dev/java/" + basePath + "/DevMain.java");
        Files.createDirectories(devMain.getParent());
        Files.writeString(devMain, devMainTemplate(basePackage, systemFactoryClass), StandardCharsets.UTF_8);

        Path pom = outPath.resolve("pom.xml");
        Files.createDirectories(pom.getParent());
        Files.writeString(pom, pomTemplate(groupId, projectName, basePackage, enkanVersion), StandardCharsets.UTF_8);

        Path sf = outPath.resolve("src/main/java/" + basePath + "/" + systemFactoryClass + ".java");
        Files.createDirectories(sf.getParent());
        Files.writeString(sf, systemFactoryTemplate(basePackage, systemFactoryClass, projectName), StandardCharsets.UTF_8);

        Path af = outPath.resolve("src/main/java/" + basePath + "/" + appFactoryClass + ".java");
        Files.createDirectories(af.getParent());
        Files.writeString(af, applicationFactoryTemplate(basePackage, appFactoryClass), StandardCharsets.UTF_8);

        Path jsonReader = outPath.resolve("src/main/java/" + basePath + "/jaxrs/JsonBodyReader.java");
        Files.createDirectories(jsonReader.getParent());
        Files.writeString(jsonReader, jsonBodyReaderTemplate(basePackage), StandardCharsets.UTF_8);

        Path jsonWriter = outPath.resolve("src/main/java/" + basePath + "/jaxrs/JsonBodyWriter.java");
        Files.writeString(jsonWriter, jsonBodyWriterTemplate(basePackage), StandardCharsets.UTF_8);

        // Empty Flyway migration directory so Flyway's classpath scan finds something.
        // The LLM is required to write V1__<name>.sql into this directory.
        Files.createDirectories(outPath.resolve("src/main/resources/db/migration"));
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Strips hyphens and underscores from a project name to form a valid Java identifier segment. */
    private static String normalizeProjectName(String projectName) {
        return projectName.replace("-", "").replace("_", "");
    }

    private static String devMainTemplate(String basePackage, String systemFactoryClass) {
        return """
                package %s;

                import enkan.system.command.JsonRequestCommand;
                import enkan.system.command.SqlCommand;
                import enkan.system.devel.command.AutoResetCommand;
                import enkan.system.devel.command.CompileCommand;
                import enkan.system.repl.JShellRepl;
                import enkan.system.repl.ReplBoot;
                import enkan.system.repl.websocket.WebSocketTransportProvider;
                import kotowari.system.KotowariCommandRegister;

                public class DevMain {
                    public static void main(String[] args) {
                        JShellRepl repl = new JShellRepl(%s.class.getName());
                        new ReplBoot(repl)
                                .register(new KotowariCommandRegister())
                                .register(r -> {
                                    r.registerCommand("sql", new SqlCommand());
                                    r.registerCommand("jsonRequest", new JsonRequestCommand());
                                    r.registerLocalCommand("autoreset", new AutoResetCommand(repl));
                                    r.registerLocalCommand("compile", new CompileCommand());
                                })
                                .transport(new WebSocketTransportProvider(3001))
                                .onReady("/start")
                                .start();
                    }
                }
                """.formatted(basePackage, systemFactoryClass);
    }

    /**
     * Builds a standalone {@code pom.xml} for the generated project. The POM has no
     * {@code <parent>} — {@code enkan-parent} is an internal aggregator that external
     * projects cannot inherit from — and instead declares all plugin and dependency
     * versions explicitly. The default persistence stack is jOOQ + Raoh + HikariCP +
     * Flyway + H2 in-memory; the LLM generates controllers and migrations on top.
     *
     * @param enkanVersion must be a concrete, non-blank version. Callers are expected
     *     to have validated it via {@link #resolveEnkanVersionOrAbort(Transport)}.
     */
    static String pomTemplate(String groupId, String projectName, String basePackage, String enkanVersion) {
        if (!SAFE_COORD_PATTERN.matcher(groupId).matches()) {
            throw new IllegalArgumentException(
                    "groupId '" + groupId + "' contains characters that are not safe to embed in XML.");
        }
        if (!SAFE_ARTIFACT_PATTERN.matcher(projectName).matches()) {
            throw new IllegalArgumentException(
                    "projectName '" + projectName + "' contains characters that are not safe to embed in XML.");
        }
        if (!SAFE_VERSION_PATTERN.matcher(enkanVersion).matches()) {
            throw new IllegalArgumentException(
                    "enkanVersion '" + enkanVersion + "' contains characters that are not safe to embed in XML.");
        }
        String snapshotNote = enkanVersion.endsWith("-SNAPSHOT")
                ? "\n    <!-- NOTE: enkan.version is a SNAPSHOT. Maven Central does not host SNAPSHOTs.\n"
                + "         Run `mvn install` on the enkan source tree first, or override with\n"
                + "         -Denkan.version=<released-version>. -->\n"
                : "";
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n"
                + "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
                + "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n"
                + snapshotNote
                + "    <modelVersion>4.0.0</modelVersion>\n"
                + "    <groupId>" + groupId + "</groupId>\n"
                + "    <artifactId>" + projectName + "</artifactId>\n"
                + "    <version>0.1.0-SNAPSHOT</version>\n"
                + "    <packaging>jar</packaging>\n\n"
                + "    <properties>\n"
                + "        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>\n"
                + "        <maven.compiler.release>25</maven.compiler.release>\n"
                + "        <enkan.version>" + enkanVersion + "</enkan.version>\n"
                + "        <jooq.version>3.21.1</jooq.version>\n"
                + "        <h2.version>2.4.240</h2.version>\n"
                + "        <raoh.version>0.5.0</raoh.version>\n"
                + "        <junit.version>5.11.3</junit.version>\n"
                + "        <assertj.version>3.27.7</assertj.version>\n"
                + "    </properties>\n\n"
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
                + "        <dependency>\n"
                + "            <groupId>net.unit8.enkan</groupId>\n"
                + "            <artifactId>enkan-component-HikariCP</artifactId>\n"
                + "            <version>${enkan.version}</version>\n"
                + "        </dependency>\n"
                + "        <dependency>\n"
                + "            <groupId>net.unit8.enkan</groupId>\n"
                + "            <artifactId>enkan-component-jooq</artifactId>\n"
                + "            <version>${enkan.version}</version>\n"
                + "        </dependency>\n"
                + "        <dependency>\n"
                + "            <groupId>net.unit8.enkan</groupId>\n"
                + "            <artifactId>enkan-component-flyway</artifactId>\n"
                + "            <version>${enkan.version}</version>\n"
                + "        </dependency>\n"
                + "        <dependency>\n"
                + "            <groupId>net.unit8.raoh</groupId>\n"
                + "            <artifactId>raoh</artifactId>\n"
                + "            <version>${raoh.version}</version>\n"
                + "        </dependency>\n"
                + "        <dependency>\n"
                + "            <groupId>net.unit8.raoh</groupId>\n"
                + "            <artifactId>raoh-jooq</artifactId>\n"
                + "            <version>${raoh.version}</version>\n"
                + "        </dependency>\n"
                + "        <dependency>\n"
                + "            <groupId>com.h2database</groupId>\n"
                + "            <artifactId>h2</artifactId>\n"
                + "            <version>${h2.version}</version>\n"
                + "        </dependency>\n"
                + "        <dependency>\n"
                + "            <groupId>org.junit.jupiter</groupId>\n"
                + "            <artifactId>junit-jupiter</artifactId>\n"
                + "            <version>${junit.version}</version>\n"
                + "            <scope>test</scope>\n"
                + "        </dependency>\n"
                + "        <dependency>\n"
                + "            <groupId>org.assertj</groupId>\n"
                + "            <artifactId>assertj-core</artifactId>\n"
                + "            <version>${assertj.version}</version>\n"
                + "            <scope>test</scope>\n"
                + "        </dependency>\n"
                + "    </dependencies>\n\n"
                + "    <build>\n"
                + "        <plugins>\n"
                + "            <plugin>\n"
                + "                <groupId>org.apache.maven.plugins</groupId>\n"
                + "                <artifactId>maven-compiler-plugin</artifactId>\n"
                + "                <version>3.13.0</version>\n"
                + "                <configuration>\n"
                + "                    <release>${maven.compiler.release}</release>\n"
                + "                </configuration>\n"
                + "            </plugin>\n"
                + "            <plugin>\n"
                + "                <groupId>org.apache.maven.plugins</groupId>\n"
                + "                <artifactId>maven-surefire-plugin</artifactId>\n"
                + "                <version>3.5.2</version>\n"
                + "            </plugin>\n"
                + "        </plugins>\n"
                + "    </build>\n\n"
                + "    <profiles>\n"
                + "        <profile>\n"
                + "            <id>dev</id>\n"
                + "            <activation><activeByDefault>true</activeByDefault></activation>\n"
                + "            <build>\n"
                + "                <plugins>\n"
                + "                    <plugin>\n"
                + "                        <groupId>org.codehaus.mojo</groupId>\n"
                + "                        <artifactId>exec-maven-plugin</artifactId>\n"
                + "                        <version>3.5.0</version>\n"
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

    /**
     * Builds the {@code SystemFactory} that wires the default persistence stack
     * (HikariCP + H2 in-memory, Flyway migrations, jOOQ DSLContext) alongside
     * Jackson, Jetty, and the generated ApplicationFactory. Matches the component
     * relationship pattern used by {@code kotowari-example/ExampleSystemFactory}.
     */
    static String systemFactoryTemplate(String basePackage, String systemFactoryClass, String projectName) {
        String appFactoryClass = capitalize(normalizeProjectName(projectName)) + "ApplicationFactory";
        String jdbcUrl = "jdbc:h2:mem:" + normalizeProjectName(projectName) + ";DB_CLOSE_DELAY=-1";
        return """
                package %s;

                import enkan.Env;
                import enkan.component.ApplicationComponent;
                import enkan.component.WebServerComponent;
                import enkan.component.builtin.HmacEncoder;
                import enkan.component.flyway.FlywayMigration;
                import enkan.component.hikaricp.HikariCPComponent;
                import enkan.component.jackson.JacksonBeansConverter;
                import enkan.component.jetty.JettyComponent;
                import enkan.component.jooq.JooqProvider;
                import enkan.config.EnkanSystemFactory;
                import enkan.collection.OptionMap;
                import enkan.system.EnkanSystem;
                import org.jooq.SQLDialect;

                import static enkan.component.ComponentRelationship.component;
                import static enkan.util.BeanBuilder.builder;

                public class %s implements EnkanSystemFactory {
                    @Override
                    public EnkanSystem create() {
                        return EnkanSystem.of(
                                "hmac", new HmacEncoder(),
                                "jackson", new JacksonBeansConverter(),
                                "datasource", new HikariCPComponent(OptionMap.of(
                                        "uri", Env.getString("JDBC_URL", "%s"))),
                                "flyway", new FlywayMigration(),
                                "jooq", builder(new JooqProvider())
                                        .set(JooqProvider::setDialect, SQLDialect.H2)
                                        .build(),
                                "app", new ApplicationComponent<>("%s.%s"),
                                "http", builder(new JettyComponent())
                                        .set(WebServerComponent::setPort, Env.getInt("PORT", 3000))
                                        .build()
                        ).relationships(
                                component("http").using("app"),
                                component("app").using("jackson", "hmac", "jooq", "datasource"),
                                component("jooq").using("datasource", "flyway"),
                                component("flyway").using("datasource")
                        );
                    }
                }
                """.formatted(basePackage, systemFactoryClass, jdbcUrl, basePackage, appFactoryClass);
    }

    private static final int MAX_FIX_ATTEMPTS = 5;

    /**
     * Distinguishes Maven model-building failures (invalid POM) from Java compile
     * failures. The fix prompt differs for each phase so the LLM knows what kind of
     * file is broken.
     */
    enum BuildPhase { VALIDATE, COMPILE }

    /**
     * Structured result of a Maven build step. {@link #errors} is {@code null} on success.
     */
    record BuildResult(BuildPhase phase, String errors) {
        boolean succeeded() { return errors == null; }
    }

    /**
     * Runs {@code mvn validate} then {@code mvn compile -Pdev} on the generated
     * project. When a step fails, sends the errors + relevant file content to the LLM
     * and applies the returned fixes. Repeats until compilation succeeds or the attempt
     * limit ({@link #MAX_FIX_ATTEMPTS}) is reached.
     *
     * <p>The two-phase approach lets the fix prompt distinguish POM problems from
     * Java problems — previously, a broken POM returned Java-shaped errors that the
     * fix loop could not parse file paths from, and the LLM would get no file context.
     *
     * @return true if compilation eventually succeeded, false if the user should abort
     */
    private boolean compileAndFix(Transport transport, Path outPath,
            String appFactoryClass, String systemFactoryClass) {
        for (int attempt = 1; attempt <= MAX_FIX_ATTEMPTS; attempt++) {
            transport.sendOut(section("Building") + DIM + "  mvn validate → compile" + RESET + "\n");
            BuildResult result = runMvnBuild(outPath);
            if (result.succeeded()) {
                transport.sendOut(GREEN + BOLD + "✓ Compilation succeeded." + RESET + "\n");
                return true;
            }
            transport.sendOut(YELLOW + "⚠ " + result.phase() + " errors (attempt "
                    + attempt + "/" + MAX_FIX_ATTEMPTS + "):" + RESET + "\n");
            transport.sendOut(DIM + result.errors() + RESET + "\n");
            if (attempt == MAX_FIX_ATTEMPTS) break;

            transport.sendOut(CYAN + "  Asking AI to fix errors..." + RESET + "\n");
            String fixResponse;
            try {
                fixResponse = requestChatCompletion(
                        buildFixSystemPrompt(),
                        buildFixUserPrompt(result, outPath),
                        "Fixing", transport);
            } catch (Exception e) {
                transport.sendErr("Failed to get fix from AI: " + safeMessage(e));
                break;
            }
            try {
                writeGeneratedFiles(outPath, fixResponse, appFactoryClass, systemFactoryClass, transport);
            } catch (IOException e) {
                transport.sendErr("Failed to write fixes: " + safeMessage(e));
                break;
            }
        }
        transport.sendErr("Compilation failed. Fix the errors manually and run /connect once the server is up.");
        return false;
    }

    /**
     * Runs {@code mvn validate} (POM sanity) then {@code mvn compile -Pdev} (Java
     * sources) in the given directory. Returns the first failing phase.
     *
     * @return {@link BuildResult#succeeded()} true if both phases pass; otherwise the
     *     result describes which phase failed and the trimmed error lines.
     */
    static BuildResult runMvnBuild(Path outPath) {
        BuildResult validate = runMvnPhase(outPath, BuildPhase.VALIDATE, "validate");
        if (!validate.succeeded()) return validate;
        return runMvnPhase(outPath, BuildPhase.COMPILE, "compile", "-Pdev");
    }

    /** Maximum time to wait for a single Maven phase before killing the process. */
    private static final int MVN_PHASE_TIMEOUT_MINUTES = 10;

    /** Package-private for testing the fallback tail path without running a real Maven build. */
    static BuildResult runMvnPhase(Path outPath, BuildPhase phase, String... mvnArgs) {
        List<String> cmd = new ArrayList<>();
        cmd.add("mvn");
        cmd.add("--no-transfer-progress");
        cmd.add("-B");
        for (String a : mvnArgs) cmd.add(a);
        Process proc = null;
        try {
            proc = new ProcessBuilder(cmd)
                    .directory(outPath.toFile())
                    .redirectErrorStream(true)
                    .start();
            String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean finished = proc.waitFor(MVN_PHASE_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (!finished) {
                return new BuildResult(phase, "mvn " + String.join(" ", mvnArgs)
                        + " timed out after " + MVN_PHASE_TIMEOUT_MINUTES + " minutes.");
            }
            int exitCode = proc.exitValue();
            if (exitCode == 0) return new BuildResult(phase, null);
            // Collect once so the fallback tail path reuses the same list.
            List<String> allLines = output.lines().toList();
            String errors = allLines.stream()
                    .filter(l -> l.startsWith("[ERROR]") || l.startsWith("[FATAL]"))
                    .collect(java.util.stream.Collectors.joining("\n"));
            if (errors.isBlank()) {
                // Fallback: no marker lines at all (rare); include the tail of raw output.
                errors = allLines.stream()
                        .skip(Math.max(0, allLines.size() - 30))
                        .collect(java.util.stream.Collectors.joining("\n"));
            }
            return new BuildResult(phase, errors);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new BuildResult(phase, "Failed to run mvn " + String.join(" ", mvnArgs) + ": " + safeMessage(e));
        } catch (IOException e) {
            return new BuildResult(phase, "Failed to run mvn " + String.join(" ", mvnArgs) + ": " + safeMessage(e));
        } finally {
            if (proc != null) proc.destroyForcibly();
        }
    }

    private static String buildFixSystemPrompt() {
        return """
                You are an expert Enkan framework developer.
                Fix the Maven errors shown by the user by outputting corrected files.

                HARD CONSTRAINTS:
                - Output ONLY the files you are changing.
                - Use EXACTLY this format per file:

                  ### relative/path/to/File.java
                  ```java
                  // corrected file content
                  ```

                - The following files are FIXED TEMPLATES and will be overwritten if you
                  emit them. NEVER output them, even if the error message mentions them:
                    pom.xml
                    *SystemFactory.java
                    *ApplicationFactory.java
                    DevMain.java
                    jaxrs/JsonBodyReader.java
                    jaxrs/JsonBodyWriter.java
                  If an error really is inside one of those files, fix it indirectly by
                  changing the user-generated class that collides with it (rename it,
                  move it, drop the unused import, etc.).
                - Do NOT include explanations outside file blocks.
                """;
    }

    static String buildFixUserPrompt(BuildResult result, Path outPath) {
        var filesToInclude = result.errors().lines()
                .map(line -> extractFileFromError(line, outPath))
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        // If the error hints at the POM (model-building errors rarely expose a line
        // reference the regex can parse) or no files were matched, always include
        // pom.xml so the LLM has *some* context to work from.
        if (result.phase() == BuildPhase.VALIDATE || filesToInclude.isEmpty()) {
            Path pom = outPath.resolve("pom.xml");
            if (Files.exists(pom)) filesToInclude.add(pom);
        }

        var sb = new StringBuilder("""
                Maven %s failed in %s:

                %s

                """.formatted(result.phase(), outPath, result.errors()));

        for (Path file : filesToInclude) {
            try {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                String rel = outPath.relativize(file).toString().replace('\\', '/');
                String lang = rel.endsWith(".xml") ? "xml"
                        : rel.endsWith(".sql") ? "sql"
                        : "java";
                sb.append("### ").append(rel).append("\n```").append(lang).append("\n")
                  .append(content).append("\n```\n\n");
            } catch (IOException ignored) { }
        }
        return sb.toString();
    }

    /**
     * Extracts a file path from a Maven error line, supporting both Java compiler
     * format and Maven model-building format.
     *
     * <ul>
     *   <li>{@code [ERROR] /abs/File.java:[row,col] message} — javac error.</li>
     *   <li>{@code [ERROR] ... @ /abs/pom.xml, line 6, column 13} — Maven model error.</li>
     *   <li>{@code [FATAL] Non-resolvable parent POM ... @ line 6, column 13} — may
     *       not contain an absolute path; caller falls back to {@code pom.xml}.</li>
     * </ul>
     *
     * Returns a {@link Path} contained within {@code outPath}, or {@code null} if no
     * path could be extracted or the path is outside the project root.
     */
    static Path extractFileFromError(String errorLine, Path outPath) {
        if (!errorLine.startsWith("[ERROR]") && !errorLine.startsWith("[FATAL]")) return null;
        String body = errorLine.substring(errorLine.indexOf(']') + 1).trim();

        // Try javac pattern: "/abs/path/File.java:[row,col] ..."
        int bracket = body.indexOf(":[");
        if (bracket >= 0) {
            Path p = safeInProjectPath(body.substring(0, bracket).trim(), outPath);
            if (p != null) return p;
        }

        // Try Maven model pattern: "... @ /abs/path/pom.xml, line N, column N"
        int at = body.lastIndexOf(" @ ");
        if (at >= 0) {
            String rest = body.substring(at + 3).trim();
            // Strip trailing ", line ..., column ..."
            int comma = rest.indexOf(',');
            String cand = (comma >= 0 ? rest.substring(0, comma) : rest).trim();
            Path p = safeInProjectPath(cand, outPath);
            if (p != null) return p;
        }
        return null;
    }

    private static Path safeInProjectPath(String candidate, Path outPath) {
        if (candidate == null || candidate.isBlank()) return null;
        try {
            Path p = Path.of(candidate).toAbsolutePath().normalize();
            Path normalizedOut = outPath.toAbsolutePath().normalize();
            if (Files.exists(p) && p.startsWith(normalizedOut)) return p;
        } catch (Exception ignored) { }
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

    /**
     * Resolves the chat-completion endpoint URI from {@link #apiUrl}. If the configured
     * URL already ends with {@code /chat/completions} it is used verbatim; otherwise
     * {@code /chat/completions} is appended. A trailing slash on the base URL is tolerated.
     */
    URI resolveChatCompletionUri() {
        String base = apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;
        if (base.endsWith("/chat/completions")) {
            return URI.create(base);
        }
        return URI.create(base + "/chat/completions");
    }

    String requestChatCompletion(String systemPrompt, String userPrompt, String spinnerLabel, Transport transport)
            throws IOException, InterruptedException {
        if (transport != null) transport.startSpinner(spinnerLabel);
        try {
            String requestBody = buildRequestBody(model, systemPrompt, userPrompt, true);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(resolveChatCompletionUri())
                    .timeout(Duration.ofMinutes(10))
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<java.io.InputStream> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                String body;
                try (var bodyStream = response.body()) {
                    body = new String(bodyStream.readAllBytes(), StandardCharsets.UTF_8);
                }
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
    int writeGeneratedFiles(Path outPath, String response,
            String appFactoryClass, String systemFactoryClass, Transport transport)
            throws IOException {
        Path normalizedOut = outPath.normalize();
        int count = 0;
        String[] lines = response.split("\\R", -1);
        String currentFile = null;
        var codeLines = new StringBuilder();
        boolean inBlock = false;
        // When a header names a fixed-template file we still need to consume its fenced
        // block, otherwise code lines inside it could be misread as new headers.
        boolean skipBlock = false;

        for (String line : lines) {
            if (!inBlock) {
                String path = extractFilePath(line);
                if (path != null) {
                    if (isFixedTemplateFile(path, appFactoryClass, systemFactoryClass)) {
                        currentFile = null;
                        skipBlock = true;
                    } else {
                        currentFile = path;
                        skipBlock = false;
                        transport.sendOut(GREEN + "  ✓" + RESET + " " + currentFile + "\n");
                    }
                } else if ((currentFile != null || skipBlock) && line.startsWith("```")) {
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
                    skipBlock = false;
                } else if (!skipBlock) {
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

    /**
     * Returns true if {@code path} names one of the deterministic template files that
     * the generator writes and the LLM must never overwrite. The exact
     * {@code appFactoryClass} and {@code systemFactoryClass} names are used so that a
     * legitimately generated file like {@code CustomerApplicationFactory.java} is not
     * accidentally suppressed.
     */
    static boolean isFixedTemplateFile(String path, String appFactoryClass, String systemFactoryClass) {
        String normalized = path.replace('\\', '/');
        String filename = normalized.substring(normalized.lastIndexOf('/') + 1);
        return filename.equals("pom.xml")
                || filename.equals("DevMain.java")
                || filename.equals("Main.java")
                || filename.equals("JsonBodyReader.java")
                || filename.equals("JsonBodyWriter.java")
                || filename.equals(systemFactoryClass + ".java")
                || filename.equals(appFactoryClass + ".java");
    }

    /**
     * Overload for callers that do not have access to the project-specific factory names
     * (e.g. {@code parseManifest} advisory checks). Falls back to the broad suffix match,
     * which is acceptable in non-write-path contexts.
     */
    static boolean isFixedTemplateFile(String path) {
        String normalized = path.replace('\\', '/');
        String filename = normalized.substring(normalized.lastIndexOf('/') + 1);
        return filename.equals("pom.xml")
                || filename.equals("DevMain.java")
                || filename.equals("Main.java")
                || filename.equals("JsonBodyReader.java")
                || filename.equals("JsonBodyWriter.java")
                || filename.endsWith("SystemFactory.java")
                || filename.endsWith("ApplicationFactory.java");
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
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        // Escape remaining C0 control characters as Unicode escape (e.g. \u0008)
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    /**
     * Builds the generation prompt split into {@code [systemPrompt, userMessage]}.
     *
     * <p>Keeping reference code and hard constraints in the system prompt (rather than
     * the user message) reduces the thinking budget the LLM spends on the user turn
     * and keeps the constraints in-context even when the user message grows during
     * fix-loop iterations.
     */
    String[] buildPrompt(String description, String projectName,
            String groupId, Path outPath) {
        String basePackage = groupId + "." + normalizeProjectName(projectName);
        boolean userWantsJpa = hasUserRequestedAlternative(description, "jpa", "hibernate", "jakarta.persistence");
        boolean userWantsDoma = hasUserRequestedAlternative(description, "doma2", "doma ");
        boolean defaultStack = !userWantsJpa && !userWantsDoma;

        // --- System prompt: framework knowledge + hard constraints + output format ---
        var sys = new StringBuilder();
        sys.append("""
                You are an expert Enkan framework developer.
                Enkan is a middleware-chain web framework for Java 25.

                == HARD CONSTRAINTS ==
                1. You may only import classes from dependencies declared in the fixed pom.xml
                   shown below. No Spring, no JPA/Hibernate, no Doma2, no Lombok, no Guava,
                   no libraries that are not in the POM.
                2. The reference files at the bottom of this prompt are for ARCHITECTURAL
                   UNDERSTANDING ONLY. NEVER import from the `kotowari.example.*` package —
                   copy the pattern, not the identifiers.
                3. The following files are ALREADY GENERATED by the template engine and will
                   be overwritten if you emit them. Do NOT include them in your output:
                     - pom.xml
                     - src/dev/java/{basePath}/DevMain.java
                     - src/main/java/{basePath}/{Name}SystemFactory.java
                     - src/main/java/{basePath}/{Name}ApplicationFactory.java
                     - src/main/java/{basePath}/jaxrs/JsonBodyReader.java
                     - src/main/java/{basePath}/jaxrs/JsonBodyWriter.java
                4. Every class name referenced from the fixed files must exist in your output.
                   In particular, the fixed ApplicationFactory calls `RoutesDef.routes()` so
                   you MUST generate `src/main/java/{basePath}/RoutesDef.java` with
                   `public static kotowari.routing.Routes routes()`.
                5. Flyway expects at least one migration file at
                   `src/main/resources/db/migration/V1__<snake_name>.sql`. Use H2-compatible
                   DDL (INT/VARCHAR/BOOLEAN, `IDENTITY` or `GENERATED BY DEFAULT AS IDENTITY`).

                == DEFAULT STACK ==
                Persistence: HikariCP + jOOQ + Flyway + H2 in-memory + Raoh for row decoding.
                Controllers obtain a `DSLContext` via
                  `DSLContext dsl = request.getExtension("jooqDslContext");`
                NEVER instantiate your own `DSLContext`, `DataSource`, or `Flyway` — the
                SystemFactory wires them.

                """);

        if (defaultStack) {
            sys.append("""
                    == CANONICAL PATTERNS (authoritative — copy these verbatim) ==
                    The files below are the single source of truth for API shapes in
                    the default jOOQ+Raoh stack. Training-data memory of Doma2, JPA,
                    Spring Data, Lombok, etc. is WRONG for this stack. When in doubt,
                    copy from these files rather than from anything else you remember.

                    """);
            loadInitReference().forEach((path, content) -> {
                sys.append("### ").append(path).append("\n");
                sys.append(content).append("\n\n");
            });
        }

        sys.append("""
                == LEGACY REFERENCE FILES (architectural context only) ==
                These are fetched from the kotowari-example project. They use Doma2,
                NOT jOOQ. Read them to understand middleware ordering and component
                wiring — but NEVER import from `kotowari.example.*` and NEVER copy
                Doma2 code.

                """);
        getOrFetchReferenceCache().forEach((filename, content) -> {
            sys.append("#### ").append(filename).append(" (uses Doma2 — substitute jOOQ)\n");
            sys.append("```\n").append(content).append("\n```\n\n");
        });

        sys.append("""
                == OUTPUT FORMAT ==
                Emit EVERY file using EXACTLY this format — no exceptions:

                ### relative/path/to/File.java
                ```java
                // file content here
                ```

                After all file blocks, emit a manifest:

                ### MANIFEST
                ```
                src/main/java/.../RoutesDef.java
                src/main/java/.../controller/FooController.java
                src/main/resources/db/migration/V1__create_foo.sql
                ```

                Do NOT include any prose outside the file blocks.
                """);

        // --- User message: project-specific requirements ---
        var user = new StringBuilder();
        user.append("Generate an Enkan project with these settings:\n\n");
        user.append("- Project name: ").append(projectName).append("\n");
        user.append("- Group ID: ").append(groupId).append("\n");
        user.append("- Artifact ID: ").append(projectName).append("\n");
        user.append("- Base package: ").append(basePackage).append("\n");
        user.append("- Base path (for file layout): src/main/java/").append(basePackage.replace('.', '/')).append("\n\n");
        user.append("Requirements: ").append(description).append("\n\n");

        user.append("""
                Generate ONLY these files (the rest are fixed templates):

                1. RoutesDef.java in the base package
                   - `public final class RoutesDef { private RoutesDef() {} public static kotowari.routing.Routes routes() { ... } }`
                   - Use `Routes.define(r -> { r.get("/path").to(FooController.class, "method"); ... }).compile();`
                2. One Flyway SQL file at `src/main/resources/db/migration/V1__<snake>.sql`
                   (H2-compatible DDL).
                3. Domain records (plain Java records) with optional Raoh decoders.
                4. Controller(s) with methods that accept an `enkan.web.data.HttpRequest`
                   and return `enkan.web.data.HttpResponse` or a POJO (the SerDes middleware
                   serializes POJOs as JSON when the client accepts `application/json`).
                   Obtain `DSLContext` via `request.getExtension("jooqDslContext")`.

                Database: H2 in-memory (jdbc:h2:mem:<projectName>), already configured.
                """);

        if (userWantsJpa) {
            user.append("\nNOTE: You asked for JPA/Hibernate. The default POM does not include ")
                .append("enkan-component-jpa -- add it manually after generation. The generator ")
                .append("still treats jakarta.persistence.* as allowed for this run.\n");
        }
        if (userWantsDoma) {
            user.append("\nNOTE: You asked for Doma2. The default POM does not include ")
                .append("enkan-component-doma2 -- add it manually after generation.\n");
        }

        return new String[]{sys.toString(), user.toString()};
    }

    /**
     * Returns true if the user's free-text description mentions any of the given
     * alternative-stack markers (case-insensitive). Used to open the allowlist just
     * enough to let, e.g., JPA-requested projects import {@code jakarta.persistence.*}.
     */
    private static boolean hasUserRequestedAlternative(String description, String... markers) {
        if (description == null) return false;
        String lower = description.toLowerCase(Locale.ROOT);
        for (String m : markers) {
            if (lower.contains(m)) return true;
        }
        return false;
    }

    private Map<String, String> getOrFetchReferenceCache() {
        if (referenceCache == null) {
            referenceCache = fetchAllReferences();
        }
        return referenceCache;
    }

    /**
     * Resolves the Enkan version to stamp into the generated {@code pom.xml}.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>{@code -Denkan.version=x.y.z} system property (allows dev/IDE overrides).</li>
     *   <li>{@code ENKAN_VERSION} environment variable.</li>
     *   <li>The Maven {@code pom.properties} resource at
     *       {@code META-INF/maven/net.unit8.enkan/enkan-repl-client/pom.properties}
     *       — present in any packaged jar.</li>
     *   <li>{@code "UNKNOWN"} as a last-resort sentinel when running from raw class files
     *       (e.g. unit tests). Callers must treat this as an error and abort.</li>
     * </ol>
     *
     * <p>The {@code Implementation-Version} manifest attribute is intentionally not
     * consulted because the enkan build does not set it; relying on {@code pom.properties}
     * is both more reliable and more portable across build plugins.
     */
    String enkanVersion() {
        String override = System.getProperty("enkan.version");
        if (override != null && !override.isBlank()) return override.trim();
        String env = System.getenv("ENKAN_VERSION");
        if (env != null && !env.isBlank()) return env.trim();
        try (var in = InitCommand.class.getResourceAsStream(
                "/META-INF/maven/net.unit8.enkan/enkan-repl-client/pom.properties")) {
            if (in != null) {
                var props = new java.util.Properties();
                props.load(in);
                String v = props.getProperty("version");
                if (v != null && !v.isBlank()) return v.trim();
            }
        } catch (IOException e) {
            LOG.warn("Failed to read pom.properties for enkan-repl-client", e);
        }
        return "UNKNOWN";
    }

    /**
     * Pattern that matches safe Maven version strings. Allows digits, letters, dots,
     * and hyphens only — rejecting any value that could break an XML comment or element
     * (e.g. {@code -->}, angle brackets, ampersands).
     */
    private static final Pattern SAFE_VERSION_PATTERN = Pattern.compile("[\\w.\\-]+");

    /**
     * Pattern that matches valid Maven {@code groupId} values (multi-segment, dot-separated).
     * Rejects characters that could break an XML element ({@code < > & " '}).
     */
    private static final Pattern SAFE_COORD_PATTERN = Pattern.compile("[a-zA-Z0-9_.\\-]+(\\.[a-zA-Z0-9_.\\-]+)*");

    /**
     * Pattern that matches valid Maven {@code artifactId} values. Unlike {@link #SAFE_COORD_PATTERN},
     * dots are not allowed — Maven convention for artifactId is alphanumerics, hyphens, and underscores only.
     */
    private static final Pattern SAFE_ARTIFACT_PATTERN = Pattern.compile("[a-zA-Z0-9_\\-]+");

    /**
     * Resolves the Enkan version and, when unavailable, prints a user-actionable error
     * to the transport and returns {@code null}. Callers should abort on null.
     *
     * <p>A SNAPSHOT version is not an abort — the REPL may be running from a locally
     * built enkan-repl-client against a locally installed snapshot of every enkan
     * component — but the user gets a clear warning so they can diagnose "unresolved
     * dependency" errors later. A user who just downloaded a release build of
     * {@code enkan-repl} never sees the warning.
     */
    String resolveEnkanVersionOrAbort(Transport transport) {
        String v = enkanVersion();
        if ("UNKNOWN".equals(v)) {
            transport.sendErr("Cannot determine Enkan version for the generated pom.xml.");
            transport.sendErr("  Run from a packaged enkan-repl-client jar, or pass -Denkan.version=<x.y.z>");
            transport.sendErr("  (or set the ENKAN_VERSION environment variable).");
            return null;
        }
        if (!SAFE_VERSION_PATTERN.matcher(v).matches()) {
            transport.sendErr("Enkan version '" + v + "' contains characters that are not safe to embed in XML.");
            transport.sendErr("  Override with -Denkan.version=<x.y.z> using only alphanumerics, dots, and hyphens.");
            return null;
        }
        if (v.endsWith("-SNAPSHOT") && System.getProperty("enkan.version") == null
                && System.getenv("ENKAN_VERSION") == null) {
            transport.sendOut(YELLOW + "⚠ Using SNAPSHOT version " + v + "." + RESET + "\n");
            transport.sendOut(DIM + "  Maven Central does not host SNAPSHOT artifacts. The generated project\n"
                    + "  will only build if you have run `mvn install` on the full enkan tree,\n"
                    + "  or if you override with -Denkan.version=<released-version>.\n" + RESET);
        }
        return v;
    }

    /**
     * Verifies that the {@code mvn} executable is reachable on {@code PATH}. The
     * generator relies on Maven both to validate the generated POM and to compile
     * the project in the fix loop; there is no point continuing without it.
     *
     * <p>Prints an actionable error and returns {@code false} when Maven is missing,
     * so the caller can abort {@code /init} before asking the user to fill in
     * prompts for a run that cannot finish.
     *
     * @return {@code true} when {@code mvn -v} exits successfully, {@code false} otherwise
     */
    boolean verifyMavenAvailable(Transport transport) {
        Process proc = null;
        try {
            proc = new ProcessBuilder("mvn", "-v")
                    .redirectErrorStream(true)
                    .start();
            // Drain output to avoid the child blocking on a full pipe.
            proc.getInputStream().readAllBytes();
            // Cap at 10 s: a slow Maven wrapper download should not block /init forever.
            boolean finished = proc.waitFor(10, TimeUnit.SECONDS);
            if (!finished) {
                LOG.warn("mvn -v timed out after 10 s");
            } else if (proc.exitValue() == 0) {
                return true;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Failed to locate mvn executable", e);
        } catch (IOException e) {
            LOG.warn("Failed to locate mvn executable", e);
        } finally {
            if (proc != null) proc.destroyForcibly();
        }
        transport.sendErr("Maven (mvn) is required but was not found on PATH.");
        transport.sendErr("  Install it from https://maven.apache.org/download.cgi");
        transport.sendErr("  or via Homebrew (`brew install maven`), SDKMAN (`sdk install maven`),");
        transport.sendErr("  or your system package manager (`apt install maven`, etc.).");
        return false;
    }

    /**
     * Fixed ApplicationFactory template: minimal middleware stack suitable for a JSON
     * REST API using jOOQ + Jackson. The LLM is responsible only for
     * {@code RoutesDef} and the controller implementations — the middleware ordering
     * itself is part of the template because the ordering is load-bearing for Enkan
     * and LLMs frequently get it subtly wrong.
     *
     * <p>Routes are delegated to a generated {@code RoutesDef.routes()} static method,
     * which the LLM writes alongside the controllers.
     */
    static String applicationFactoryTemplate(String basePackage, String appFactoryClass) {
        return """
                package %1$s;

                import %1$s.jaxrs.JsonBodyReader;
                import %1$s.jaxrs.JsonBodyWriter;
                import enkan.Application;
                import enkan.config.ApplicationFactory;
                import enkan.middleware.jooq.JooqDslContextMiddleware;
                import enkan.middleware.jooq.JooqTransactionMiddleware;
                import enkan.system.inject.ComponentInjector;
                import enkan.web.application.WebApplication;
                import enkan.web.data.HttpRequest;
                import enkan.web.data.HttpResponse;
                import enkan.web.middleware.ContentNegotiationMiddleware;
                import enkan.web.middleware.ContentTypeMiddleware;
                import enkan.web.middleware.CookiesMiddleware;
                import enkan.web.middleware.DefaultCharsetMiddleware;
                import enkan.web.middleware.NestedParamsMiddleware;
                import enkan.web.middleware.ParamsMiddleware;
                import enkan.web.middleware.TraceMiddleware;
                import jakarta.ws.rs.ext.MessageBodyWriter;
                import kotowari.middleware.ControllerInvokerMiddleware;
                import kotowari.middleware.FormMiddleware;
                import kotowari.middleware.RoutingMiddleware;
                import kotowari.middleware.SerDesMiddleware;
                import kotowari.middleware.ValidateBodyMiddleware;
                import kotowari.middleware.serdes.ToStringBodyWriter;
                import kotowari.routing.Routes;
                import tools.jackson.databind.ObjectMapper;
                import tools.jackson.databind.json.JsonMapper;

                import static enkan.util.BeanBuilder.builder;

                public class %2$s implements ApplicationFactory<HttpRequest, HttpResponse> {
                    @Override
                    public Application<HttpRequest, HttpResponse> create(ComponentInjector injector) {
                        WebApplication app = new WebApplication();
                        ObjectMapper mapper = JsonMapper.builder().build();

                        // Routes are defined in the generated RoutesDef class, which the LLM
                        // writes alongside the controllers. The static routes() method returns
                        // a compiled Routes instance.
                        Routes routes = RoutesDef.routes();

                        app.use(new DefaultCharsetMiddleware());
                        app.use(new TraceMiddleware<>());
                        app.use(new ContentTypeMiddleware());
                        app.use(new ParamsMiddleware());
                        app.use(new NestedParamsMiddleware());
                        app.use(new CookiesMiddleware());
                        app.use(new ContentNegotiationMiddleware());
                        app.use(new RoutingMiddleware(routes));
                        app.use(new JooqDslContextMiddleware<>());
                        app.use(new JooqTransactionMiddleware<>());
                        app.use(new FormMiddleware());
                        app.use(builder(new SerDesMiddleware<>())
                                .set(SerDesMiddleware::setBodyWriters,
                                        new MessageBodyWriter[]{
                                                new ToStringBodyWriter(),
                                                new JsonBodyWriter<>(mapper)})
                                .set(SerDesMiddleware::setBodyReaders,
                                        new JsonBodyReader<>(mapper))
                                .build());
                        app.use(new ValidateBodyMiddleware<>());
                        app.use(new ControllerInvokerMiddleware<>(injector));

                        return app;
                    }
                }
                """.formatted(basePackage, appFactoryClass);
    }

    /**
     * Fixed {@code JsonBodyReader} template. Copied verbatim from
     * {@code kotowari-example/jaxrs/JsonBodyReader.java} because {@code kotowari} itself
     * does not ship a JSON body reader — only {@code ToStringBodyWriter} and
     * {@code UrlFormEncodedBodyWriter}.
     */
    static String jsonBodyReaderTemplate(String basePackage) {
        return """
                package %s.jaxrs;

                import jakarta.ws.rs.WebApplicationException;
                import jakarta.ws.rs.core.MediaType;
                import jakarta.ws.rs.core.MultivaluedMap;
                import jakarta.ws.rs.ext.MessageBodyReader;
                import tools.jackson.databind.ObjectMapper;

                import java.io.IOException;
                import java.io.InputStream;
                import java.lang.annotation.Annotation;
                import java.lang.reflect.Type;
                import java.util.Objects;

                public class JsonBodyReader<T> implements MessageBodyReader<T> {
                    private final ObjectMapper mapper;

                    public JsonBodyReader(ObjectMapper mapper) {
                        this.mapper = mapper;
                    }

                    @Override
                    public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
                        return Objects.equals(mediaType.getSubtype(), "json");
                    }

                    @Override
                    public T readFrom(Class<T> type, Type genericType, Annotation[] annotations, MediaType mediaType,
                            MultivaluedMap<String, String> httpHeaders, InputStream entityStream)
                            throws IOException, WebApplicationException {
                        return mapper.readerFor(mapper.getTypeFactory().constructType(genericType))
                                .readValue(entityStream);
                    }
                }
                """.formatted(basePackage);
    }

    /**
     * Fixed {@code JsonBodyWriter} template — see {@link #jsonBodyReaderTemplate} for the
     * rationale. The pair is needed for the SerDes middleware to negotiate {@code application/json}.
     */
    static String jsonBodyWriterTemplate(String basePackage) {
        return """
                package %s.jaxrs;

                import jakarta.ws.rs.WebApplicationException;
                import jakarta.ws.rs.core.MediaType;
                import jakarta.ws.rs.core.MultivaluedMap;
                import jakarta.ws.rs.ext.MessageBodyWriter;
                import tools.jackson.databind.ObjectMapper;

                import java.io.IOException;
                import java.io.OutputStream;
                import java.lang.annotation.Annotation;
                import java.lang.reflect.Type;
                import java.util.Objects;

                public class JsonBodyWriter<T> implements MessageBodyWriter<T> {
                    private final ObjectMapper mapper;

                    public JsonBodyWriter(ObjectMapper mapper) {
                        this.mapper = mapper;
                    }

                    @Override
                    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
                        return Objects.equals(mediaType.getSubtype(), "json");
                    }

                    @Override
                    public void writeTo(T o, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType,
                            MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream)
                            throws IOException, WebApplicationException {
                        mapper.writerFor(mapper.getTypeFactory().constructType(genericType))
                                .writeValue(entityStream, o);
                    }
                }
                """.formatted(basePackage);
    }

    /**
     * Fetches all reference URLs in parallel and returns them as a filename → content map.
     * Results preserve the declaration order of {@link #REFERENCE_URLS}. Any future that
     * completes exceptionally is logged and skipped (so a single failure does not abort
     * the whole init run).
     */
    Map<String, String> fetchAllReferences() {
        List<CompletableFuture<Map.Entry<String, String>>> futures = new ArrayList<>();
        for (String url : REFERENCE_URLS) {
            futures.add(fetchReferenceAsync(url));
        }
        Map<String, String> cache = new LinkedHashMap<>();
        for (CompletableFuture<Map.Entry<String, String>> f : futures) {
            try {
                Map.Entry<String, String> entry = f.get(15, TimeUnit.SECONDS);
                if (entry != null) {
                    cache.put(entry.getKey(), entry.getValue());
                }
            } catch (java.util.concurrent.TimeoutException e) {
                LOG.warn("Reference fetch timed out after 15 s");
                f.cancel(true);
            } catch (java.util.concurrent.ExecutionException | CancellationException e) {
                LOG.warn("Reference fetch future failed unexpectedly", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return cache;
    }

    /**
     * Loads the canonical init-reference markdown files from the classpath. Unlike
     * {@link #fetchAllReferences()} (which talks to GitHub over HTTP), this is
     * synchronous, offline, and guaranteed to succeed as long as the
     * enkan-repl-client jar is intact.
     *
     * <p>Keys in the returned map are the resource paths (e.g.
     * {@code "init-reference/patterns/routes.md"}) so the prompt section headers
     * make it obvious where each snippet came from. Preserves the declaration order
     * of {@link #INIT_REFERENCE_RESOURCES}.
     *
     * <p>Any resource that cannot be read (shouldn't happen in a packaged jar but
     * might during tests running from raw class directories if a file is missing)
     * is skipped with a warning rather than aborting {@code /init} — the generator
     * degrades gracefully, trading some guidance quality for robustness.
     */
    Map<String, String> loadInitReference() {
        if (initReferenceCache != null) {
            return initReferenceCache;
        }
        Map<String, String> loaded = new LinkedHashMap<>();
        for (String resource : INIT_REFERENCE_RESOURCES) {
            try (var in = InitCommand.class.getResourceAsStream(resource)) {
                if (in == null) {
                    LOG.warn("Init reference resource not found on classpath: {}", resource);
                    continue;
                }
                String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                // Strip the leading slash so the section header reads more naturally.
                String key = resource.startsWith("/") ? resource.substring(1) : resource;
                loaded.put(key, content);
            } catch (IOException e) {
                LOG.warn("Failed to load init reference resource: {}", resource, e);
            }
        }
        if (loaded.isEmpty()) {
            LOG.warn("No init-reference resources could be loaded from the classpath. "
                    + "Generator will proceed with degraded guidance quality.");
        }
        initReferenceCache = loaded;
        return initReferenceCache;
    }

    private CompletableFuture<Map.Entry<String, String>> fetchReferenceAsync(String url) {
        String filename = url.substring(url.lastIndexOf('/') + 1);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
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

    private static String safeMessage(Throwable t) {
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
        String lower = description.toLowerCase(Locale.ROOT);
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
