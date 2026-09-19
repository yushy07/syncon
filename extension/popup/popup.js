const C = SyncOnCore;

function renderDomains(totals, domains) {
  const container = document.querySelector("#domains");
  const entries = Object.entries(totals || {}).sort((a, b) => b[1] - a[1]).slice(0, 5);
  if (!entries.length) {
    container.innerHTML = '<div class="empty">Your focused browsing time will appear here.</div>';
    return;
  }
  container.innerHTML = entries.map(([domain, duration]) => `
    <div class="domain-row">
      <span class="domain-icon">${domain[0]}</span>
      <div><div class="domain-name">${domain}</div><small class="muted">${domains[domain]?.category || "Other"}</small></div>
      <strong>${C.formatDuration(duration)}</strong>
    </div>`).join("");
}

async function render() {
  const live = await chrome.runtime.sendMessage({ type: "GET_LIVE_STATE" });
  const today = C.usageDate();
  const totals = { ...(live.data.dailyTotals[today] || {}) };
  if (live.active.domain && live.active.startedAt) {
    totals[live.active.domain] = (totals[live.active.domain] || 0) + Date.now() - live.active.startedAt;
  }
  const total = Object.values(totals).reduce((sum, value) => sum + value, 0);
  document.querySelector("#total").textContent = C.formatDuration(total);
  document.querySelector("#dayLabel").textContent = today;
  document.querySelector("#current").textContent = live.active.domain
    ? `Focused on ${live.active.domain}`
    : live.isIdle ? "Paused while your computer is idle" : "No trackable website is active";
  const enabled = live.data.settings.trackingEnabled !== false;
  document.querySelector("#status").innerHTML = `<i class="status-dot ${enabled ? "" : "paused"}"></i><span>${enabled ? "Tracking" : "Paused"}</span>`;
  document.querySelector("#toggle").textContent = enabled ? "Pause tracking" : "Resume tracking";
  document.querySelector("#toggle").dataset.enabled = String(enabled);
  renderDomains(totals, live.data.domains);
}

document.querySelector("#toggle").addEventListener("click", async event => {
  const enabled = event.currentTarget.dataset.enabled === "true";
  await chrome.runtime.sendMessage({ type: "SET_TRACKING", enabled: !enabled });
  await render();
});
document.querySelector("#dashboard").addEventListener("click", () => chrome.runtime.openOptionsPage());
render();
