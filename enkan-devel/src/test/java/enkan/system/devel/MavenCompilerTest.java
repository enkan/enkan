package enkan.system.devel;

import enkan.Env;
import enkan.system.ReplResponse;
import enkan.system.Transport;
import enkan.system.devel.compiler.MavenCompiler;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * @author kawasima
 */
public class MavenCompilerTest {

    private static boolean isMvnAvailable() {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("win");
        String mvnBin = isWindows ? "bin/mvn.cmd" : "bin/mvn";
        String mavenHomeEnv = Env.getString("MAVEN_HOME", Env.getString("M2_HOME", null));
        if (mavenHomeEnv != null && !mavenHomeEnv.isBlank()) {
            return new File(mavenHomeEnv, mvnBin).canExecute();
        }
        // Check if mvn is resolvable from PATH
        String path = System.getenv("PATH");
        if (path == null) return false;
        String mvnExe = isWindows ? "mvn.cmd" : "mvn";
        return Arrays.stream(path.split(File.pathSeparator))
                .map(dir -> new File(dir, mvnExe))
                .anyMatch(File::canExecute);
    }

    @BeforeEach
    public void setup() throws IOException {
        FileUtils.deleteDirectory(new File("target/proj"));
        FileUtils.forceMkdir(new File("target/proj/src/main/java"));

        FileUtils.copyFileToDirectory(
                new File("src/test/resources/pom.xml"),
                new File("target/proj")
        );
    }


    @Test
    public void success() throws IOException {
        FileUtils.copyFileToDirectory(
                new File("src/test/resources/Hello.java"),
                new File("target/proj/src/main/java")
        );

        MavenCompiler compiler = new MavenCompiler();
        compiler.setProjectDirectory("target/proj");

        Transport t = new Transport() {
            @Override
            public void send(ReplResponse response) {
            }

            @Override
            public String recv(long timeout) {
                return null;
            }
        };

        assumeTrue(MavenCompilerTest::isMvnAvailable);
        CompileResult result = compiler.execute(t);
        assertThat(result.executionException()).isNull();
    }

    @Test
    public void compileError() throws IOException {
        FileUtils.copyFileToDirectory(
                new File("src/test/resources/HelloError.java"),
                new File("target/proj/src/main/java")
        );

        MavenCompiler compiler = new MavenCompiler();
        compiler.setProjectDirectory("target/proj");

        Transport t = new Transport() {
            @Override
            public void send(ReplResponse response) {
            }

            @Override
            public String recv(long timeout) {
                return null;
            }
        };

        assumeTrue(MavenCompilerTest::isMvnAvailable);
        CompileResult result = compiler.execute(t);
        assertThat(result.executionException()).isNotNull();
    }
}
