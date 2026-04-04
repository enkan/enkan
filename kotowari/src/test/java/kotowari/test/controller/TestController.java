package kotowari.test.controller;

import enkan.web.data.HttpResponse;
import kotowari.test.form.NestedForm;

public class TestController {
    public HttpResponse index(NestedForm form) {
        return HttpResponse.of("");
    }
}
