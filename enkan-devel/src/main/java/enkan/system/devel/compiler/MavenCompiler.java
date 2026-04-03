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

/**
 * A compiler implementation that delegates to {@code mvn compile} via
 * {@link ProcessBuilder}.
 *
 * <p>Requires either {@code MAVEN_HOME} or {@code M2_HOME} environment
 * variable to be set, or Maven installed at {@code /opt/maven}.</p>
 *
 * @author kawasima
 */
public class MavenCompiler implements Compiler {
    private String projectDirectory = ".";

    @Override
    public CompileResult execute(Transport t) {
        File mavenHome = new File(Env.getString("MAVEN_HOME",
                Env.getString("M2_HOME", "/opt/maven")));
        if (!mavenHome.exists()) {
            throw new MisconfigurationException("devel.MAVEN_HOME_NOT_SET");
        }

        String mvnCommand = new File(mavenHome, "bin/mvn").getAbsolutePath();
        ProcessBuilder pb = new ProcessBuilder(mvnCommand, "compile");
        pb.directory(new File(projectDirectory));
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
                    // Process ended — stop reading
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
                    // Process ended — stop reading
                }
            });

            int exitCode = process.waitFor();
            stdoutReader.join();
            stderrReader.join();

            if (exitCode != 0) {
                return CompileResult.failure(new IllegalStateException(
                        "Maven compile failed with exit code " + exitCode));
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
