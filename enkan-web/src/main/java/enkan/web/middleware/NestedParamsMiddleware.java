package enkan.web.middleware;

import enkan.MiddlewareChain;
import enkan.annotation.Middleware;
import enkan.collection.Parameters;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.exception.MisconfigurationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Middleware that converts flat, bracket-notation request parameters into
 * nested {@link enkan.collection.Parameters} structures.
 *
 * <p>For example, the query string {@code user[name]=Alice&user[age]=30}
 * is expanded to {@code {user: {name: "Alice", age: "30"}}}.
 * Array notation ({@code items[]=a&items[]=b}) produces a list value.
 *
 * <p>This middleware requires the {@code params} middleware to have already
 * populated {@link enkan.data.HttpRequest#getParams()}.
 *
 * @author kawasima
 */
@Middleware(name = "nestedParams", dependencies = {"params"})
public class NestedParamsMiddleware implements WebMiddleware {
    private static final int MAX_NESTING_DEPTH = 32;
    private static final Pattern RE_NESTED_NAME = Pattern.compile("^(?s)(.*?)((?:\\[.*?\\])*)$");
    private static final Pattern RE_NESTED_TOKEN = Pattern.compile("\\[(.*?)\\]");
    protected final Function<String, String[]> parseNestedKeys = (paramName) -> {
        if (paramName == null) return new String[]{};

        Matcher m = RE_NESTED_NAME.matcher(paramName);
        List<String> keys = new ArrayList<>();

        if (m.find()) {
            keys.add(m.group(1));
            String ks = m.group(2);
            if (ks != null && !ks.isEmpty()) {
                Matcher mt = RE_NESTED_TOKEN.matcher(ks);
                while (mt.find()) {
                    keys.add(mt.group(1));
                }
            } else {
                return new String[]{ m.group(1) };
            }
        }

        return keys.toArray(new String[0]);
    };

    protected Parameters assocVector(Parameters map, String key, Object value) {
        if (!map.containsKey(key)) {
            map.put(key, new ArrayList<>());
        }
        return assocConj(map, key, value);
    }

    /**
     * Association
     *
     * <ul>
     *   <li>foo[bar]=[aaa,bbb] =&gt; {foo: {bar: [aaa, bbb]}}</li>
     *   <li>for[][bar]=[aaa,bbb] =&gt; {foo: [{bar: aaa}, {bar: bbb}]}</li>
     *   <li>for[]=[aaa,bbb] =&gt; {foo: [aaa, bbb]}</li>
     * </ul>
     *
     * @param map    a Parameters
     * @param key    a String key
     * @param value  a Object associated with the key
     * @return a Parameters contains the given key and value
     */
    protected Parameters assocConj(Parameters map, String key, Object value) {
        Object cur = map.getRawType(key);
        if (cur != null) {
            if (cur instanceof List<?> curList) {
                @SuppressWarnings("unchecked")
                List<Object> curTyped = (List<Object>) curList;
                if (value instanceof List<?> valueList) {
                    curTyped.addAll(valueList);
                } else {
                    curTyped.add(value);
                }
            } else {
                List<Object> values = new ArrayList<>();
                values.add(cur);
                if (value instanceof List<?> valueList) {
                    values.addAll(valueList);
                } else {
                    values.add(value);
                }
                map.put(key, values);
            }
        } else {
            if (value instanceof List<?> values) {
                if (values.size() > 1) {
                    assocVector(map, key, value);
                } else if (values.size() == 1) {
                    map.put(key, values.getFirst());
                }
            } else {
                map.put(key, value);
            }
        }
        return map;
    }

    protected Object assocNested(Parameters map, String[] keys, List<String> values) {
        return assocNested(map, keys, values, 0);
    }

    private Object assocNested(Parameters map, String[] keys, List<String> values, int depth) {
        if (depth > MAX_NESTING_DEPTH) {
            throw new MisconfigurationException("web.NESTING_TOO_DEEP", MAX_NESTING_DEPTH);
        }
        if (keys.length > 0) {
            String[] ks = new String[keys.length - 1];

            if (ks.length > 0) {
                System.arraycopy(keys, 1, ks, 0, ks.length);

                String j = ks[0];
                if (j.isEmpty()) {
                    // Array
                    String[] js = new String[keys.length - 2];
                    if (js.length > 0) {
                        System.arraycopy(keys, 2, js, 0, js.length);
                    }

                    List<Object> nestedList = map.getList(keys[0]);

                    for (int i = nestedList.size(); i < values.size(); i++) nestedList.add(null);

                    for (int i = 0; i < values.size(); i++) {
                        List<String> vs = new ArrayList<>();
                        vs.add(values.get(i));
                        if (js.length > 0) {
                            nestedList.set(i,
                                    assocNested((Parameters) Optional.ofNullable(nestedList.get(i)).orElse(Parameters.empty())
                                            , js, vs, depth + 1));
                        } else {
                            nestedList.set(i, vs.getFirst());
                        }

                    }
                    if (map.containsKey(keys[0])) {
                        map.replace(keys[0], nestedList);
                    } else {
                        map.put(keys[0], nestedList);
                    }
                    return map;
                } else {
                    // Map
                    Object existing = map.getRawType(keys[0]);
                    Parameters submap = existing instanceof Parameters p ? p : Parameters.empty();
                    map.put(keys[0], assocNested(submap, ks, values, depth + 1));
                    return map;
                }
            } else {
                return assocConj(map, keys[0], values);
            }
        } else {
            return values;
        }
    }

    /**
     * Transforms the flat request parameters into a nested structure using the
     * supplied key parser and stores the result back on the request.
     *
     * @param request   the incoming HTTP request
     * @param keyParser a function that splits a parameter name (e.g. {@code "user[name]"})
     *                  into an array of nested keys (e.g. {@code ["user", "name"]})
     * @return the same request with updated nested parameters
     */
    public HttpRequest nestedParamsRequest(HttpRequest request, Function<String, String[]> keyParser) {
        Parameters params = request.getParams();
        if (params == null || params.isEmpty()) {
            return request;
        }
        Parameters nestedParams = Parameters.empty();

        params.keySet().forEach(key -> assocNested(nestedParams, keyParser.apply(key), params.getList(key)));
        request.setParams(nestedParams);
        return request;
    }

    @Override
    public <NNREQ, NNRES> HttpResponse handle(HttpRequest request, MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
        return castToHttpResponse(chain.next(nestedParamsRequest(request, parseNestedKeys)));
    }
}
