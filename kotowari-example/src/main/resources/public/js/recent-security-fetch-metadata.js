(() => {
  const script = document.currentScript;
  const apiPath = script ? script.dataset.apiPath : null;
  const result = document.getElementById("fetch-result");
  const button = document.getElementById("fetch-btn");

  if (!apiPath || !result || !button) return;

  button.addEventListener("click", async () => {
    const res = await fetch(apiPath);
    const text = await res.text();
    result.textContent = `status=${res.status}\n${text}`;
  });
})();
