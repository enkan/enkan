package enkan.util.sf;

/**
 * Thrown when a Structured Fields header value cannot be parsed per RFC 8941.
 *
 * @author kawasima
 */
public class SfParseException extends RuntimeException {

    public SfParseException(String message) {
        super(message);
    }
}
