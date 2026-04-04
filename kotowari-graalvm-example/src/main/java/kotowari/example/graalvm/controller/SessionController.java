package kotowari.example.graalvm.controller;

import enkan.web.data.HttpRequest;
import enkan.data.Session;

import java.io.Serializable;

public class SessionController {

    /**
     * GET /session — returns and increments a per-session visit counter.
     * The session is populated by SessionMiddleware before this method is called.
     */
    public String visit(HttpRequest request) {
        Session session = request.getSession();
        if (session == null) {
            session = new Session();
            request.setSession(session);
        }
        int count = 0;
        Serializable stored = session.get("visits");
        if (stored instanceof Integer n) {
            count = n;
        } else if (stored != null) {
            session.remove("visits");
        }
        count++;
        session.put("visits", count);
        return "visits=" + count;
    }
}
