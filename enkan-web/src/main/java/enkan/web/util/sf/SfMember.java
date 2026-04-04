package enkan.web.util.sf;

/**
 * A member of a List or Dictionary in an RFC 8941 Structured Field.
 * A member is either an {@link SfItem} or an {@link SfInnerList}.
 *
 * @author kawasima
 */
public sealed interface SfMember permits SfItem, SfInnerList {
}
