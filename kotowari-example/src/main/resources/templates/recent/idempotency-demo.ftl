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

  <script>
    var requestCount = 0;
    var firstResponse = null;
    var currentKey = '"demo-key-1"';

    function sendRequest() {
      requestCount++;
      document.getElementById('request-count').textContent = 'Requests sent: ' + requestCount;
      document.getElementById('idempotency-result').textContent = 'Sending request #' + requestCount + ' ...';

      fetch('/api/recent/idempotency/echo', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Idempotency-Key': currentKey
        },
        body: JSON.stringify({orderId: 'A-1001', amount: 1200})
      })
        .then(function(r) {
          var cached = r.headers.get('Idempotency-Key-Replayed') === 'true';
          return r.json().then(function(b) { return { body: b, cached: cached }; });
        })
        .then(function(res) {
          var note = '';
          if (requestCount === 1) {
            firstResponse = res.body;
            note = '\n\n[First request — response generated fresh and cached.]';
          } else {
            var same = firstResponse && res.body.id === firstResponse.id;
            note = '\n\n[Request #' + requestCount + ' — response is ' +
              (same ? 'CACHED (same id as first request).' : 'different (new key was used).') + ']';
          }
          document.getElementById('idempotency-result').textContent =
            JSON.stringify(res.body, null, 2) + note;
        })
        .catch(function(err) {
          document.getElementById('idempotency-result').textContent = 'Error: ' + err;
        });
    }

    document.getElementById('send-btn').addEventListener('click', sendRequest);

    document.getElementById('reset-btn').addEventListener('click', function() {
      requestCount = 0;
      firstResponse = null;
      var ts = Date.now();
      currentKey = '"demo-key-' + ts + '"';
      document.getElementById('request-count').textContent = 'Requests sent: 0 (new key: ' + currentKey + ')';
      document.getElementById('idempotency-result').textContent = 'Key reset. Click "Send POST" to start fresh.';
      document.getElementById('curl-first').textContent =
        'curl -i -X POST "http://localhost:3000/api/recent/idempotency/echo" \\\n' +
        '  -H "Content-Type: application/json" \\\n' +
        "  -H 'Idempotency-Key: " + currentKey + "' \\\n" +
        "  --data-binary '{\"orderId\":\"A-1001\",\"amount\":1200}'";
    });
  </script>
</@layout.layout>
