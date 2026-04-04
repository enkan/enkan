package enkan.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Properties;
import java.util.ResourceBundle;

/**
 * A control for loading UTF-8 ResourceBundle.
 *
 * @author kawasima
 * @deprecated {@link ResourceBundle.Control} is prohibited in named modules (JPMS).
 *             This class is no longer used internally; it will be removed in a future release.
 *             Use {@code ClassLoader.getResources()} with manual property merging instead.
 */
@Deprecated(since = "0.15.0", forRemoval = true)
public class MergeableResourceBundleControl extends ResourceBundle.Control {
    protected Properties load(URL url, boolean reload) throws IOException {
        Properties properties = new Properties();
        if (url != null) {
            URLConnection connection = url.openConnection();
            if (connection != null) {
                connection.setUseCaches(!reload);
                try (InputStream stream = connection.getInputStream();
                     InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    properties.load(reader);
                }
            }
        }
        return properties;
    }

    public ResourceBundle newBundle
            (String baseName, Locale locale, String format, ClassLoader loader, boolean reload)
            throws IOException {
        // The below is a copy of the default implementation.
        String bundleName = toBundleName(baseName, locale);
        String resourceName = toResourceName(bundleName, "properties");
        Enumeration<URL> urls = loader.getResources(resourceName);

        Properties properties = new Properties();
        while (urls.hasMoreElements()) {
            URL url = urls.nextElement();
            properties.putAll(load(url, reload));
        }
        return new MergeableResourceBundle(properties);
    }
}
