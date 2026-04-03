package enkan.util.sf;

import java.util.List;

/**
 * A List in an RFC 8941 Structured Field (§3.1).
 * Members are either {@link SfItem} or {@link SfInnerList}.
 *
 * @author kawasima
 */
public record SfList(List<Object> members) {

    /**
     * Returns the member at the given index, cast to the expected type.
     *
     * @param index the zero-based index
     * @param type  the expected type ({@link SfItem} or {@link SfInnerList})
     * @param <T>   the expected type
     * @return the member at the given index
     */
    public <T> T get(int index, Class<T> type) {
        return type.cast(members.get(index));
    }

    public int size() {
        return members.size();
    }
}
