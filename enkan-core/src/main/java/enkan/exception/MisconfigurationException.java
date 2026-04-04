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
        misconfigurationMessages = loadMessages();
    }

    private static Properties loadMessages() {
        Properties merged = new Properties();
        String resourceName = "META-INF/misconfiguration.properties";
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = MisconfigurationException.class.getClassLoader();
        }
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
                    merged.putAll(p);
                }
            }
        } catch (IOException e) {
            // If we can't load messages, an empty Properties is used; messages will show raw codes
        }
        return merged;
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
