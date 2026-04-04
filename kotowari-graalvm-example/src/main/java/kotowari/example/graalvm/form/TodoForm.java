package kotowari.example.graalvm.form;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kotowari.data.Validatable;

import java.util.HashMap;
import java.util.Map;

/**
 * Form object for creating a Todo item with validation.
 * Implements Validatable so ValidateBodyMiddleware can collect constraint violations.
 */
public class TodoForm implements Validatable {
    private final Map<String, Object> extensions = new HashMap<>();

    @NotNull
    @Size(min = 1, max = 100)
    private String title;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getExtension(String name) {
        return (T) extensions.get(name);
    }

    @Override
    public <T> void setExtension(String name, T extension) {
        extensions.put(name, extension);
    }
}
