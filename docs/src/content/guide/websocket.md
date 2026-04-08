type=page
status=published
title=WebSocket & SSE | Enkan
~~~~~~

# Real-time: WebSocket & Server-Sent Events

Enkan supports two push-communication patterns built on virtual threads:
**WebSocket** for full-duplex messaging, and **Server-Sent Events (SSE)** for
server-to-client streaming.

---

## WebSocket

WebSocket support is provided by `enkan-component-jetty`. Handlers are
registered on `JettyComponent` before the server starts.

### Implement a handler

Implement the `WebSocketHandler` interface:

```java
import enkan.web.websocket.WebSocketHandler;
import enkan.web.websocket.WebSocketSession;

public class ChatHandler implements WebSocketHandler {

    @Override
    public void onOpen(WebSocketSession session) {
        // Called when the client connects
        session.sendText("Welcome!");
    }

    @Override
    public void onMessage(WebSocketSession session, String message) {
        // Echo back
        session.sendText("Echo: " + message);
    }

    @Override
    public void onClose(WebSocketSession session, int statusCode, String reason) {
        // RFC 6455 status code, e.g. 1000 = normal close
    }

    @Override
    public void onError(WebSocketSession session, Throwable cause) {
        // Handle errors; session may or may not still be open
    }
}
```

Binary messages can be handled by overriding the default `onBinary` method:

```java
@Override
public void onBinary(WebSocketSession session, java.nio.ByteBuffer data) {
    // process binary frame
}
```

### Register on JettyComponent

```java
import enkan.component.jetty.JettyComponent;

JettyComponent jetty = new JettyComponent()
    .addWebSocket("/ws/chat", new ChatHandler())
    .addWebSocket("/ws/notify", new NotifyHandler());
```

`addWebSocket` must be called **before** the component starts. It is chainable
and returns `this`. Calling it after start throws `MisconfigurationException`.

Path syntax follows Jetty's path-mapping rules:
- `/ws/chat` — exact match
- `/ws/*` — prefix wildcard

### WebSocketSession API

| Method | Description |
|--------|-------------|
| `getId()` | Unique session identifier |
| `isOpen()` | Whether the connection is still open |
| `sendText(String)` | Send a UTF-8 text frame (async) |
| `sendBinary(ByteBuffer)` | Send a binary frame (async; buffer is defensively copied) |
| `close()` | Normal close (status 1000) |
| `close(int, String)` | Close with RFC 6455 status and reason |

### Broadcasting to multiple clients

Maintain your own session registry:

```java
public class ChatHandler implements WebSocketHandler {
    private final Set<WebSocketSession> sessions =
        Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Override
    public void onOpen(WebSocketSession session) {
        sessions.add(session);
    }

    @Override
    public void onMessage(WebSocketSession session, String message) {
        sessions.stream()
            .filter(WebSocketSession::isOpen)
            .forEach(s -> s.sendText(message));
    }

    @Override
    public void onClose(WebSocketSession session, int code, String reason) {
        sessions.remove(session);
    }

    @Override
    public void onError(WebSocketSession session, Throwable cause) {
        sessions.remove(session);
    }
}
```

---

## Server-Sent Events (SSE)

SSE is a standard HTTP streaming protocol where the server pushes events to the
client over a single long-lived connection. It requires no special component —
any server (Jetty, Undertow) works.

### Core types

| Type | Role |
|------|------|
| `SseEmitter` | Bridge between event producer and the HTTP connection |
| `SseEvent` | Immutable record representing a single SSE frame |
| `JobExecutor` | Background executor (virtual threads) for event producers |

### Register the route

```java
Routes routes = Routes.define(r -> {
    r.get("/events").to(EventController.class, "stream");
}).compile();
```

### Write a streaming controller

```java
import enkan.web.data.SseEmitter;
import enkan.web.data.SseEvent;
import enkan.component.JobExecutor;
import enkan.data.HttpResponse;
import enkan.web.collection.Headers;

import jakarta.inject.Inject;
import java.time.Duration;

public class EventController {

    @Inject
    private JobExecutor jobExecutor;

    public HttpResponse stream() {
        // Bounded queue — natural back-pressure for slow clients
        SseEmitter emitter = new SseEmitter(100);

        jobExecutor.submit(() -> {
            try {
                for (int i = 0; i < 10; i++) {
                    emitter.send(SseEvent.builder()
                        .event("update")
                        .data("{\"count\":" + i + "}")
                        .id("msg-" + i)
                        .build());
                    Thread.sleep(1000);
                }
            } finally {
                emitter.complete(); // Always call complete() — even on exception
            }
        });

        return HttpResponse.of(emitter)
            .setHeaders(Headers.of(
                "Content-Type", "text/event-stream",
                "Cache-Control", "no-cache",
                "X-Accel-Buffering", "no"  // Disable nginx proxy buffering
            ));
    }
}
```

### SseEvent construction

```java
// Simple data-only event
SseEvent.of("Hello, world!");

// Full event
SseEvent.builder()
    .data("{\"key\":\"value\"}")  // required
    .event("update")              // optional; omit for default "message" type
    .id("event-42")               // optional; used by client for reconnection
    .retry(Duration.ofSeconds(5)) // optional; reconnection delay hint
    .build();

// Keep-alive comment (prevents proxy timeouts)
SseEvent.keepAlive();
```

### SseEmitter sizing

| Constructor | Behaviour |
|-------------|-----------|
| `new SseEmitter()` | Unbounded queue — risk of memory growth if client is slow |
| `new SseEmitter(int capacity)` | Bounded queue — `send()` blocks when full (back-pressure) |

Always call `emitter.complete()` in a `finally` block. If `complete()` is never
called the virtual thread running `writeTo()` blocks until the executor shuts down.

### Wire up JobExecutor

`JobExecutor` is an `enkan-system` component. The default implementation
`LocalJobExecutor` uses virtual threads.

```java
// SystemFactory
EnkanSystem system = EnkanSystem.of(
    "jobExecutor", new LocalJobExecutor(),
    "app",         new ApplicationComponent(AppFactory.class.getName()),
    "http",        new JettyComponent()
).relationships(
    component("app").using("jobExecutor"),
    component("http").using("app")
);
```

```java
// ApplicationFactory — inject into controllers
app.use(new ControllerInvokerMiddleware(system));
```

```java
// Controller
public class EventController {
    @Inject
    private JobExecutor jobExecutor;
    ...
}
```

`LocalJobExecutor` configuration:

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `name` | `String` | `"enkan-job"` | Thread name prefix |
| `shutdownTimeoutMs` | `long` | `30000` | Graceful shutdown wait (ms) |

### Keep-alive for long idle streams

Some proxies close connections that carry no data for 30–60 seconds. Send a
periodic keep-alive comment:

```java
jobExecutor.submit(() -> {
    try {
        while (!done()) {
            Object event = queue.poll(20, TimeUnit.SECONDS);
            if (event == null) {
                emitter.send(SseEvent.keepAlive());
            } else {
                emitter.send(toSseEvent(event));
            }
        }
    } finally {
        emitter.complete();
    }
});
```

---

## Choosing between WebSocket and SSE

| | WebSocket | SSE |
|---|---|---|
| Direction | Full-duplex | Server → Client only |
| Protocol | WS / WSS | Plain HTTP |
| Reconnection | Manual | Built into browser |
| Proxy support | Requires upgrade | Works everywhere |
| Use case | Chat, games, collaborative editing | Dashboards, notifications, progress bars |
