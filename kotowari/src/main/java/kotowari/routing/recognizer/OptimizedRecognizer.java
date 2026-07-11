package kotowari.routing.recognizer;

import enkan.collection.OptionMap;
import enkan.web.data.HttpRequest;
import kotowari.routing.*;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author kawasima
 */
public class OptimizedRecognizer implements Recognizer {
    private @Nullable SegmentNode tree;
    private List<Route> routes = new ArrayList<>();

    public String[] toPlainSegments(String str) {
        str = str.replaceAll("^/+", "").replaceAll("/+$", "");
        return str.split("\\.[^/]+/+|" + "[" + RegexpUtils.escape(String.join("", RouteBuilder.SEPARATORS)) + "]+|\\.[^/]+\\Z");
    }

    public void setRoutes(List<Route> routes) {
        this.routes = routes;
        optimize();
    }

    @Override
    public boolean isOptimized() {
        return tree != null;
    }

    @Override
    public void optimize() {
        tree = new SegmentNode(0);

        int i = -1;
        for (Route route : routes) {
            i += 1;
            SegmentNode node = tree;
            String[] segments = toPlainSegments(route.getSegments().stream().map(Segment::toString).collect(Collectors.joining("")));
            for (String seg : segments) {
                if (!seg.isEmpty() && seg.charAt(0) == ':') {
                    seg = ":dynamic";
                }
                if (node.isEmpty() || !seg.equals(Objects.requireNonNull(node.lastChild()).getLabel())) {
                    node.add(new SegmentNode(seg, i));
                }
                node = node.lastChild();
                if (node == null) break;
            }
        }
    }
    private int calcIndex(String[] segments, SegmentNode node, int level) {
        if (node.size() <= 1 || segments.length == level)
            return node.getIndex();
        String seg = segments[level];
        for (SegmentNode item : node.getChildNodes()) {
            if (":dynamic".equals(item.getLabel()) || seg.equals(item.getLabel())) {
                return calcIndex(segments, item, level + 1);
            }
        }
        return node.getIndex();
    }

    @Override
    public OptionMap recognize(HttpRequest request) {
        String[] segments = toPlainSegments(Objects.requireNonNull(request.getUri(), "request URI"));

        int index = calcIndex(segments, Objects.requireNonNull(tree, "recognizer not optimized"), 0);
        while (index < routes.size()) {
            OptionMap result = routes.get(index).recognize(request);
            if (result != null) return result;
            index += 1;
        }
        return OptionMap.of();
    }

    private static class SegmentNode {
        private final int index;
        private final @Nullable String label;
        private final List<SegmentNode> childNodes;

        SegmentNode(int index) {
            this(null, index);
        }

        SegmentNode(@Nullable String label, int index) {
            this.index = index;
            this.label = label;
            childNodes = new ArrayList<>();
        }

        void add(SegmentNode child) {
            childNodes.add(child);
        }

        boolean isEmpty() {
            return childNodes.isEmpty();
        }

        @Nullable SegmentNode lastChild() {
            if (isEmpty())
                return null;
            return childNodes.getLast();
        }

        @Nullable String getLabel() {
            return label;
        }

        int getIndex() {
            return index;
        }

        List<SegmentNode> getChildNodes() {
            return childNodes;
        }
        int size() {
            return childNodes.size();
        }
    }
}
