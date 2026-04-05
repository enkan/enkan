<#import "/layout/defaultLayout.ftl" as layout>
<@layout.layout "Idempotency-Key Demo">
  <h1>Idempotency-Key Demo</h1>
  <p>This page demonstrates the <code>IdempotencyKeyMiddleware</code>.
     The middleware caches POST responses keyed by the <code>Idempotency-Key</code> request header
     (an SF string per RFC 8941, so the value must be quoted).
     Repeating a POST with the same key returns the <em>exact same cached response</em> without re-processing.</p>

  <div class="panel panel-default">
    <div class="panel-heading"><strong>Demo Details</strong></div>
    <div class="panel-body">
      <p><strong>Endpoint:</strong> <code>POST /api/recent/idempotency/echo</code></p>
      <p><strong>Fixed Idempotency-Key:</strong> <code>"demo-key-1"</code> (quoted SF string)</p>
      <p><strong>Body:</strong> <code>{"orderId":"A-1001","amount":1200}</code></p>
    </div>
  </div>

  <h3>Interactive Test</h3>
  <p>Each click sends the same request with the same key.
     The <code>id</code> and <code>processedAt</code> fields in the response will be
     <strong>identical</strong> after the first request — proof that the cached response is replayed.</p>
  <button id="send-btn" class="btn btn-primary">Send POST</button>
  <button id="reset-btn" class="btn btn-default" style="margin-left: 8px;">Reset (new key)</button>
  <p id="request-count" style="margin-top: 8px; color: #888;">Requests sent: 0</p>
  <pre id="idempotency-result" style="margin-top: 4px; white-space: pre-wrap;">Click "Send POST" to start.</pre>

  <h3>Try with curl</h3>
  <p>Run the first command, then run it again. The response — including the random <code>id</code> — will be identical:</p>
  <pre id="curl-first">curl -i -X POST "http://localhost:3000/api/recent/idempotency/echo" \
  -H "Content-Type: application/json" \
  -H 'Idempotency-Key: "demo-key-1"' \
  --data-binary '{"orderId":"A-1001","amount":1200}'</pre>

  <h3>Expected</h3>
  <ul>
    <li>First request: server processes the body, generates a random UUID, and returns <code>200</code>.</li>
    <li>Subsequent requests with the same key: middleware short-circuits and returns the cached <code>200</code> — same UUID, same <code>processedAt</code>.</li>
    <li>Using a different <code>Idempotency-Key</code> value produces a new response.</li>
  </ul>

  <script src="/assets/js/recent-idempotency-demo.js"></script>
</@layout.layout>
