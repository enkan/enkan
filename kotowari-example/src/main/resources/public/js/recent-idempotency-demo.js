(() => {
  let requestCount = 0;
  let firstResponse = null;
  let currentKey = '"demo-key-1"';

  const sendBtn = document.getElementById('send-btn');
  const resetBtn = document.getElementById('reset-btn');
  const countEl = document.getElementById('request-count');
  const resultEl = document.getElementById('idempotency-result');
  const curlEl = document.getElementById('curl-first');

  if (!sendBtn || !resetBtn) return;

  function sendRequest() {
    requestCount++;
    countEl.textContent = 'Requests sent: ' + requestCount;
    resultEl.textContent = 'Sending request #' + requestCount + ' ...';

    fetch('/api/recent/idempotency/echo', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Idempotency-Key': currentKey
      },
      body: JSON.stringify({ orderId: 'A-1001', amount: 1200 })
    })
      .then(function (r) { return r.json().then(function (b) { return { body: b }; }); })
      .then(function (res) {
        let note;
        if (requestCount === 1) {
          firstResponse = res.body;
          note = '\n\n[First request — response generated fresh and cached.]';
        } else {
          const same = firstResponse && res.body.id === firstResponse.id;
          note = '\n\n[Request #' + requestCount + ' — response is ' +
            (same ? 'CACHED (same id as first request).' : 'different (new key was used).') + ']';
        }
        resultEl.textContent = JSON.stringify(res.body, null, 2) + note;
      })
      .catch(function (err) {
        resultEl.textContent = 'Error: ' + err;
      });
  }

  sendBtn.addEventListener('click', sendRequest);

  resetBtn.addEventListener('click', function () {
    requestCount = 0;
    firstResponse = null;
    currentKey = '"demo-key-' + Date.now() + '"';
    countEl.textContent = 'Requests sent: 0 (new key: ' + currentKey + ')';
    resultEl.textContent = 'Key reset. Click "Send POST" to start fresh.';
    curlEl.textContent =
      'curl -i -X POST "http://localhost:3000/api/recent/idempotency/echo" \\\n' +
      '  -H "Content-Type: application/json" \\\n' +
      "  -H 'Idempotency-Key: " + currentKey + "' \\\n" +
      "  --data-binary '{\"orderId\":\"A-1001\",\"amount\":1200}'";
  });
})();
