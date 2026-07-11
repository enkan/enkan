package kotowari.routing.segment;

import enkan.collection.OptionMap;

import org.jspecify.annotations.Nullable;

/**
 * @author kawasima
 */
public class DividerSegment extends StaticSegment {
    public DividerSegment(@Nullable String value, OptionMap options) {
        super(value, setDefault(options));
    }

    private static OptionMap setDefault(OptionMap options) {
        options.put("raw", true);
        options.put("optional", true);
        return options;
    }


    public DividerSegment(String value) {
        this(value, OptionMap.of());
    }
    public DividerSegment() {
        this(null, OptionMap.of());
    }

    public boolean isOptionalityImplied() {
        return true;
    }
}
