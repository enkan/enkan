package kotowari.example.controller;

import enkan.web.data.HttpResponse;
import enkan.exception.UnreachableException;
import kotowari.component.TemplateEngine;
import jakarta.inject.Inject;

/**
 * @author kawasima
 */
public class HospitalityDemoController {
    @Inject
    private TemplateEngine<?> templateEngine;

    public String unreachable() {
        throw new UnreachableException();
    }

    public HttpResponse misconfiguration() {
        return templateEngine.render("misc/misconfiguration");
    }
}
