(() => {
  let currentToken = null;

  const issueBtn = document.getElementById('issue-btn');
  const verifyBtn = document.getElementById('verify-btn');
  const subInput = document.getElementById('sub-input');
  const issueResult = document.getElementById('issue-result');
  const verifyResult = document.getElementById('verify-result');
  const curlIssue = document.getElementById('curl-issue');
  const curlVerify = document.getElementById('curl-verify');

  if (!issueBtn || !verifyBtn) return;

  issueBtn.addEventListener('click', function () {
    const sub = (subInput ? subInput.value.trim() : '') || 'alice';
    issueResult.textContent = 'Issuing token for sub=' + sub + ' ...';

    fetch('/api/recent/jwt/issue?sub=' + encodeURIComponent(sub))
      .then(function (r) { return r.json(); })
      .then(function (data) {
        currentToken = data.token;
        issueResult.textContent =
          'Token:\n' + data.token +
          '\n\nDecoded Claims:\n' + JSON.stringify(data.claims || {}, null, 2);
        verifyBtn.disabled = false;
        curlIssue.textContent =
          'curl "http://localhost:3000/api/recent/jwt/issue?sub=' + encodeURIComponent(sub) + '"';
        curlVerify.textContent =
          'curl -i "http://localhost:3000/api/recent/jwt/verify" \\\n' +
          '  -H "Authorization: Bearer ' + data.token + '"';
      })
      .catch(function (err) {
        issueResult.textContent = 'Error: ' + err;
      });
  });

  verifyBtn.addEventListener('click', function () {
    if (!currentToken) return;
    verifyResult.textContent = 'Verifying ...';

    fetch('/api/recent/jwt/verify', {
      headers: { 'Authorization': 'Bearer ' + currentToken }
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
