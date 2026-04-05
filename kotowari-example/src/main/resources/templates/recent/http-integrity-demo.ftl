<#import "/layout/defaultLayout.ftl" as layout>
<@layout.layout "HTTP Integrity Demo">
  <h1>HTTP Integrity Demo (RFC 9530 + RFC 9421)</h1>
  <p>This page demonstrates two complementary RFCs for HTTP message integrity:</p>
  <ul>
    <li><strong>RFC 9530 — Digest Fields:</strong> the <code>Content-Digest</code> header carries a hash of the request body,
        verified by <code>DigestValidationMiddleware</code>.</li>
    <li><strong>RFC 9421 — HTTP Message Signatures:</strong> the <code>Signature</code> and <code>Signature-Input</code> headers
        carry a cryptographic signature over selected request components (<code>@method</code>, <code>@path</code>,
        <code>content-digest</code>), verified by <code>SignatureVerificationMiddleware</code>.</li>
  </ul>

  <h3>Step 1 — Fetch a signed sample request</h3>
  <p>Click the button to ask the server to build a valid signed request and return the headers and curl command:</p>
  <button id="sample-btn" class="btn btn-default">Get Sample Headers</button>
  <pre id="sample-result" style="margin-top: 12px; white-space: pre-wrap; word-break: break-all;">Click "Get Sample Headers" to generate a signed request.</pre>

  <h3>Step 2 — Send the signed request</h3>
  <p>Click <strong>Send Signed Request</strong> to POST the sample payload to <code>/api/http-integrity/verify</code>
     with the correct <code>Content-Digest</code>, <code>Signature-Input</code>, and <code>Signature</code> headers.
     The server verifies both the digest and the signature.</p>
  <button id="verify-btn" class="btn btn-primary" disabled>Send Signed Request</button>
  <pre id="verify-result" style="margin-top: 12px; white-space: pre-wrap; word-break: break-all;">Get sample headers first.</pre>

  <h3>Try with curl</h3>
  <p>The full curl command (generated server-side using the same key) appears here after fetching the sample:</p>
  <pre id="curl-cmd" style="white-space: pre-wrap; word-break: break-all;">Run Step 1 to get the curl command.</pre>

  <h3>Expected</h3>
  <ul>
    <li><code>POST /api/http-integrity/verify</code> with correct headers returns <code>200</code> and lists the verified signature(s).</li>
    <li>Modifying the body or any signed header causes the middleware to reject the request with <code>400</code> or <code>401</code>.</li>
    <li>The <code>Content-Digest</code> is checked first (body integrity), then the signature (authenticity).</li>
  </ul>

  <script>
    var sampleData = null;

    document.getElementById('sample-btn').addEventListener('click', function () {
      document.getElementById('sample-result').textContent = 'Fetching sample...';
      fetch('/api/http-integrity/sample')
        .then(function(r) { return r.json(); })
        .then(function(data) {
          sampleData = data;
          var headersText = Object.entries(data.headers || {})
            .map(function(e) { return e[0] + ': ' + e[1]; })
            .join('\n');
          document.getElementById('sample-result').textContent =
            'Algorithm: ' + data.algorithm +
            '\nKey ID: ' + data.keyId +
            '\nCovered Components: ' + (data.coveredComponents || []).join(', ') +
            '\nPayload: ' + data.payload +
            '\n\nHeaders:\n' + headersText;
          document.getElementById('verify-btn').disabled = false;
          document.getElementById('curl-cmd').textContent = data.curl || '(no curl command returned)';
        })
        .catch(function(err) {
          document.getElementById('sample-result').textContent = 'Error: ' + err;
        });
    });

    document.getElementById('verify-btn').addEventListener('click', function () {
      if (!sampleData) return;
      document.getElementById('verify-result').textContent = 'Sending signed request...';

      var headers = Object.assign({}, sampleData.headers || {});

      fetch('/api/http-integrity/verify', {
        method: 'POST',
        headers: headers,
        body: sampleData.payload
      })
        .then(function(r) { return r.json().then(function(b) { return { status: r.status, body: b }; }); })
        .then(function(res) {
          document.getElementById('verify-result').textContent =
            'HTTP ' + res.status + '\n' + JSON.stringify(res.body, null, 2);
        })
        .catch(function(err) {
          document.getElementById('verify-result').textContent = 'Error: ' + err;
        });
    });
  </script>
</@layout.layout>
