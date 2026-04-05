<#import "/layout/defaultLayout.ftl" as layout>
<@layout.layout "Fetch Metadata Demo">
  <h1>Fetch Metadata Demo</h1>
  <p>このデモでは <code>FetchMetadataMiddleware</code> を
    <code>${apiPath}</code> にのみ適用しています（allow-list なし）。</p>

  <h3>Observed Headers On This Page Request</h3>
  <pre><#list observed as key, value>${key}: <#if value??>${value}<#else>-</#if>
</#list></pre>

  <h3>Try (Same-origin browser request)</h3>
  <button id="fetch-btn" class="btn btn-primary">Call protected API</button>
  <pre id="fetch-result" style="margin-top: 12px;">No request yet.</pre>

  <script src="/assets/js/recent-security-fetch-metadata.js" data-api-path="${apiPath}"></script>

  <h3>Cross-site Reproduction (curl)</h3>
  <pre>curl -i "http://localhost:3000${apiPath}" \
  -H "Sec-Fetch-Site: cross-site" \
  -H "Sec-Fetch-Mode: cors"</pre>

  <h3>Expected</h3>
  <ul>
    <li>Sec-Fetch ヘッダなし: 200（後方互換）</li>
    <li><code>Sec-Fetch-Site: cross-site</code> + 非 navigate: 403</li>
  </ul>
</@layout.layout>
