package kotowari.example.graalvm;

import enkan.web.application.WebApplication;
import enkan.util.MixinUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Standalone entry point invoked by the Maven {@code exec:java} goal (in the
 * {@code prepare-package} phase of the {@code native} profile) to
 * pre-generate the {@code $Mixin} class files required by GraalVM native image.
 *
 * <p>It triggers {@link MixinUtils#createFactory} by constructing the full
 * middleware stack via {@link NativeApplicationFactory#buildApp()} and calling
 * {@link WebApplication#createRequest()} once.  The resulting class bytes
 * (stored in {@link MixinUtils#generatedClassBytes}) are then written as
 * regular {@code .class} files into {@code target/classes/} so the Maven shade
 * plugin includes them in the fat JAR.  When native-image processes the fat JAR
 * it sees the mixin classes as ordinary compiled classes — no predefined-classes
 * mechanism is required.
 *
 * <p>Usage (via Maven exec plugin):
 * <pre>{@code
 * java -cp <classpath> kotowari.example.graalvm.GenerateMixinConfig <outputDir>
 * }</pre>
 * where {@code <outputDir>} defaults to {@code target/classes} when not given.
 */
public class GenerateMixinConfig {

    public static void main(String[] args) throws Exception {
        Path classesDir = args.length > 0 ? Path.of(args[0]).resolve("classes") : Path.of("target/classes");

        // Build the app and trigger mixin class generation
        WebApplication app = NativeApplicationFactory.buildApp();
        app.createRequest();

        Map<String, byte[]> generatedBytes = MixinUtils.generatedClassBytes;
        if (generatedBytes.isEmpty()) {
            System.out.println("[GenerateMixinConfig] No mixin classes generated — nothing to write.");
            return;
        }

        for (Map.Entry<String, byte[]> entry : generatedBytes.entrySet()) {
            String className = entry.getKey();   // e.g. "enkan.data.DefaultHttpRequest$Mixin1"
            byte[] bytes = entry.getValue();

            // Convert binary class name to path: "enkan/data/DefaultHttpRequest$Mixin1.class"
            String relativePath = className.replace('.', '/') + ".class";
            // For inner-class-style names like "Foo$Mixin1", the '$' stays as-is
            Path classFile = classesDir.resolve(relativePath);
            Files.createDirectories(classFile.getParent());
            Files.write(classFile, bytes);

            System.out.println("[GenerateMixinConfig] Wrote mixin class: " + className + " -> " + classFile);
        }
    }
}
