package enkan.system.devel.compiler;

import enkan.Env;
import enkan.exception.MisconfigurationException;
import enkan.system.ReplResponse;
import enkan.system.Transport;
import enkan.system.devel.CompileResult;
import enkan.system.devel.Compiler;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * A compiler implementation that delegates to {@code mvn compile} via
 * {@link ProcessBuilder}.
 *
 * <p>When {@code MAVEN_HOME} or {@code M2_HOME} is set, Maven is resolved
 * from that directory. Otherwise, {@code mvn} is resolved from {@code PATH}.</p>
 *
 * @author kawasima
 */
public class MavenCompiler implements Compiler {
    private static final boolean IS_WINDOWS =
            System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("win");

    private static final long COMPILE_TIMEOUT_MINUTES = 10L;

    private String projectDirectory = ".";

    @Override
    public CompileResult execute(Transport t) {
        String mavenHomeEnv = Env.getString("MAVEN_HOME", Env.getString("M2_HOME", null));
        if (mavenHomeEnv != null && mavenHomeEnv.isBlank()) {
            mavenHomeEnv = null;
        }

        String mvnCommand;
        if (mavenHomeEnv != null) {
            File mavenHome = new File(mavenHomeEnv);
            String mvnBin = IS_WINDOWS ? "bin/mvn.cmd" : "bin/mvn";
            File mvnExec = new File(mavenHome, mvnBin);
            if (!mvnExec.canExecute()) {
                throw new MisconfigurationException("devel.MAVEN_HOME_NOT_SET");
            }
            mvnCommand = mvnExec.getAbsolutePath();
        } else {
            mvnCommand = IS_WINDOWS ? "mvn.cmd" : "mvn";
        }

        File projectDir = new File(projectDirectory);
        File pomFile = new File(projectDir, "pom.xml");
        if (!pomFile.exists()) {
            return CompileResult.failure(new IllegalStateException(
                    "pom.xml not found in project directory: " + projectDirectory));
        }

        ProcessBuilder pb = new ProcessBuilder(mvnCommand, "compile");
        pb.directory(projectDir);
        pb.redirectErrorStream(false);

        try {
            Process process = pb.start();

            // Stream stdout and stderr to Transport in parallel
            Thread stdoutReader = Thread.ofVirtual().start(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        t.send(ReplResponse.withOut(line));
                    }
                } catch (IOException e) {
                    t.send(ReplResponse.withErr("I/O error reading process stdout: " + e.getMessage()));
                }
            });

            Thread stderrReader = Thread.ofVirtual().start(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        t.send(ReplResponse.withErr(line));
                    }
                } catch (IOException e) {
                    t.send(ReplResponse.withErr("I/O error reading process stderr: " + e.getMessage()));
                }
            });

            try {
                boolean finished = process.waitFor(COMPILE_TIMEOUT_MINUTES, TimeUnit.MINUTES);
                if (!finished) {
                    return CompileResult.failure(new IllegalStateException(
                            "Maven compile timed out after " + COMPILE_TIMEOUT_MINUTES + " minutes"));
                }
                int exitCode = process.exitValue();
                if (exitCode != 0) {
                    return CompileResult.failure(new IllegalStateException(
                            "Maven compile failed with exit code " + exitCode));
                }
            } finally {
                // no-op if process already exited; ensures cleanup on error/timeout paths
                process.destroyForcibly();
                try {
                    stdoutReader.join(5_000);
                    stderrReader.join(5_000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        } catch (IOException e) {
            return CompileResult.failure(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CompileResult.failure(e);
        }
        return CompileResult.success();
    }

    public void setProjectDirectory(String projectDirectory) {
        this.projectDirectory = projectDirectory;
    }
}
