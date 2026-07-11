package enkan.system.command;

import enkan.component.WebServerComponent;
import enkan.system.EnkanSystem;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import enkan.system.ReplResponse;
import enkan.system.SystemCommand;
import enkan.system.Transport;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import static enkan.system.ReplResponse.ResponseStatus.*;

/**
 * The command for requesting json to the server.
 *
 * @author kawasima
 */
public class JsonRequestCommand implements SystemCommand {
    private static final long serialVersionUID = 1L;

    @Override
    public String shortDescription() {
        return "Send JSON HTTP request";
    }

    @Override
    public String detailedDescription() {
        return "Send an HTTP request to the local web server.\nUsage: /jsonRequest <method> <path> [json body]";
    }

    @Override
    public boolean execute(@Nullable EnkanSystem system, Transport transport, String... args) {
        Objects.requireNonNull(system);
        if (args.length < 2) {
            transport.sendErr("/jsonRequest [method] [path] [request json(optional)]", DONE);
            return true;
        }
        String method = args[0].toUpperCase(Locale.US);
        String pathAndQuery = args[1];
        StringBuilder sb = new StringBuilder();
        for (int i=2; i<args.length; i++) {
            sb.append(args[i]).append(" ");
        }
        String jsonBody = sb.toString().trim();
        @SuppressWarnings("unchecked")
        List<WebServerComponent<?>> webServers = (List<WebServerComponent<?>>)(List<?>)system.getComponents(WebServerComponent.class);
        if (webServers.isEmpty()) {
            transport.sendErr("WebServerComponent not found", DONE);
            return true;
        }
        WebServerComponent<?> webServer = webServers.getFirst();
        URL url;
        try {
            url = URI.create((webServer.isSsl() ? "https" : "http")
                    + "://" + webServer.getHost()
                    + ":" + webServer.getPort()
                    + pathAndQuery).toURL();
        } catch (MalformedURLException e) {
            transport.sendErr("Malformed url: " + pathAndQuery, DONE);
            return true;
        }

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(method);
            connection.setInstanceFollowRedirects(false);
            connection.setAllowUserInteraction(false);
            connection.setUseCaches(false);
            if (!jsonBody.isEmpty()) {
                connection.setRequestProperty("Content-Type", "application/json");
            }
            connection.setRequestProperty("Accept", "application/json");

            connection.setDoOutput(!jsonBody.isEmpty());
            connection.setDoInput(true);

            connection.connect();
            if (!jsonBody.isEmpty()) {
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                }
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                reader.lines().forEach(line -> transport.send(ReplResponse.withOut(line)));
            }
            transport.sendOut("");
        } catch (FileNotFoundException e) {
            if (connection == null) {
                transport.sendErr("IO Error: " + e.getLocalizedMessage());
                return true;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getErrorStream()))) {
                transport.send(ReplResponse.withOut(connection.getResponseCode() + " " + connection.getResponseMessage()));
                transport.send(ReplResponse.withOut(""));
                reader.lines().forEach(line -> transport.send(ReplResponse.withOut(line)));
                transport.sendOut("");
            } catch (IOException ioe) {
                transport.sendErr("IO Error: " + e.getLocalizedMessage());
                return true;
            }
        } catch (IOException e) {
            transport.sendErr("IO Error: " + e.getLocalizedMessage(), DONE);
            return true;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return true;
    }
}
