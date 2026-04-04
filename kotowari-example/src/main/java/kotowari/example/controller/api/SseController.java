package kotowari.example.controller.api;

import enkan.component.JobExecutor;
import enkan.web.data.HttpResponse;
import enkan.web.data.SseEmitter;
import enkan.web.data.SseEvent;

import jakarta.inject.Inject;
import java.time.Duration;

/**
 * Example SSE controller demonstrating Server-Sent Events with JobExecutor.
 *
 * <p>Connect with: {@code curl -N http://localhost:3000/api/sse/countdown}</p>
 */
public class SseController {

    @Inject
    private JobExecutor<?> jobExecutor;

    /**
     * Streams a countdown from 5 to 0 as SSE events.
     */
    public HttpResponse countdown() {
        SseEmitter emitter = new SseEmitter();
        jobExecutor.submit(() -> {
            try {
                for (int i = 5; i >= 0; i--) {
                    emitter.send(SseEvent.builder()
                            .event("countdown")
                            .data(String.valueOf(i))
                            .id(String.valueOf(5 - i))
                            .build());
                    Thread.sleep(1000);
                }
                emitter.send(SseEvent.of("done"));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                emitter.complete();
            }
        });
        HttpResponse response = HttpResponse.of(emitter);
        response.setContentType("text/event-stream");
        response.getHeaders().put("Cache-Control", "no-cache");
        return response;
    }

    /**
     * Streams periodic keep-alive events, useful for testing long-lived connections.
     * Sends 10 ticks then completes.
     */
    public HttpResponse tick() {
        SseEmitter emitter = new SseEmitter(4);
        jobExecutor.submit(() -> {
            try {
                for (int i = 1; i <= 10; i++) {
                    emitter.send(SseEvent.builder()
                            .event("tick")
                            .data("{\"seq\":" + i + ",\"ts\":" + System.currentTimeMillis() + "}")
                            .id(String.valueOf(i))
                            .retry(Duration.ofSeconds(3))
                            .build());
                    Thread.sleep(500);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                emitter.complete();
            }
        });
        HttpResponse response = HttpResponse.of(emitter);
        response.setContentType("text/event-stream");
        response.getHeaders().put("Cache-Control", "no-cache");
        return response;
    }
}
