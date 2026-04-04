package enkan.web.middleware.session;

import java.io.Serializable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public interface AsyncKeyValueStore extends KeyValueStore {
    Future<Serializable> readAsync(String key, ExecutorService executorService);
    Future<String> writeAsync(String key, Serializable value, ExecutorService executorService);
    Future<String> deleteAsync(String key, ExecutorService executorService);

}
