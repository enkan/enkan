package kotowari.routing.segment;

import enkan.collection.OptionMap;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the various {@link kotowari.routing.Segment} implementations.
 */
public class SegmentTest {

    @Nested
    class StaticSegmentTests {

        @Test
        void toStringReturnsValue() {
            StaticSegment segment = new StaticSegment("users");
            assertThat(segment.toString()).isEqualTo("users");
        }

        @Test
        void interpolationChunkUrlEncodesValue() {
            StaticSegment segment = new StaticSegment("hello world");
            assertThat(segment.interpolationChunk(OptionMap.of())).isEqualTo("hello%20world");
        }

        @Test
        void rawInterpolationChunkReturnsValueAsIs() {
            StaticSegment segment = new StaticSegment("/", OptionMap.of("raw", true));
            assertThat(segment.interpolationChunk(OptionMap.of())).isEqualTo("/");
        }

        @Test
        void regexpChunkEscapesSpecialCharacters() {
            StaticSegment segment = new StaticSegment("users.json");
            assertThat(segment.regexpChunk()).isEqualTo("users\\.json");
        }

        @Test
        void optionalRegexpChunkIsOptionalized() {
            StaticSegment segment = new StaticSegment("ext", OptionMap.of("optional", true));
            assertThat(segment.isOptional()).isTrue();
            // optionalized form wraps the escaped value
            assertThat(segment.regexpChunk()).contains("?");
        }

        @Test
        void numberOfCapturesIsZero() {
            StaticSegment segment = new StaticSegment("static");
            assertThat(segment.numberOfCaptures()).isEqualTo(0);
        }

        @Test
        void buildPatternPrependsEscapedValue() {
            StaticSegment segment = new StaticSegment("users");
            assertThat(segment.buildPattern("")).isEqualTo("users");
            assertThat(segment.buildPattern("/list")).isEqualTo("users/list");
        }
    }

    @Nested
    class DynamicSegmentTests {

        @Test
        void hasKeyReturnsTrue() {
            DynamicSegment segment = new DynamicSegment("id");
            assertThat(segment.hasKey()).isTrue();
        }

        @Test
        void getKeyReturnsTheKey() {
            DynamicSegment segment = new DynamicSegment("id");
            assertThat(segment.getKey()).isEqualTo("id");
        }

        @Test
        void toStringFormatsWithColon() {
            DynamicSegment segment = new DynamicSegment("id");
            assertThat(segment.toString()).isEqualTo(":id");
        }

        @Test
        void toStringWithWrapParentheses() {
            DynamicSegment segment = new DynamicSegment("id",
                    OptionMap.of("wrapParentheses", true));
            assertThat(segment.toString()).isEqualTo("(:id)");
        }

        @Test
        void regexpChunkGeneratesCaptureGroup() {
            DynamicSegment segment = new DynamicSegment("id");
            String chunk = segment.regexpChunk();
            // Should be a capturing group that matches non-separator chars
            assertThat(chunk).startsWith("(");
            assertThat(chunk).endsWith(")");
        }

        @Test
        void regexpChunkUsesCustomRegexp() {
            DynamicSegment segment = new DynamicSegment("id",
                    OptionMap.of("regexp", "\\d+"));
            assertThat(segment.regexpChunk()).isEqualTo("(\\d+)");
        }

        @Test
        void hasDefaultReturnsTrue() {
            DynamicSegment segment = new DynamicSegment("action");
            assertThat(segment.hasDefault()).isTrue();
        }

        @Test
        void defaultValueIsSetFromOptions() {
            DynamicSegment segment = new DynamicSegment("action",
                    OptionMap.of("default", "index"));
            assertThat(segment.getDefault()).isEqualTo("index");
        }

        @Test
        void interpolationChunkUrlEncodesValue() {
            DynamicSegment segment = new DynamicSegment("name");
            OptionMap hash = OptionMap.of("name", "hello world");
            assertThat(segment.interpolationChunk(hash)).isEqualTo("hello%20world");
        }

        @Test
        void buildPatternContainsCaptureGroup() {
            DynamicSegment segment = new DynamicSegment("id");
            String pattern = segment.buildPattern("");
            assertThat(pattern).startsWith("(");
            assertThat(pattern).contains("+)");
        }
    }

    @Nested
    class DividerSegmentTests {

        @Test
        void toStringReturnsTheSeparator() {
            DividerSegment segment = new DividerSegment("/");
            assertThat(segment.toString()).isEqualTo("/");
        }

        @Test
        void isOptionalByDefault() {
            DividerSegment segment = new DividerSegment("/");
            assertThat(segment.isOptional()).isTrue();
        }

        @Test
        void optionalityIsImplied() {
            DividerSegment segment = new DividerSegment("/");
            assertThat(segment.isOptionalityImplied()).isTrue();
        }

        @Test
        void interpolationChunkReturnsRawValue() {
            DividerSegment segment = new DividerSegment("/");
            // DividerSegment sets raw=true, so interpolationChunk returns value as-is
            assertThat(segment.interpolationChunk(OptionMap.of())).isEqualTo("/");
        }

        @Test
        void dotDivider() {
            DividerSegment segment = new DividerSegment(".");
            assertThat(segment.toString()).isEqualTo(".");
            assertThat(segment.interpolationChunk(OptionMap.of())).isEqualTo(".");
        }
    }

    @Nested
    class OptionalFormatSegmentTests {

        @Test
        void toStringReturnsFormatPattern() {
            OptionalFormatSegment segment = new OptionalFormatSegment();
            assertThat(segment.toString()).isEqualTo("(.:format)?");
        }

        @Test
        void keyIsFormat() {
            OptionalFormatSegment segment = new OptionalFormatSegment();
            assertThat(segment.getKey()).isEqualTo("format");
        }

        @Test
        void isOptionalFlagNotSetByDynamicSegmentConstructor() {
            // OptionalFormatSegment passes "optional" to DynamicSegment, but
            // DynamicSegment does not handle that option -- so isOptional()
            // remains false. The optionality is expressed in regexpChunk() instead.
            OptionalFormatSegment segment = new OptionalFormatSegment();
            assertThat(segment.isOptional()).isFalse();
        }

        @Test
        void regexpChunkMatchesOptionalDotExtension() {
            OptionalFormatSegment segment = new OptionalFormatSegment();
            String chunk = segment.regexpChunk();
            // Should match an optional .ext pattern
            assertThat(chunk).contains("?");
            assertThat(chunk).contains("\\.");
        }

        @Test
        void interpolationChunkPrependsDot() {
            OptionalFormatSegment segment = new OptionalFormatSegment();
            OptionMap hash = OptionMap.of("format", "json");
            assertThat(segment.interpolationChunk(hash)).isEqualTo(".json");
        }
    }

    @Nested
    class PathSegmentTests {

        @Test
        void keyIsSet() {
            PathSegment segment = new PathSegment("path", OptionMap.of());
            assertThat(segment.getKey()).isEqualTo("path");
        }

        @Test
        void defaultIsEmptyString() {
            PathSegment segment = new PathSegment("path", OptionMap.of());
            assertThat(segment.getDefault()).isEqualTo("");
        }

        @Test
        void defaultRegexpChunkIsGreedy() {
            PathSegment segment = new PathSegment("path", OptionMap.of());
            assertThat(segment.defaultRegexpChunk()).isEqualTo("(.*)");
        }

        @Test
        void numberOfCapturesIsOne() {
            PathSegment segment = new PathSegment("path", OptionMap.of());
            assertThat(segment.numberOfCaptures()).isEqualTo(1);
        }

        @Test
        void optionalityIsImplied() {
            PathSegment segment = new PathSegment("path", OptionMap.of());
            assertThat(segment.optionalityImplied()).isTrue();
        }

        @Test
        void interpolationChunkDecodesSlashes() {
            PathSegment segment = new PathSegment("path", OptionMap.of());
            OptionMap hash = OptionMap.of("path", "a/b/c");
            String chunk = segment.interpolationChunk(hash);
            // Encoded slashes should be decoded back to /
            assertThat(chunk).isEqualTo("a/b/c");
        }
    }
}
