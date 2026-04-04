package benchmark.enkan.controller;

import enkan.collection.Parameters;
import enkan.web.data.HttpResponse;
import enkan.data.Session;

import java.time.Instant;
import java.util.Map;

import static enkan.util.BeanBuilder.builder;
import static enkan.util.HttpResponseUtils.response;

public class BenchmarkController {

    public HttpResponse hello() {
        return builder(response("Hello, World!"))
                .set(HttpResponse::setContentType, "text/plain")
                .build();
    }

    public Map<String, Object> json() {
        return Map.of(
                "message", "Hello, World!",
                "timestamp", Instant.now().toEpochMilli()
        );
    }

    public HttpResponse echo(Parameters params, Session session) {
        String name = params.get("name");
        if (name == null) name = "world";

        int count = 0;
        if (session != null && session.containsKey("count")) {
            count = (Integer) session.get("count");
        } else {
            session = new Session();
        }
        count++;
        session.put("count", count);

        String body = "{\"name\":\"" + name + "\",\"count\":" + count + "}";
        return builder(response(body))
                .set(HttpResponse::setContentType, "application/json")
                .set(HttpResponse::setSession, session)
                .build();
    }
}
