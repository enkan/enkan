package enkan.util.sf;

/**
 * Parser and serializer for RFC 8941 Structured Field Values.
 *
 * <p>This class provides static methods to parse and serialize the three
 * top-level types defined by RFC 8941: Items, Lists, and Dictionaries.
 * Date and Display String types from RFC 9651 are also supported.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Parse an Item header (e.g., Idempotency-Key)
 * SfItem item = StructuredFields.parseItem("\"abc-123\"");
 *
 * // Parse a Dictionary header (e.g., Signature-Input)
 * SfDictionary dict = StructuredFields.parseDictionary("sig1=(\"@method\" \"@path\");keyid=\"key1\"");
 *
 * // Serialize back to header value
 * String header = StructuredFields.serializeDictionary(dict);
 * }</pre>
 *
 * @author kawasima
 * @see <a href="https://www.rfc-editor.org/rfc/rfc8941">RFC 8941</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9651">RFC 9651</a>
 */
public final class StructuredFields {

    private StructuredFields() {}

    /**
     * Parses an Item header value (RFC 8941 §4.2.3).
     *
     * @param input the raw header value
     * @return the parsed Item
     * @throws SfParseException if the input is malformed
     */
    public static SfItem parseItem(String input) {
        if (input == null || input.isEmpty()) {
            throw new SfParseException("Empty input");
        }
        SfParser parser = new SfParser(input);
        SfItem item = parser.parseItem();
        parser.ensureEmpty();
        return item;
    }

    /**
     * Parses a List header value (RFC 8941 §4.2.1).
     *
     * @param input the raw header value
     * @return the parsed List
     * @throws SfParseException if the input is malformed
     */
    public static SfList parseList(String input) {
        if (input == null || input.isEmpty()) {
            throw new SfParseException("Empty input");
        }
        SfParser parser = new SfParser(input);
        SfList list = parser.parseList();
        parser.ensureEmpty();
        return list;
    }

    /**
     * Parses a Dictionary header value (RFC 8941 §4.2.2).
     *
     * @param input the raw header value
     * @return the parsed Dictionary
     * @throws SfParseException if the input is malformed
     */
    public static SfDictionary parseDictionary(String input) {
        if (input == null || input.isEmpty()) {
            throw new SfParseException("Empty input");
        }
        SfParser parser = new SfParser(input);
        SfDictionary dict = parser.parseDictionary();
        parser.ensureEmpty();
        return dict;
    }

    /**
     * Serializes an Item to a header value string (RFC 8941 §4.1.3).
     *
     * @param item the Item to serialize
     * @return the serialized header value
     */
    public static String serializeItem(SfItem item) {
        return SfSerializer.serializeItem(item);
    }

    /**
     * Serializes a List to a header value string (RFC 8941 §4.1.1).
     *
     * @param list the List to serialize
     * @return the serialized header value
     */
    public static String serializeList(SfList list) {
        return SfSerializer.serializeList(list);
    }

    /**
     * Serializes a Dictionary to a header value string (RFC 8941 §4.1.2).
     *
     * @param dict the Dictionary to serialize
     * @return the serialized header value
     */
    public static String serializeDictionary(SfDictionary dict) {
        return SfSerializer.serializeDictionary(dict);
    }
}
