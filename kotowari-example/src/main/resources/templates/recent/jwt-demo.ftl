<#import "/layout/defaultLayout.ftl" as layout>
<@layout.layout "JWT Demo">
  <h1>JWT Demo (HS256)</h1>
  <p>This page demonstrates the <code>JwtProcessor</code> built into Enkan.
     Enter a subject name, click <strong>Issue Token</strong>, and the server will sign a JWT using HMAC-SHA256.
     The decoded header and claims are displayed below. You can then verify the token inline or via <code>curl</code>.</p>

  <h3>Issue a Token</h3>
  <div class="form-inline" style="margin-bottom: 12px;">
    <label for="sub-input" class="sr-only">Subject (sub)</label>
    <input id="sub-input" type="text" class="form-control" placeholder="Subject (e.g. alice)" value="alice" style="margin-right: 8px; width: 220px;">
    <button id="issue-btn" class="btn btn-primary">Issue Token</button>
  </div>
  <pre id="issue-result" style="margin-top: 12px; white-space: pre-wrap; word-break: break-all;">Click "Issue Token" to get started.</pre>

  <h3>Verify Token</h3>
  <p>After issuing, click <strong>Verify</strong> to send the token to <code>/api/recent/jwt/verify</code>
     with an <code>Authorization: Bearer &lt;token&gt;</code> header.</p>
  <button id="verify-btn" class="btn btn-success" disabled>Verify</button>
  <pre id="verify-result" style="margin-top: 12px; white-space: pre-wrap; word-break: break-all;">No verification attempt yet.</pre>

  <h3>Try with curl</h3>
  <p>After issuing a token, the equivalent curl commands appear here:</p>
  <pre id="curl-issue">curl "http://localhost:3000/api/recent/jwt/issue?sub=alice"</pre>
  <pre id="curl-verify" style="white-space: pre-wrap; word-break: break-all;">Run the issue command first to get a token.</pre>

  <h3>Expected</h3>
  <ul>
    <li>Issue returns a signed JWT with <code>sub</code>, <code>scope</code>, <code>iat</code>, <code>exp</code> claims.</li>
    <li>Verify returns <code>"ok": true</code> and the decoded claims when the token is valid.</li>
    <li>Verify returns <code>401</code> when the token is missing, tampered, or expired (tokens expire in 5 minutes).</li>
  </ul>

  <script src="/assets/js/recent-jwt-demo.js"></script>
</@layout.layout>
