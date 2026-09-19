importScripts("lib/core.js", "lib/db.js");

const C = SyncOnCore;
const STORAGE_VERSION = 1;
const ALARM_NAME = "syncon-minute-tick";
const RETENTION_DAYS = 1095;

let active = { tabId: null, windowId: null, domain: null, startedAt: null, url: null };
let isIdle = false;
let focusedWindowId = chrome.windows.WINDOW_ID_NONE;

async function persistActiveSession() {
  if (active.domain && active.startedAt) {
    await chrome.storage.session.set({ activeSession: active });
  } else {
    await chrome.storage.session.remove("activeSession");
  }
}

async function state() {
  const data = await chrome.storage.local.get([
    "installationId", "settings", "dailyTotals", "domains", "limits", "categoryLimits", "dailyStates", "blockEvents"
  ]);
  if (!data.installationId) data.installationId = crypto.randomUUID();
  data.settings ||= { trackingEnabled: true, idleThresholdSeconds: 60, excludedDomains: [] };
  data.dailyTotals ||= {};
  data.domains ||= {};
  data.limits ||= {};
  data.categoryLimits ||= {};
  data.dailyStates ||= {};
  data.blockEvents ||= [];
  await chrome.storage.local.set({ installationId: data.installationId, settings: data.settings });
  return data;
}

async function migrateLegacyIntervals() {
  const legacy = await chrome.storage.local.get("intervals");
  if (Array.isArray(legacy.intervals) && legacy.intervals.length) {
    await SyncOnDb.addIntervals(legacy.intervals.filter(validInterval));
  }
  if (legacy.intervals) await chrome.storage.local.remove("intervals");
}

function validInterval(item) {
  return Boolean(
    item && typeof item.recordId === "string" && item.recordId &&
    item.sourcePlatform === "CHROME" && item.sourceType === "CHROME_DOMAIN" &&
    typeof item.sourceIdentifier === "string" && item.sourceIdentifier &&
    typeof item.usageDate === "string" && /^\d{4}-\d{2}-\d{2}$/.test(item.usageDate) &&
    Number.isFinite(item.startTimeUtc) && Number.isFinite(item.endTimeUtc) &&
    item.endTimeUtc > item.startTimeUtc &&
    Number.isFinite(item.durationMillis) && item.durationMillis > 0
  );
}

function totalsFromIntervals(intervals) {
  const totals = {};
  intervals.filter(item => !item.isDeleted).forEach(item => {
    totals[item.usageDate] ||= {};
    totals[item.usageDate][item.sourceIdentifier] = (totals[item.usageDate][item.sourceIdentifier] || 0) + item.durationMillis;
  });
  return totals;
}

function trackable(domain, settings) {
  if (!domain || settings.trackingEnabled === false) return false;
  return !(settings.excludedDomains || []).some(item => domain === item || domain.endsWith(`.${item}`));
}

async function commitActive(endAt = Date.now()) {
  if (!active.domain || !active.startedAt || endAt <= active.startedAt) return;
  const data = await state();
  if (!trackable(active.domain, data.settings)) {
    active.startedAt = endAt;
    return;
  }

  const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC";
  const candidates = [];
  for (const segment of C.splitUsageInterval(active.startedAt, endAt)) {
    const recordId = C.stableId([data.installationId, active.domain, segment.startTimeUtc, segment.endTimeUtc]);
    candidates.push({
      recordId,
      installationId: data.installationId,
      sourcePlatform: "CHROME",
      sourceType: "CHROME_DOMAIN",
      sourceIdentifier: active.domain,
      usageDate: segment.usageDate,
      startTimeUtc: segment.startTimeUtc,
      endTimeUtc: segment.endTimeUtc,
      durationMillis: segment.durationMillis,
      timezoneId: timezone,
      utcOffsetMinutes: -new Date(segment.startTimeUtc).getTimezoneOffset(),
      createdAtUtc: endAt,
      updatedAtUtc: endAt,
      localRevision: 1,
      serverRevision: null,
      syncState: "LOCAL_ONLY",
      isDeleted: false
    });
  }
  const inserted = await SyncOnDb.addIntervals(candidates);
  for (const interval of inserted) {
    data.dailyTotals[interval.usageDate] ||= {};
    data.dailyTotals[interval.usageDate][active.domain] = (data.dailyTotals[interval.usageDate][active.domain] || 0) + interval.durationMillis;
  }
  data.domains[active.domain] ||= { displayName: active.domain, category: C.categoryForDomain(active.domain), manuallyCategorized: false };
  await chrome.storage.local.set({ dailyTotals: data.dailyTotals, domains: data.domains });
  active.startedAt = endAt;
  await persistActiveSession();
}

async function stopActive(endAt = Date.now()) {
  await commitActive(endAt);
  active = { tabId: null, windowId: null, domain: null, startedAt: null, url: null };
  await persistActiveSession();
}

async function beginTab(tab) {
  await stopActive();
  const data = await state();
  const domain = C.domainFromUrl(tab?.url || "");
  if (isIdle || focusedWindowId === chrome.windows.WINDOW_ID_NONE || !trackable(domain, data.settings)) return;
  active = { tabId: tab.id, windowId: tab.windowId, domain, startedAt: Date.now(), url: tab.url };
  await persistActiveSession();
  await enforce(tab, data);
}

async function refreshCurrentTab() {
  if (isIdle || focusedWindowId === chrome.windows.WINDOW_ID_NONE) return stopActive();
  const tabs = await chrome.tabs.query({ active: true, windowId: focusedWindowId });
  await beginTab(tabs[0]);
}

async function usedToday(domain, data) {
  let total = data.dailyTotals[C.usageDate()]?.[domain] || 0;
  if (active.domain === domain && active.startedAt) total += Date.now() - active.startedAt;
  return total;
}

async function enforce(tab, suppliedState) {
  if (!tab?.id || !active.domain) return;
  const data = suppliedState || await state();
  const limit = data.limits[active.domain];
  const category = data.domains[active.domain]?.category || C.categoryForDomain(active.domain);
  const categoryLimit = data.categoryLimits[category];
  if (!limit?.enabled && !categoryLimit?.enabled) {
    await chrome.action.setBadgeText({ tabId: tab.id, text: "" });
    return;
  }
  const key = `${C.usageDate()}|${active.domain}`;
  const dailyState = data.dailyStates[key] || { warningShown: false, extraMinutes: 0 };
  const appEvaluation = C.evaluateLimit(await usedToday(active.domain, data), limit, dailyState);
  const categoryKey = `${C.usageDate()}|category|${category}`;
  const categoryState = data.dailyStates[categoryKey] || { warningShown: false, extraMinutes: 0 };
  const categoryUsed = Object.entries(data.dailyTotals[C.usageDate()] || {}).reduce((sum, [domain, duration]) => {
    return sum + ((data.domains[domain]?.category || C.categoryForDomain(domain)) === category ? duration : 0);
  }, 0) + (active.startedAt ? Date.now() - active.startedAt : 0);
  const categoryEvaluation = C.evaluateLimit(categoryUsed, categoryLimit, categoryState);
  const evaluation = [appEvaluation, categoryEvaluation].filter(Boolean).sort((a, b) => a.remainingMinutes - b.remainingMinutes)[0];
  if (!evaluation) return;
  const categoryTriggered = evaluation === categoryEvaluation;
  const selectedState = categoryTriggered ? categoryState : dailyState;
  const selectedKey = categoryTriggered ? categoryKey : key;

  if (evaluation.shouldWarn) {
    selectedState.warningShown = true;
    data.dailyStates[selectedKey] = selectedState;
    await chrome.storage.local.set({ dailyStates: data.dailyStates });
    await chrome.action.setBadgeText({ tabId: tab.id, text: `${evaluation.remainingMinutes}m` });
    await chrome.action.setBadgeBackgroundColor({ tabId: tab.id, color: "#E67E68" });
    await chrome.notifications.create(`warning-${selectedKey}`, {
      type: "basic",
      iconUrl: "assets/icon-128.png",
      title: categoryTriggered ? `${category} time is almost up` : `${active.domain} time is almost up`,
      message: `${evaluation.remainingMinutes} minute${evaluation.remainingMinutes === 1 ? "" : "s"} remaining today.`
    });
  }
  if (!evaluation.shouldWarn && !evaluation.shouldBlock) {
    await chrome.action.setBadgeText({ tabId: tab.id, text: "" });
  }

  if (evaluation.shouldBlock && !tab.url?.startsWith(chrome.runtime.getURL("blocked/blocked.html"))) {
    if (!selectedState.blockLogged) {
      selectedState.blockLogged = true;
      data.dailyStates[selectedKey] = selectedState;
      data.blockEvents.push({ recordId: crypto.randomUUID(), usageDate: C.usageDate(), domain: active.domain, category: categoryTriggered ? category : null, timestamp: Date.now(), eventType: "BLOCKED", syncState: "LOCAL_ONLY", localRevision: 1 });
      await chrome.storage.local.set({ dailyStates: data.dailyStates, blockEvents: data.blockEvents });
    }
    await commitActive();
    const params = new URLSearchParams({
      domain: active.domain,
      style: evaluation.style,
      snooze: String(evaluation.snoozeMinutes),
      used: String(evaluation.usedMinutes),
      limit: String(evaluation.limitMinutes)
    });
    if (categoryTriggered) params.set("category", category);
    await chrome.tabs.update(tab.id, { url: `${chrome.runtime.getURL("blocked/blocked.html")}?${params}` });
  }
}

async function cleanup() {
  const data = await state();
  const cutoff = Date.now() - RETENTION_DAYS * 86_400_000;
  await SyncOnDb.deleteOlderThan(cutoff);
  for (const date of Object.keys(data.dailyTotals)) {
    if (new Date(`${date}T04:00:00`).getTime() < cutoff) delete data.dailyTotals[date];
  }
  const today = C.usageDate();
  for (const key of Object.keys(data.dailyStates)) if (!key.startsWith(`${today}|`)) delete data.dailyStates[key];
  await chrome.storage.local.set({ dailyTotals: data.dailyTotals, dailyStates: data.dailyStates });
}

chrome.runtime.onInstalled.addListener(async () => {
  await migrateLegacyIntervals();
  await state();
  chrome.idle.setDetectionInterval(60);
  chrome.alarms.create(ALARM_NAME, { periodInMinutes: 0.5 });
  await cleanup();
});

chrome.runtime.onStartup.addListener(async () => {
  await migrateLegacyIntervals();
  await state();
  chrome.alarms.create(ALARM_NAME, { periodInMinutes: 0.5 });
  focusedWindowId = (await chrome.windows.getLastFocused()).id;
  await refreshCurrentTab();
});

chrome.alarms.onAlarm.addListener(async alarm => {
  if (alarm.name !== ALARM_NAME) return;
  if (active.domain && !isIdle) {
    // Enforce every 30 seconds, but avoid creating and rewriting an interval record on every
    // check. Long uninterrupted sessions are durably checkpointed every five minutes; tab,
    // focus, idle and shutdown transitions still commit immediately.
    if (Date.now() - active.startedAt >= 5 * 60_000) await commitActive();
    const [tab] = await chrome.tabs.query({ active: true, windowId: active.windowId });
    await enforce(tab);
  }
  if (new Date().getHours() === 4 && new Date().getMinutes() < 2) await cleanup();
});

chrome.tabs.onActivated.addListener(refreshCurrentTab);
chrome.tabs.onUpdated.addListener(async (tabId, changeInfo, tab) => {
  if (tabId === active.tabId && changeInfo.url) await beginTab(tab);
});
chrome.tabs.onRemoved.addListener(async tabId => {
  if (tabId === active.tabId) await stopActive();
});
chrome.windows.onFocusChanged.addListener(async windowId => {
  focusedWindowId = windowId;
  await refreshCurrentTab();
});
chrome.idle.onStateChanged.addListener(async newState => {
  isIdle = newState !== "active";
  if (isIdle) await stopActive(); else await refreshCurrentTab();
});

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  (async () => {
    if (message.type === "GET_LIVE_STATE") {
      const data = await state();
      sendResponse({ active, isIdle, data });
    } else if (message.type === "GET_INTERVAL_STATS") {
      sendResponse({ count: await SyncOnDb.count() });
    } else if (message.type === "EXPORT_BACKUP") {
      const data = await state();
      sendResponse({
        kind: "SYNCON_EXTENSION_BACKUP",
        schemaVersion: 2,
        exportedAtUtc: Date.now(),
        sourcePlatform: "CHROME",
        sourceInstallationId: data.installationId,
        data: {
          intervals: await SyncOnDb.getAll(),
          dailyTotals: data.dailyTotals,
          domains: data.domains,
          limits: data.limits,
          categoryLimits: data.categoryLimits,
          dailyStates: data.dailyStates,
          blockEvents: data.blockEvents,
          settings: data.settings
        }
      });
    } else if (message.type === "IMPORT_BACKUP") {
      const payload = message.payload;
      if (payload?.kind !== "SYNCON_EXTENSION_BACKUP" || ![1, 2].includes(payload.schemaVersion) || !payload.data) {
        throw new Error("Unsupported SyncOn backup");
      }
      const intervals = payload.data.intervals || [];
      if (!Array.isArray(intervals) || !intervals.every(validInterval)) throw new Error("Backup contains invalid activity intervals");
      await SyncOnDb.addIntervals(intervals);
      const allIntervals = await SyncOnDb.getAll();
      const data = await state();
      data.dailyTotals = totalsFromIntervals(allIntervals);
      data.domains = { ...(payload.data.domains || {}), ...data.domains };
      data.limits = { ...(payload.data.limits || {}), ...data.limits };
      data.categoryLimits = { ...(payload.data.categoryLimits || {}), ...data.categoryLimits };
      data.dailyStates = { ...(payload.data.dailyStates || {}), ...data.dailyStates };
      const eventIds = new Set(data.blockEvents.map(item => item.recordId));
      for (const event of payload.data.blockEvents || []) if (event?.recordId && !eventIds.has(event.recordId)) data.blockEvents.push(event);
      await chrome.storage.local.set({ dailyTotals: data.dailyTotals, domains: data.domains, limits: data.limits, categoryLimits: data.categoryLimits, dailyStates: data.dailyStates, blockEvents: data.blockEvents });
      sendResponse({ ok: true, count: allIntervals.length });
    } else if (message.type === "CLEAR_HISTORY") {
      await SyncOnDb.clear();
      await chrome.storage.local.set({ dailyTotals: {}, dailyStates: {} });
      sendResponse({ ok: true });
    } else if (message.type === "SET_TRACKING") {
      const data = await state();
      data.settings.trackingEnabled = Boolean(message.enabled);
      await chrome.storage.local.set({ settings: data.settings });
      if (!message.enabled) await stopActive(); else await refreshCurrentTab();
      sendResponse({ ok: true });
    } else if (message.type === "SNOOZE_DOMAIN") {
      const data = await state();
      const key = `${C.usageDate()}|${message.domain}`;
      const daily = data.dailyStates[key] || { warningShown: true, extraMinutes: 0 };
      daily.extraMinutes += Number(message.minutes) || 5;
      data.dailyStates[key] = daily;
      await chrome.storage.local.set({ dailyStates: data.dailyStates });
      sendResponse({ ok: true });
    } else if (message.type === "SNOOZE_CATEGORY") {
      const data = await state();
      const key = `${C.usageDate()}|category|${message.category}`;
      const daily = data.dailyStates[key] || { warningShown: true, extraMinutes: 0 };
      daily.extraMinutes += Number(message.minutes) || 5;
      data.dailyStates[key] = daily;
      await chrome.storage.local.set({ dailyStates: data.dailyStates });
      sendResponse({ ok: true });
    }
  })().catch(error => sendResponse({ ok: false, error: error?.message || "SyncOn operation failed" }));
  return true;
});

async function restoreWorkerSession() {
  await migrateLegacyIntervals();
  const window = await chrome.windows.getLastFocused();
  focusedWindowId = window.id;
  isIdle = (await chrome.idle.queryState(60)) !== "active";
  const [tab] = await chrome.tabs.query({ active: true, windowId: focusedWindowId });
  const saved = (await chrome.storage.session.get("activeSession")).activeSession;
  const domain = C.domainFromUrl(tab?.url || "");
  const data = await state();

  if (!isIdle && saved?.domain === domain && saved?.tabId === tab?.id && trackable(domain, data.settings)) {
    active = saved;
    // Events that change focus/tab wake the worker. If none occurred while it slept, the saved
    // session is continuous and the gap can be committed safely.
    await commitActive(Date.now());
    await enforce(tab, data);
  } else {
    await refreshCurrentTab();
  }
}

restoreWorkerSession();
