importScripts("lib/core.js");

const C = SyncOnCore;
const STORAGE_VERSION = 1;
const ALARM_NAME = "syncon-minute-tick";
const RETENTION_DAYS = 1095;

let active = { tabId: null, windowId: null, domain: null, startedAt: null, url: null };
let isIdle = false;
let focusedWindowId = chrome.windows.WINDOW_ID_NONE;

async function state() {
  const data = await chrome.storage.local.get([
    "installationId", "settings", "intervals", "dailyTotals", "domains", "limits", "dailyStates"
  ]);
  if (!data.installationId) data.installationId = crypto.randomUUID();
  data.settings ||= { trackingEnabled: true, idleThresholdSeconds: 60, excludedDomains: [] };
  data.intervals ||= [];
  data.dailyTotals ||= {};
  data.domains ||= {};
  data.limits ||= {};
  data.dailyStates ||= {};
  await chrome.storage.local.set({ installationId: data.installationId, settings: data.settings });
  return data;
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
  for (const segment of C.splitUsageInterval(active.startedAt, endAt)) {
    const recordId = C.stableId([data.installationId, active.domain, segment.startTimeUtc, segment.endTimeUtc]);
    if (data.intervals.some(item => item.recordId === recordId)) continue;
    data.intervals.push({
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
    data.dailyTotals[segment.usageDate] ||= {};
    data.dailyTotals[segment.usageDate][active.domain] = (data.dailyTotals[segment.usageDate][active.domain] || 0) + segment.durationMillis;
    data.domains[active.domain] ||= { displayName: active.domain, category: C.categoryForDomain(active.domain), manuallyCategorized: false };
  }
  await chrome.storage.local.set({ intervals: data.intervals, dailyTotals: data.dailyTotals, domains: data.domains });
  active.startedAt = endAt;
}

async function stopActive(endAt = Date.now()) {
  await commitActive(endAt);
  active = { tabId: null, windowId: null, domain: null, startedAt: null, url: null };
}

async function beginTab(tab) {
  await stopActive();
  const data = await state();
  const domain = C.domainFromUrl(tab?.url || "");
  if (isIdle || focusedWindowId === chrome.windows.WINDOW_ID_NONE || !trackable(domain, data.settings)) return;
  active = { tabId: tab.id, windowId: tab.windowId, domain, startedAt: Date.now(), url: tab.url };
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
  if (!limit?.enabled) return;
  const key = `${C.usageDate()}|${active.domain}`;
  const dailyState = data.dailyStates[key] || { warningShown: false, extraMinutes: 0 };
  const evaluation = C.evaluateLimit(await usedToday(active.domain, data), limit, dailyState);
  if (!evaluation) return;

  if (evaluation.shouldWarn) {
    dailyState.warningShown = true;
    data.dailyStates[key] = dailyState;
    await chrome.storage.local.set({ dailyStates: data.dailyStates });
    await chrome.action.setBadgeText({ tabId: tab.id, text: `${evaluation.remainingMinutes}m` });
    await chrome.action.setBadgeBackgroundColor({ tabId: tab.id, color: "#E67E68" });
  }

  if (evaluation.shouldBlock && !tab.url?.startsWith(chrome.runtime.getURL("blocked/blocked.html"))) {
    await commitActive();
    const params = new URLSearchParams({
      domain: active.domain,
      style: evaluation.style,
      snooze: String(evaluation.snoozeMinutes),
      used: String(evaluation.usedMinutes),
      limit: String(evaluation.limitMinutes)
    });
    await chrome.tabs.update(tab.id, { url: `${chrome.runtime.getURL("blocked/blocked.html")}?${params}` });
  }
}

async function cleanup() {
  const data = await state();
  const cutoff = Date.now() - RETENTION_DAYS * 86_400_000;
  data.intervals = data.intervals.filter(item => item.endTimeUtc >= cutoff);
  const validDates = new Set(data.intervals.map(item => item.usageDate));
  for (const date of Object.keys(data.dailyTotals)) if (!validDates.has(date)) delete data.dailyTotals[date];
  const today = C.usageDate();
  for (const key of Object.keys(data.dailyStates)) if (!key.startsWith(`${today}|`)) delete data.dailyStates[key];
  await chrome.storage.local.set({ intervals: data.intervals, dailyTotals: data.dailyTotals, dailyStates: data.dailyStates });
}

chrome.runtime.onInstalled.addListener(async () => {
  await state();
  chrome.idle.setDetectionInterval(60);
  chrome.alarms.create(ALARM_NAME, { periodInMinutes: 0.5 });
  await cleanup();
});

chrome.runtime.onStartup.addListener(async () => {
  await state();
  chrome.alarms.create(ALARM_NAME, { periodInMinutes: 0.5 });
  focusedWindowId = (await chrome.windows.getLastFocused()).id;
  await refreshCurrentTab();
});

chrome.alarms.onAlarm.addListener(async alarm => {
  if (alarm.name !== ALARM_NAME) return;
  if (active.domain && !isIdle) {
    await commitActive();
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
    }
  })();
  return true;
});

chrome.windows.getLastFocused().then(window => {
  focusedWindowId = window.id;
  return chrome.idle.queryState(60);
}).then(idleState => {
  isIdle = idleState !== "active";
  return refreshCurrentTab();
});
