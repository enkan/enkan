package enkan.throttling;

import enkan.web.data.HttpRequest;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * @author kawasima
 */
public class Throttle {
    private static final int DEFAULT_MAX_BUCKETS = 10_000;

    private final Function<HttpRequest, Object> discriminateFn;
    private final String name;
    private final LimitRate limitRate;
    private final Map<Object, TokenBucket> buckets;

    public Throttle(String name, LimitRate limitRate, Function<HttpRequest, Object> discriminateFn) {
        this(name, limitRate, discriminateFn, DEFAULT_MAX_BUCKETS);
    }

    public Throttle(String name, LimitRate limitRate, Function<HttpRequest, Object> discriminateFn, int maxBuckets) {
        this.name = name;
        this.limitRate = limitRate;
        this.discriminateFn = discriminateFn;
        this.buckets = Collections.synchronizedMap(
                new LinkedHashMap<>(maxBuckets, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<Object, TokenBucket> eldest) {
                        return size() > maxBuckets;
                    }
                }
        );
    }

    public boolean apply(HttpRequest request) {
        Object discriminator = discriminateFn.apply(request);
        if (discriminator == null) return false;

        TokenBucket bucket;
        synchronized (buckets) {
            bucket = buckets.computeIfAbsent(discriminator, key ->
                    new TokenBucket(limitRate.max(), limitRate.max(),
                            new RefillStrategy(limitRate.max(), limitRate.duration()),
                            () -> {
                                boolean interrupted = false;
                                try {
                                    TimeUnit.NANOSECONDS.sleep(1);
                                } catch (InterruptedException e) {
                                    interrupted = true;
                                } finally {
                                    if (interrupted) Thread.currentThread().interrupt();
                                }
                            })
            );
        }
        return !bucket.tryConsume(1);
    }

    public String getName() {
        return name;
    }
}
