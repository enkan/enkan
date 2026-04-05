(() => {
  let sampleData = null;

  const sampleBtn = document.getElementById('sample-btn');
  const verifyBtn = document.getElementById('verify-btn');
  const sampleResult = document.getElementById('sample-result');
  const verifyResult = document.getElementById('verify-result');
  const curlCmd = document.getElementById('curl-cmd');

  if (!sampleBtn || !verifyBtn) return;

  sampleBtn.addEventListener('click', function () {
    sampleResult.textContent = 'Fetching sample...';

    fetch('/api/http-integrity/sample')
      .then(function (r) { return r.json(); })
      .then(function (data) {
        sampleData = data;
        const headersText = Object.entries(data.headers || {})
          .map(function (e) { return e[0] + ': ' + e[1]; })
          .join('\n');
        sampleResult.textContent =
          'Algorithm: ' + data.algorithm +
          '\nKey ID: ' + data.keyId +
          '\nCovered Components: ' + (data.coveredComponents || []).join(', ') +
          '\nPayload: ' + data.payload +
          '\n\nHeaders:\n' + headersText;
        verifyBtn.disabled = false;
        curlCmd.textContent = data.curl || '(no curl command returned)';
      })
      .catch(function (err) {
        sampleResult.textContent = 'Error: ' + err;
      });
  });

  verifyBtn.addEventListener('click', function () {
    if (!sampleData) return;
    verifyResult.textContent = 'Sending signed request...';

    fetch('/api/http-integrity/verify', {
      method: 'POST',
      headers: Object.assign({}, sampleData.headers || {}),
      body: sampleData.payload
    })
      .then(function (r) { return r.json().then(function (b) { return { status: r.status, body: b }; }); })
      .then(function (res) {
        verifyResult.textContent = 'HTTP ' + res.status + '\n' + JSON.stringify(res.body, null, 2);
      })
      .catch(function (err) {
        verifyResult.textContent = 'Error: ' + err;
      });
  });
})();
