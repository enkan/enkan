package kotowari.routing.segment;

import enkan.collection.OptionMap;
import enkan.exception.UnreachableException;
import enkan.web.util.CodecUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author kawasima
 */
public class PathSegment extends DynamicSegment {
    private static final Pattern ENCODED_SLASH = Pattern.compile("%2f", Pattern.CASE_INSENSITIVE);
    public PathSegment(String key, OptionMap options) {
        super(key, options);
    }

    @Override
    public String interpolationChunk(OptionMap hash) {
        String value = hash.getString(getKey());
        try {
            value = CodecUtils.urlEncode(value);
            Matcher m = ENCODED_SLASH.matcher(value);
            return m.replaceAll("/");
        } catch (Exception e) {
            return value;
        }
    }

    @Override
    public String getDefault() {
        return "";
    }

    public void setDefault(String path) {
        if (!path.isEmpty())
            throw new UnreachableException();
    }

    public String defaultRegexpChunk() {
        return "(.*)";
    }

    @Override
    public int numberOfCaptures() {
        return 1;
    }

    public boolean optionalityImplied() {
        return true;
    }

    @Override
    public void matchExtraction(OptionMap params, Matcher match, int nextCapture) {
        String value = match.group(nextCapture);
        params.put(getKey(), CodecUtils.urlDecode(value));
    }
}
