(() => {
  const script = document.currentScript;
  const apiPath = script ? script.dataset.apiPath : null;
  const result = document.getElementById("timeout-result");
  const okBtn = document.getElementById("ok-btn");
  const timeoutBtn = document.getElementById("timeout-btn");

  if (!apiPath || !result || !okBtn || !timeoutBtn) return;

  async function run(delayMs) {
    const res = await fetch(`${apiPath}?delayMs=${delayMs}`);
    const text = await res.text();
    result.textContent = `status=${res.status}\n${text}`;
  }

  okBtn.addEventListener("click", () => run(50));
  timeoutBtn.addEventListener("click", () => run(1000));
})();
