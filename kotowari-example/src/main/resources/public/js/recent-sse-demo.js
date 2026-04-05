(() => {
  function setupStream(btnId, stopBtnId, resultId, url) {
    const btn = document.getElementById(btnId);
    const stopBtn = document.getElementById(stopBtnId);
    const result = document.getElementById(resultId);
    if (!btn || !stopBtn || !result) return;

    let es = null;

    btn.addEventListener('click', function () {
      if (es) { es.close(); }
      result.textContent = 'Connecting...\n';
      btn.disabled = true;
      stopBtn.disabled = false;

      es = new EventSource(url);

      es.addEventListener('message', function (e) {
        result.textContent += 'data: ' + e.data + '\n';
      });

      // named event listeners (countdown, tick, done)
      ['countdown', 'tick', 'done'].forEach(function (name) {
        es.addEventListener(name, function (e) {
          result.textContent += '[' + name + '] ' + e.data + '\n';
          if (name === 'done') {
            es.close();
            es = null;
            btn.disabled = false;
            stopBtn.disabled = true;
          }
        });
      });

      es.onerror = function () {
        // server closed the connection after completion — EventSource reports error
        if (es && es.readyState === EventSource.CLOSED) {
          result.textContent += '(stream closed)\n';
        }
        if (es) { es.close(); es = null; }
        btn.disabled = false;
        stopBtn.disabled = true;
      };
    });

    stopBtn.addEventListener('click', function () {
      if (es) { es.close(); es = null; }
      result.textContent += '(stopped by user)\n';
      btn.disabled = false;
      stopBtn.disabled = true;
    });
  }

  setupStream('countdown-btn', 'countdown-stop-btn', 'countdown-result', '/api/sse/countdown');
  setupStream('tick-btn', 'tick-stop-btn', 'tick-result', '/api/sse/tick');
})();
