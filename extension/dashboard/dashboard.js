const C = SyncOnCore;
const CATEGORIES = ["Social Media", "Communication", "Entertainment", "Education", "Productivity", "News", "Shopping", "Finance", "Other"];
let appState = null;
let live = null;
let trendDays = 7;
let editingDomain = null;

const $ = selector => document.querySelector(selector);
const $$ = selector => [...document.querySelectorAll(selector)];
const escapeHtml = value => String(value).replace(/[&<>'"]/g, character => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;" }[character]));

function toast(message) {
  const node = $("#toast");
  node.textContent = message;
  node.classList.add("show");
  setTimeout(() => node.classList.remove("show"), 2200);
}

function todayTotals() {
  const totals = { ...(appState.dailyTotals[C.usageDate()] || {}) };
  if (live.active.domain && live.active.startedAt) totals[live.active.domain] = (totals[live.active.domain] || 0) + Date.now() - live.active.startedAt;
  return totals;
}

function domainRows(totals, max = Infinity) {
  const entries = Object.entries(totals).sort((a, b) => b[1] - a[1]).slice(0, max);
  if (!entries.length) return '<div class="empty">No focused browsing time recorded yet.</div>';
  const maximum = Math.max(...entries.map(item => item[1]), 1);
  return entries.map(([domain, duration]) => `
    <div class="list-row">
      <span class="domain-icon">${escapeHtml(domain[0])}</span>
      <div><strong>${escapeHtml(domain)}</strong><div class="muted">${escapeHtml(appState.domains[domain]?.category || "Other")}</div></div>
      <div class="progress"><span style="width:${Math.max(3, duration / maximum * 100)}%"></span></div>
      <strong>${C.formatDuration(duration)}</strong>
    </div>`).join("");
}

function renderOverview() {
  const totals = todayTotals();
  const sorted = Object.entries(totals).sort((a, b) => b[1] - a[1]);
  const total = sorted.reduce((sum, item) => sum + item[1], 0);
  $("#todayTotal").textContent = C.formatDuration(total);
  $("#currentDomain").textContent = live.active.domain || "None";
  $("#currentDuration").textContent = live.active.startedAt ? C.formatDuration(Date.now() - live.active.startedAt) + " in this focus session" : "Waiting for activity";
  $("#topDomain").textContent = sorted[0]?.[0] || "—";
  $("#topDuration").textContent = sorted[0] ? C.formatDuration(sorted[0][1]) : "No usage yet";
  $("#todayDomains").innerHTML = domainRows(totals, 8);

  const categories = {};
  for (const [domain, duration] of sorted) {
    const category = appState.domains[domain]?.category || "Other";
    categories[category] = (categories[category] || 0) + duration;
  }
  const maximum = Math.max(...Object.values(categories), 1);
  $("#categoryBreakdown").innerHTML = Object.entries(categories).sort((a, b) => b[1] - a[1]).map(([category, duration]) => `
    <div class="category-row"><div class="row between"><strong>${escapeHtml(category)}</strong><span>${C.formatDuration(duration)}</span></div><div class="progress"><span style="width:${duration / maximum * 100}%"></span></div></div>`).join("") || '<div class="empty">Categories appear as you browse.</div>';
}

function renderWebsites() {
  const query = $("#search").value.toLowerCase();
  const category = $("#categoryFilter").value;
  const totals = todayTotals();
  const known = new Set([...Object.keys(appState.domains), ...Object.keys(totals), ...Object.keys(appState.limits)]);
  const entries = [...known].filter(domain => domain.includes(query) && (category === "All" || (appState.domains[domain]?.category || "Other") === category)).sort((a, b) => (totals[b] || 0) - (totals[a] || 0));
  $("#websiteList").innerHTML = entries.map(domain => {
    const limit = appState.limits[domain];
    const used = totals[domain] || 0;
    return `<article class="card website-card">
      <div class="row"><span class="domain-icon">${escapeHtml(domain[0])}</span><div><h3>${escapeHtml(domain)}</h3><span class="tag">${escapeHtml(appState.domains[domain]?.category || "Other")}</span></div></div>
      <div class="row between"><span class="muted">Today</span><strong>${C.formatDuration(used)}</strong></div>
      <div class="row between"><span class="muted">Limit</span><span>${limit?.enabled ? `${limit.limitMinutes}m · ${limit.style.toLowerCase()}` : "No limit"}</span></div>
      <button class="pill secondary edit-limit" data-domain="${escapeHtml(domain)}">Manage</button>
    </article>`;
  }).join("") || '<div class="empty card">No websites match this filter.</div>';
  $$(".edit-limit").forEach(button => button.addEventListener("click", () => openLimit(button.dataset.domain)));
}

function renderTrends() {
  const dates = C.recentUsageDates(trendDays);
  const totals = dates.map(date => Object.values(appState.dailyTotals[date] || {}).reduce((sum, value) => sum + value, 0));
  const max = Math.max(...totals, 1);
  $("#trendChart").innerHTML = dates.map((date, index) => `<div class="bar-wrap" title="${date}: ${C.formatDuration(totals[index])}"><div class="bar" style="height:${Math.max(2, totals[index] / max * 190)}px"></div><small>${new Date(`${date}T12:00:00`).toLocaleDateString(undefined, { weekday: "short" })}</small></div>`).join("");
  const domainTotals = {};
  dates.forEach(date => Object.entries(appState.dailyTotals[date] || {}).forEach(([domain, duration]) => { domainTotals[domain] = (domainTotals[domain] || 0) + duration; }));
  $("#trendDomains").innerHTML = domainRows(domainTotals, 10);
}

function renderSettings() {
  $("#trackingToggle").checked = appState.settings.trackingEnabled !== false;
  $("#idleTimeout").value = String(appState.settings.idleThresholdSeconds || 60);
  $("#excludedDomains").value = (appState.settings.excludedDomains || []).join("\n");
  $("#dataStats").innerHTML = `
    <div class="data-stat"><span>Precise intervals</span><strong>${appState.intervals.length}</strong></div>
    <div class="data-stat"><span>Known websites</span><strong>${Object.keys(appState.domains).length}</strong></div>
    <div class="data-stat"><span>Configured limits</span><strong>${Object.values(appState.limits).filter(item => item.enabled).length}</strong></div>`;
  $("#backendInfo").innerHTML = `
    <div class="data-stat"><span>Platform</span><strong>CHROME</strong></div>
    <div class="data-stat"><span>Schema</span><strong>1</strong></div>
    <div class="data-stat"><span>Installation</span><strong title="${escapeHtml(appState.installationId)}">${escapeHtml(appState.installationId.slice(0, 8))}…</strong></div>
    <div class="data-stat"><span>Sync state</span><strong>LOCAL_ONLY</strong></div>`;
}

function renderStatus() {
  const enabled = appState.settings.trackingEnabled !== false;
  $("#trackingStatus").innerHTML = `<i class="status-dot ${enabled ? "" : "paused"}"></i><span>${enabled ? "Tracking active" : "Tracking paused"}</span>`;
}

function renderAll() { renderStatus(); renderOverview(); renderWebsites(); renderTrends(); renderSettings(); }

function openLimit(domain) {
  editingDomain = domain;
  const limit = appState.limits[domain] || { enabled: false, limitMinutes: 60, style: "STRICT", snoozeMinutes: 5 };
  $("#limitDomain").textContent = domain;
  $("#limitEnabled").checked = limit.enabled;
  $("#limitMinutes").value = limit.limitMinutes;
  $("#limitStyle").value = limit.style;
  $("#snoozeMinutes").value = limit.snoozeMinutes;
  $("#domainCategory").value = appState.domains[domain]?.category || C.categoryForDomain(domain);
  $("#limitDialog").showModal();
}

async function load() {
  live = await chrome.runtime.sendMessage({ type: "GET_LIVE_STATE" });
  appState = live.data;
  $("#categoryFilter").innerHTML += CATEGORIES.map(category => `<option>${category}</option>`).join("");
  $("#domainCategory").innerHTML = CATEGORIES.map(category => `<option>${category}</option>`).join("");
  renderAll();
}

$$('.nav').forEach(button => button.addEventListener("click", () => {
  $$(".nav, .view").forEach(node => node.classList.remove("active"));
  button.classList.add("active");
  $(`#${button.dataset.view}`).classList.add("active");
  $("#pageTitle").textContent = button.textContent;
}));
$$('[data-go]').forEach(button => button.addEventListener("click", () => $(`.nav[data-view="${button.dataset.go}"]`).click()));
$("#search").addEventListener("input", renderWebsites);
$("#categoryFilter").addEventListener("change", renderWebsites);
$$('[data-days]').forEach(button => button.addEventListener("click", () => { $$('[data-days]').forEach(item => item.classList.remove("active")); button.classList.add("active"); trendDays = Number(button.dataset.days); renderTrends(); }));

$("#saveLimit").addEventListener("click", async event => {
  event.preventDefault();
  const limitMinutes = Number($("#limitMinutes").value);
  if (!Number.isFinite(limitMinutes) || limitMinutes < 1) return toast("Enter a valid daily limit.");
  appState.limits[editingDomain] = { enabled: $("#limitEnabled").checked, limitMinutes, style: $("#limitStyle").value, snoozeMinutes: Number($("#snoozeMinutes").value) || 5, updatedAtUtc: Date.now(), localRevision: (appState.limits[editingDomain]?.localRevision || 0) + 1, syncState: "LOCAL_ONLY" };
  appState.domains[editingDomain] ||= { displayName: editingDomain, category: "Other" };
  appState.domains[editingDomain].category = $("#domainCategory").value;
  appState.domains[editingDomain].manuallyCategorized = true;
  await chrome.storage.local.set({ limits: appState.limits, domains: appState.domains });
  $("#limitDialog").close();
  renderAll(); toast("Website controls saved.");
});

$("#saveSettings").addEventListener("click", async () => {
  appState.settings.trackingEnabled = $("#trackingToggle").checked;
  appState.settings.idleThresholdSeconds = Number($("#idleTimeout").value);
  appState.settings.excludedDomains = $("#excludedDomains").value.split(/\r?\n/).map(value => value.trim().toLowerCase()).filter(Boolean);
  await chrome.storage.local.set({ settings: appState.settings });
  chrome.idle.setDetectionInterval(appState.settings.idleThresholdSeconds);
  await chrome.runtime.sendMessage({ type: "SET_TRACKING", enabled: appState.settings.trackingEnabled });
  renderStatus(); toast("Tracking settings saved.");
});

$("#exportData").addEventListener("click", () => {
  const payload = { kind: "SYNCON_EXTENSION_BACKUP", schemaVersion: 1, exportedAtUtc: Date.now(), sourcePlatform: "CHROME", sourceInstallationId: appState.installationId, data: { intervals: appState.intervals, dailyTotals: appState.dailyTotals, domains: appState.domains, limits: appState.limits, dailyStates: appState.dailyStates, settings: appState.settings } };
  const url = URL.createObjectURL(new Blob([JSON.stringify(payload, null, 2)], { type: "application/json" }));
  const anchor = document.createElement("a"); anchor.href = url; anchor.download = `syncon-chrome-${C.usageDate()}.json`; anchor.click(); URL.revokeObjectURL(url);
});

$("#importData").addEventListener("change", async event => {
  try {
    const payload = JSON.parse(await event.target.files[0].text());
    if (payload.kind !== "SYNCON_EXTENSION_BACKUP" || payload.schemaVersion !== 1 || !payload.data) throw new Error("Unsupported SyncOn backup");
    const byId = new Map(appState.intervals.map(item => [item.recordId, item]));
    (payload.data.intervals || []).forEach(item => { if (item.recordId && item.sourcePlatform === "CHROME") byId.set(item.recordId, item); });
    appState.intervals = [...byId.values()];
    appState.domains = { ...payload.data.domains, ...appState.domains };
    appState.limits = { ...payload.data.limits, ...appState.limits };
    appState.dailyStates = { ...payload.data.dailyStates, ...appState.dailyStates };
    appState.dailyTotals = {};
    appState.intervals.filter(item => !item.isDeleted).forEach(item => { appState.dailyTotals[item.usageDate] ||= {}; appState.dailyTotals[item.usageDate][item.sourceIdentifier] = (appState.dailyTotals[item.usageDate][item.sourceIdentifier] || 0) + item.durationMillis; });
    await chrome.storage.local.set({ intervals: appState.intervals, domains: appState.domains, limits: appState.limits, dailyStates: appState.dailyStates, dailyTotals: appState.dailyTotals });
    renderAll(); toast("Backup imported without changing this installation ID.");
  } catch (error) { toast(error.message || "Could not import this backup."); }
  event.target.value = "";
});

$("#clearData").addEventListener("click", async () => {
  if (!confirm("Clear all Chrome usage intervals and daily totals? Limits and settings will remain.")) return;
  appState.intervals = []; appState.dailyTotals = {}; appState.dailyStates = {};
  await chrome.storage.local.set({ intervals: [], dailyTotals: {}, dailyStates: {} });
  renderAll(); toast("Chrome usage history cleared.");
});

load();
