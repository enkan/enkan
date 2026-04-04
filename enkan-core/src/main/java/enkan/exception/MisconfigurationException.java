package enkan.exception;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.*;

/**
 * If the exception is caused by a misconfiguration, throws this exception.
 * MisconfigurationException is for the developers.
 *
 * @author kawasima
 */
public class MisconfigurationException extends UnrecoverableException {
    private final String problem;
    private final String solution;

    static final Properties misconfigurationMessages;

    static {
        misconfigurationMessages = loadMessages(Locale.getDefault());
    }

    /**
     * Loads and merges misconfiguration message properties from all JARs on the classpath,
     * with locale-specific files taking precedence over the base file (same lookup order
     * as {@link ResourceBundle}).
     *
     * <p>Loading order (later entries win, matching ResourceBundle fallback chain):
     * <ol>
     *   <li>{@code META-INF/misconfiguration.properties} (base)</li>
     *   <li>{@code META-INF/misconfiguration_<language>.properties}</li>
     *   <li>{@code META-INF/misconfiguration_<language>_<country>.properties}</li>
     *   <li>{@code META-INF/misconfiguration_<language>_<country>_<variant>.properties}</li>
     * </ol>
     *
     * <p>This replicates the ResourceBundle fallback behavior
     * without using {@link ResourceBundle.Control}, which is prohibited in named modules.
     */
    private static Properties loadMessages(Locale locale) {
        Properties merged = new Properties();
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = MisconfigurationException.class.getClassLoader();
        }

        // Build the ResourceBundle-style candidate list from least to most specific,
        // so that more specific files overwrite less specific ones.
        List<String> candidates = buildCandidateNames(locale);
        for (String candidate : candidates) {
            loadInto(loader, candidate, merged);
        }
        return merged;
    }

    private static List<String> buildCandidateNames(Locale locale) {
        List<String> names = new ArrayList<>();
        names.add("META-INF/misconfiguration.properties");

        String language = locale.getLanguage();
        String country  = locale.getCountry();
        String variant  = locale.getVariant();

        if (!language.isEmpty()) {
            names.add("META-INF/misconfiguration_" + language + ".properties");
            if (!country.isEmpty()) {
                names.add("META-INF/misconfiguration_" + language + "_" + country + ".properties");
                if (!variant.isEmpty()) {
                    names.add("META-INF/misconfiguration_" + language + "_" + country + "_" + variant + ".properties");
                }
            }
        }
        return names;
    }

    private static void loadInto(ClassLoader loader, String resourceName, Properties target) {
        try {
            Enumeration<URL> urls = loader.getResources(resourceName);
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                URLConnection conn = url.openConnection();
                conn.setUseCaches(false);
                try (InputStream is = conn.getInputStream();
                     InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                    Properties p = new Properties();
                    p.load(reader);
                    target.putAll(p);
                }
            }
        } catch (IOException e) {
            // Skip unreadable resource files; remaining candidates will still be loaded
        }
    }

    @Override
    public String getMessage() {
        return super.getMessage() + ":" + problem;
    }

    public MisconfigurationException(String code, Object... arguments) {
        super(code, Arrays.stream(arguments)
                .filter(arg -> arg instanceof Throwable)
                .map(arg -> (Throwable) arg)
                .findFirst().orElse(null));
        String problemFmt = misconfigurationMessages.getProperty(code + ".problem", code + ".problem (message not found)");
        problem = new MessageFormat(problemFmt, Locale.getDefault()).format(arguments);
        String solutionFmt = misconfigurationMessages.getProperty(code + ".solution", code + ".solution (message not found)");
        solution = new MessageFormat(solutionFmt, Locale.getDefault()).format(arguments);
    }

    public String getCode() {
        return super.getMessage();
    }

    public String getProblem() {
        return problem;
    }

    public String getSolution() {
        return solution;
    }
}
