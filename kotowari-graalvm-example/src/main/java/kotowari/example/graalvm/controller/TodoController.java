package kotowari.example.graalvm.controller;

import enkan.collection.Parameters;
import enkan.data.HttpRequest;
import enkan.data.HttpResponse;
import kotowari.example.graalvm.form.TodoForm;
import kotowari.example.graalvm.model.Todo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static enkan.util.BeanBuilder.builder;

public class TodoController {
    private static final AtomicLong idGenerator = new AtomicLong(1);
    private static final Map<Long, Todo> store = new ConcurrentHashMap<>();

    public List<Todo> list(HttpRequest request) {
        return new ArrayList<>(store.values());
    }

    public Object show(HttpRequest request) {
        Parameters params = request.getParams();
        Long id = params.getLong("id");
        Todo todo = store.get(id);
        if (todo == null) {
            return builder(HttpResponse.of("Not Found"))
                    .set(HttpResponse::setStatus, 404)
                    .build();
        }
        return todo;
    }

    public Todo create(Todo todo) {
        long id = idGenerator.getAndIncrement();
        Todo created = new Todo(id, todo.title(), todo.done());
        store.put(id, created);
        return created;
    }

    public Object createWithValidation(TodoForm form) {
        if (form.hasErrors()) {
            return builder(HttpResponse.of("Validation failed"))
                    .set(HttpResponse::setStatus, 400)
                    .build();
        }
        long id = idGenerator.getAndIncrement();
        Todo created = new Todo(id, form.getTitle(), false);
        store.put(id, created);
        return created;
    }
}
