package kotowari.routing.segment;

import enkan.collection.OptionMap;
import enkan.web.util.CodecUtils;

import java.util.regex.Matcher;

/**
 * @author kawasima
 */
public class OptionalFormatSegment extends DynamicSegment{
    public OptionalFormatSegment(String key, OptionMap options) {
        super("format", OptionMap.of("optional", true));
    }

    public OptionalFormatSegment() {
        this(null, OptionMap.of());
    }

    @Override
    public String interpolationChunk(OptionMap hash) {
        return "." + super.interpolationChunk(hash);
    }

    @Override
    public String regexpChunk() {
        return "/|(\\.[^/?\\.]+)?";
    }

    @Override
    public String toString() {
        return "(.:format)?";
    }

    @Override
    public void matchExtraction(OptionMap params, Matcher match, int nextCapture) {
        String m = match.group(nextCapture);
        if (m != null) {
            params.put(getKey(), CodecUtils.urlDecode(m.substring(1)));
        } else {
            params.put(getKey(), CodecUtils.urlDecode(getDefault()));
        }
    }
}
