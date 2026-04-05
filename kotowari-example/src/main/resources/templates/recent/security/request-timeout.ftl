<#import "layout/defaultLayout.ftl" as layout>
<@layout.layout "Request Timeout Demo">
  <h1>Request Timeout Demo</h1>
  <p>このデモでは <code>RequestTimeoutMiddleware</code> を
    <code>/api/recent/security/timeout-echo</code> のみに適用し、timeout を <code>${timeoutMs}</code>ms に設定しています。</p>

  <h3>API</h3>
  <p><code>${apiPath}</code></p>

  <h3>Try (curl)</h3>
  <pre>curl -i "http://localhost:3000${apiPath}?delayMs=50"</pre>
  <pre>curl -i "http://localhost:3000${apiPath}?delayMs=1000"</pre>

  <h3>Browser Quick Test</h3>
  <button id="ok-btn" class="btn btn-primary">delayMs=50 (expect 200)</button>
  <button id="timeout-btn" class="btn btn-danger">delayMs=1000 (expect 504)</button>
  <pre id="timeout-result" style="margin-top: 12px;">No request yet.</pre>

  <script src="/js/recent-security-request-timeout.js" data-api-path="${apiPath}"></script>

  <h3>Expected</h3>
  <ul>
    <li><code>delayMs=50</code> は 200</li>
    <li><code>delayMs=1000</code> は 504 Gateway Timeout</li>
  </ul>
</@layout.layout>
