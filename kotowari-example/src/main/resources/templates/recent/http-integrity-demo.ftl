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

  <script src="/assets/js/recent-http-integrity-demo.js"></script>
</@layout.layout>
