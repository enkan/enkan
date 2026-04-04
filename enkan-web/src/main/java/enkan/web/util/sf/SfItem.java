package enkan.web.util.sf;

/**
 * An Item in an RFC 8941 Structured Field (§3.3).
 * An Item is a bare value with optional parameters.
 *
 * @author kawasima
 */
public record SfItem(SfValue value, SfParameters parameters) implements SfMember {

    public SfItem(SfValue value) {
        this(value, SfParameters.EMPTY);
    }
}
