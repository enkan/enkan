package kotowari.graalvm.controller;

import app.example.SimpleForm;
import enkan.data.HttpRequest;

public class SimpleController {
    public String index() {
        return "index";
    }

    public String show(HttpRequest request) {
        return "show";
    }

    public SimpleForm create(SimpleForm form) {
        return form;
    }
}
