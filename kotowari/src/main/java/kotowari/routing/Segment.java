package kotowari.routing;

import enkan.collection.OptionMap;
import enkan.web.util.CodecUtils;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author kawasima
 */
public abstract class Segment {
    public static final String RESERVED_PCHAR = ":@&=+$,;";

    private @Nullable String value;
    private boolean isOptional;

    public Segment() {
        this(null);
    }
    public Segment(@Nullable String value) {
        this.value = value;
        isOptional = false;
    }

    public int numberOfCaptures() {
        return Pattern.compile(regexpChunk()).matcher("").groupCount();
    }

    public @Nullable String getExtractionCode() {
        return null;
    }

    public String continueStringStructure(List<Segment> list, OptionMap hash) {
        if (list.isEmpty()) {
            return interpolationStatement(list, hash);
        } else {
            List<Segment> newPriors = list.subList(0, list.size() - 1);
            return list.getLast().stringStructure(newPriors, hash);
        }
    }
    public String interpolationChunk(OptionMap hash) {
        // Only reached for segments that carry a value (e.g. StaticSegment).
        return CodecUtils.urlEncode(Objects.requireNonNull(value));
    }

    public String interpolationStatement(List<Segment> list, OptionMap hash) {
        StringBuilder chunks = new StringBuilder(128);
        for(Segment seg : list) {
            chunks.append(seg.interpolationChunk(hash));
        }
        chunks.append(interpolationChunk(hash));
        return allOptionalsAvailableCondition(list, hash) ? chunks.toString() : "";
    }

    public String stringStructure(List<Segment> list, OptionMap hash) {
        return isOptional ? continueStringStructure(list, hash) : interpolationStatement(list, hash);
    }

    public boolean allOptionalsAvailableCondition(List<Segment> priorSegments, OptionMap hash) {
        for (Segment segment : priorSegments) {
            String key = segment.getKey();
            if (!segment.isOptional() && key != null) {
                String v = hash.getString(key);
                if (v == null || v.isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    public void matchExtraction(OptionMap params, Matcher match, int nextCapture) {
    }

    public boolean hasKey() {
        return false;
    }

    public @Nullable String getKey() {
        return null;
    }

    public boolean hasDefault() {
        return false;
    }

    public @Nullable String getDefault() {
        return null;
    }

    public @Nullable String getValue() {
        return value;
    }

    public abstract String regexpChunk();
    public boolean isOptional() {
        return isOptional;
    }

    public void setOptional(boolean optional) {
        this.isOptional = optional;
    }

    public void setRegexp(Pattern regexp) {}
    public void setDefault(@Nullable String def) {}

    public abstract String buildPattern(String pattern);

    public @Nullable String getRegexp() {
        return null;
    }
}
