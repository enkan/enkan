package kotowari.routing;

import kotowari.routing.segment.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RouteBuilderTest {

    private RouteBuilder builder;

    @BeforeEach
    void setup() {
        builder = new RouteBuilder();
    }

    @Test
    void simpleStaticPath() {
        List<Segment> segments = builder.segmentsForRoutePath("/users");
        assertThat(segments).hasSize(2);
        assertThat(segments.get(0)).isInstanceOf(DividerSegment.class);
        assertThat(segments.get(0).getValue()).isEqualTo("/");
        assertThat(segments.get(1)).isInstanceOf(StaticSegment.class);
        assertThat(segments.get(1).getValue()).isEqualTo("users");
    }

    @Test
    void dynamicSegment() {
        List<Segment> segments = builder.segmentsForRoutePath("/:id");
        assertThat(segments).hasSize(2);
        assertThat(segments.get(0)).isInstanceOf(DividerSegment.class);
        assertThat(segments.get(1)).isInstanceOf(DynamicSegment.class);
        assertThat(segments.get(1).getKey()).isEqualTo("id");
        assertThat(segments.get(1).hasKey()).isTrue();
    }

    @Test
    void globPathSegment() {
        List<Segment> segments = builder.segmentsForRoutePath("/*path");
        assertThat(segments).hasSize(2);
        assertThat(segments.get(0)).isInstanceOf(DividerSegment.class);
        assertThat(segments.get(1)).isInstanceOf(PathSegment.class);
        assertThat(segments.get(1).getKey()).isEqualTo("path");
    }

    @Test
    void complexPathWithMultipleSegments() {
        List<Segment> segments = builder.segmentsForRoutePath("/users/:id/edit");
        // Expected: / users / :id / edit
        assertThat(segments).hasSize(6);
        assertThat(segments.get(0)).isInstanceOf(DividerSegment.class);
        assertThat(segments.get(1)).isInstanceOf(StaticSegment.class);
        assertThat(segments.get(1).getValue()).isEqualTo("users");
        assertThat(segments.get(2)).isInstanceOf(DividerSegment.class);
        assertThat(segments.get(3)).isInstanceOf(DynamicSegment.class);
        assertThat(segments.get(3).getKey()).isEqualTo("id");
        assertThat(segments.get(4)).isInstanceOf(DividerSegment.class);
        assertThat(segments.get(5)).isInstanceOf(StaticSegment.class);
        assertThat(segments.get(5).getValue()).isEqualTo("edit");
    }

    @Test
    void multipleDynamicSegments() {
        List<Segment> segments = builder.segmentsForRoutePath("/:controller/:action");
        // Expected: / :controller / :action
        assertThat(segments).hasSize(4);
        assertThat(segments.get(1)).isInstanceOf(DynamicSegment.class);
        assertThat(segments.get(1).getKey()).isEqualTo("controller");
        assertThat(segments.get(3)).isInstanceOf(DynamicSegment.class);
        assertThat(segments.get(3).getKey()).isEqualTo("action");
    }
}
