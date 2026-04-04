package kotowari.graalvm.controller;

import app.example.Address;
import app.example.SimpleForm;
import enkan.data.HttpRequest;

import java.util.List;

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

    public List<Address> listAddresses() {
        return List.of();
    }
}
