package kotowari.example.controller;

import enkan.collection.Parameters;
import enkan.data.HttpResponse;
import enkan.data.Session;
import kotowari.component.TemplateEngine;
import jakarta.inject.Inject;

import java.io.File;

import static enkan.util.BeanBuilder.builder;
import static enkan.util.HttpResponseUtils.response;

/**
 * @author kawasima
 */
public class MiscController {
    @Inject
    private TemplateEngine<?> templateEngine;

    public HttpResponse uploadForm() {
        return templateEngine.render("misc/upload");
    }

    public String upload(Parameters params) {
        File tempfile = (File) params.getIn("datafile", "tempfile");
        return tempfile.getAbsolutePath() + "("
                + tempfile.length()
                + " bytes) is uploaded. description: "
                + params.get("description");
    }

    public HttpResponse counter(Session session) {
        if (session == null) {
            session = new Session();
        }
        Integer count = (Integer) session.get("count");
        count = (count == null) ? 1 : count + 1;
        session.put("count", count);
        return builder(response(count + "times."))
                .set(HttpResponse::setContentType, "text/plain")
                .set(HttpResponse::setSession, session)
                .build();

    }

}
