package enkan;

import enkan.exception.FalteringEnvironmentException;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * The utilities of environments.
 * In the Enkan application, set environments apart from configurations.
 *
 * @author kawasima
 */
public class Env {
    static final Map<String, String> envMap;

    static {
        Map<String, String> mutable = new HashMap<>();
        readEnvFile(mutable);
        readSystemEnv(mutable);
        readSystemProps(mutable);
        envMap = Collections.unmodifiableMap(mutable);
    }

    /**
     * Normalize the given key to lower case delimited by dot..
     *
     * @param key The key of the environment variable.
     * @return a normalized String
     */
    private static String normalizeKey(String key) {
        return key.toLowerCase(Locale.US).replace('_', '.');
    }

    /**
     * Read environments from a file.
     */
    private static void readEnvFile(Map<String, String> map) {
        Properties properties = new Properties();
        URL url = Thread.currentThread().getContextClassLoader().getResource("env.properties");
        if (url != null) {
            try (Reader reader=new InputStreamReader(url.openStream(), StandardCharsets.UTF_8)) {
                properties.load(reader);
                properties.stringPropertyNames()
                        .forEach(k -> map.put(normalizeKey(k), properties.getProperty(k)));
            } catch (IOException e) {
                throw new FalteringEnvironmentException(e);
            }
        }
    }

    /**
     * Read environments from system properties.
     */
    private static void readSystemProps(Map<String, String> map) {
        System.getProperties().stringPropertyNames()
                .forEach(k -> map.put(normalizeKey(k), System.getProperty(k)));
    }

    /**
     * Read environments from environment variables.
     */
    private static void readSystemEnv(Map<String, String> map) {
        System.getenv().forEach((k, v) -> map.put(normalizeKey(k), v));
    }

    /**
     * Get an environment variable, or {@code null} if not set.
     *
     * @param name variable name
     * @return value, or {@code null} if the variable is not set
     */
    public static @Nullable String get(String name) {
        return getString(name, null);
    }

    /**
     * Get an environment variable as String.
     *
     * @param name variable name
     * @param defaultValue default value
     * @return value
     */
    public static @Nullable String getString(String name, @Nullable String defaultValue) {
        String value = envMap.get(normalizeKey(name));
        return value != null ? value : defaultValue;
    }

    /**
     * Get an environment variable as Integer.
     *
     * @param name variable name
     * @param defaultValue default value
     * @return integer value
     */
    public static int getInt(String name, int defaultValue) {
        String value = envMap.get(normalizeKey(name));
        return value != null ? Integer.parseInt(value) : defaultValue;
    }

    /**
     * Get an environment variable as Long.
     *
     * @param name variable name
     * @param defaultValue default value
     * @return long value
     */
    public static long getLong(String name, long defaultValue) {
        String value = envMap.get(normalizeKey(name));
        return value != null ? Long.parseLong(value) : defaultValue;
    }

    /**
     * Get an environment variable as Boolean.
     *
     * @param name variable name
     * @param defaultValue default value
     * @return boolean value
     */
    public static boolean getBoolean(String name, boolean defaultValue) {
        String value = envMap.get(normalizeKey(name));
        return value != null ? Boolean.parseBoolean(value) : defaultValue;
    }

}
