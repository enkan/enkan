package enkan.util.sf;

import java.util.List;

/**
 * An Inner List in an RFC 8941 Structured Field (§3.1.1).
 * An Inner List is a list of Items with optional parameters on the list itself.
 *
 * @author kawasima
 */
public record SfInnerList(List<SfItem> items, SfParameters parameters) implements SfMember {

    public SfInnerList(List<SfItem> items) {
        this(items, SfParameters.EMPTY);
    }
}
