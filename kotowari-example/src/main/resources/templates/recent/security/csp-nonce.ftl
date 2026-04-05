<#import "layout/defaultLayout.ftl" as layout>
<@layout.layout "CSP Nonce Demo">
  <h1>CSP Nonce Demo</h1>
  <p>このページは <code>CspNonceMiddleware</code> を <code>/recent/security/csp-nonce</code> のみに適用しています。</p>

  <h3>Observed Nonce</h3>
  <p><code>${nonce!""}</code></p>

  <h3>Inline Script Check</h3>
  <p id="nonce-status">Inline script not executed yet.</p>
  <script nonce="${nonce!""}">
    document.getElementById("nonce-status").textContent =
      "Inline script executed with nonce: ${nonce!""}";
  </script>

  <h3>Try</h3>
  <ol>
    <li>開発者ツールでレスポンスヘッダ <code>Content-Security-Policy</code> を確認</li>
    <li><code>script-src</code> に <code>'nonce-${nonce!""}'</code> が含まれることを確認</li>
    <li>上記ステータスが更新されることを確認</li>
  </ol>

  <h3>Expected</h3>
  <ul>
    <li>HTTP 200</li>
    <li>CSP ヘッダに nonce が付与される</li>
    <li>nonce付き inline script は実行される</li>
  </ul>
</@layout.layout>
