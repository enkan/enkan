package enkan.system.devel.compiler;

import enkan.Env;
import enkan.exception.FalteringEnvironmentException;
import enkan.system.ReplResponse;
import enkan.system.Transport;
import enkan.system.devel.CompileResult;
import enkan.system.devel.Compiler;
import org.gradle.tooling.*;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * A compiler implementation that delegates to Gradle's compileJava task
 * via the Gradle Tooling API.
 *
 * <p>By default, the project's Gradle wrapper distribution is used
 * ({@code useBuildDistribution()}). You can override this by setting
 * the {@code GRADLE_HOME} environment variable to use a local installation,
 * or by calling {@link #setGradleVersion(String)} to pin a specific version.</p>
 *
 * @author kawasima
 */
public class GradleCompiler implements Compiler {
    private static final Logger LOG = LoggerFactory.getLogger(GradleCompiler.class);

    private String projectDirectory = ".";
    private @Nullable String gradleVersion = null;

    @Override
    public CompileResult execute(Transport t) {
        GradleConnector connector = GradleConnector.newConnector()
                .forProjectDirectory(new File(projectDirectory));

        String gradleHome = Env.getString("GRADLE_HOME", null);
        if (gradleHome != null && !gradleHome.isEmpty()) {
            connector.useInstallation(new File(gradleHome));
        } else if (gradleVersion != null) {
            connector.useGradleVersion(gradleVersion);
        } else {
            connector.useBuildDistribution();
        }

        try (ProjectConnection connection = connector.connect()) {
            BuildLauncher launcher = connection.newBuild();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            launcher.setStandardOutput(out);
            launcher.setStandardError(err);
            launcher.forTasks("compileJava");

            CompletableFuture<GradleConnectionException> future = new CompletableFuture<>();
            launcher.run(new ResultHandler<>() {
                @Override
                public void onComplete(Void result) {
                    LOG.info("Gradle compileJava completed successfully");
                    future.complete(null);
                }

                @Override
                public void onFailure(GradleConnectionException failure) {
                    future.complete(failure);
                }
            });

            GradleConnectionException gce = future.get();
            if (out.size() > 0) {
                t.send(ReplResponse.withOut(out.toString()));
            }
            if (err.size() > 0) {
                t.send(ReplResponse.withErr(err.toString()));
            }
            return new CompileResult(gce);
        } catch (InterruptedException | ExecutionException e) {
            throw new FalteringEnvironmentException(e);
        }
    }

    public void setProjectDirectory(String projectDirectory) {
        this.projectDirectory = projectDirectory;
    }

    public void setGradleVersion(String gradleVersion) {
        this.gradleVersion = gradleVersion;
    }
}
