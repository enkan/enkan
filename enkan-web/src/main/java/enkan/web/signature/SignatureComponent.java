package enkan.web.signature;

import enkan.web.util.sf.SfItem;
import enkan.web.util.sf.SfParameters;
import enkan.web.util.sf.SfValue;

/**
 * A covered component identifier in an RFC 9421 {@code Signature-Input} inner list.
 *
 * <p>Each component has a name (e.g. {@code "@method"}, {@code "content-type"}) and
 * optional Structured Fields parameters ({@code ;sf}, {@code ;key=xxx}, {@code ;name=xxx}).
 *
 * @param name       the component identifier (lowercase)
 * @param parameters the SF parameters attached to this component
 * @author kawasima
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9421#section-2">RFC 9421 §2</a>
 */
public record SignatureComponent(String name, SfParameters parameters) {

    /** Creates a component with no parameters. */
    public static SignatureComponent of(String name) {
        return new SignatureComponent(name, SfParameters.EMPTY);
    }

    /** Creates a component from an {@link SfItem} in a parsed {@code Signature-Input} inner list. */
    public static SignatureComponent fromSfItem(SfItem item) {
        if (!(item.value() instanceof SfValue.SfString s)) {
            throw new IllegalArgumentException("Component identifier must be an SF String, got: " + item.value());
        }
        return new SignatureComponent(s.value(), item.parameters());
    }

    /** Returns {@code true} if this is a derived component (name starts with {@code @}). */
    public boolean isDerived() {
        return name.startsWith("@");
    }

    /** Returns {@code true} if the {@code ;sf} parameter is present. */
    public boolean isSf() {
        return parameters.get("sf") != null;
    }

    /** Returns {@code true} if the {@code ;bs} parameter is present. */
    public boolean isBs() {
        return parameters.get("bs") != null;
    }

    /** Returns {@code true} if the {@code ;req} parameter is present. */
    public boolean isReq() {
        return parameters.get("req") != null;
    }

    /** Returns the value of the {@code ;key} parameter, or {@code null} if absent. */
    public String keyParam() {
        SfValue v = parameters.get("key");
        return v instanceof SfValue.SfString s ? s.value() : null;
    }

    /** Returns the value of the {@code ;name} parameter, or {@code null} if absent. */
    public String nameParam() {
        SfValue v = parameters.get("name");
        return v instanceof SfValue.SfString s ? s.value() : null;
    }
}
