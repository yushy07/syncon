(function (root) {
  const api = root.SyncOnSupabase;
  const db = root.SyncOnDb;
  const core = root.SyncOnCore;
  const CONNECTION_KEY = "connectionState";

  const iso = value => new Date(value ?? Date.now()).toISOString();
  const camelInterval = item => ({
    recordId: item.record_id,
    installationId: item.installation_id,
    sourcePlatform: item.source_platform,
    sourceType: item.source_type,
    sourceIdentifier: item.source_identifier,
    usageDate: item.usage_date,
    startTimeUtc: Date.parse(item.start_time_utc),
    endTimeUtc: Date.parse(item.end_time_utc),
    durationMillis: Number(item.duration_millis),
    timezoneId: item.timezone_id,
    utcOffsetMinutes: Number(item.utc_offset_minutes),
    createdAtUtc: Date.parse(item.client_created_at),
    updatedAtUtc: Date.parse(item.client_updated_at),
    localRevision: Number(item.local_revision),
    serverRevision: Number(item.server_revision),
    syncState: "SYNCED",
    isDeleted: Boolean(item.is_deleted)
  });

  async function getConnection() {
    return (await chrome.storage.local.get(CONNECTION_KEY))[CONNECTION_KEY] || { status: "LOCAL_ONLY" };
  }

  async function setConnection(patch) {
    const value = { ...(await getConnection()), ...patch, updatedAt: Date.now() };
    await chrome.storage.local.set({ [CONNECTION_KEY]: value });
    return value;
  }

  function secretBytes() {
    const bytes = new Uint8Array(32);
    crypto.getRandomValues(bytes);
    return bytes;
  }

  function base64Url(bytes) {
    let binary = "";
    bytes.forEach(value => { binary += String.fromCharCode(value); });
    return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  }

  async function sha256Hex(value) {
    const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
    return [...new Uint8Array(digest)].map(byte => byte.toString(16).padStart(2, "0")).join("");
  }

  async function startPairing(installationId) {
    await api.ensureAnonymousSession();
    const secret = base64Url(secretBytes());
    const response = await api.rpc("create_pairing_request_v2", {
      p_installation_id: installationId,
      p_secret_hash: await sha256Hex(secret),
      p_display_name: "Chrome browser",
      p_client_version: chrome.runtime.getManifest().version
    });
    return setConnection({
      status: "PAIRING",
      requestId: response.request_id,
      secret,
      qrPayload: `syncon://pair?v=1&id=${encodeURIComponent(response.request_id)}&secret=${encodeURIComponent(secret)}`,
      expiresAt: Date.parse(response.expires_at),
      lastError: null
    });
  }

  async function pollPairing() {
    const connection = await getConnection();
    if (connection.status !== "PAIRING" || !connection.requestId) return connection;
    if (connection.expiresAt <= Date.now()) return setConnection({ status: "EXPIRED", secret: null, qrPayload: null });
    try {
      const result = await api.rpc("get_pairing_status_v2", { p_request_id: connection.requestId });
      if (result.status === "CONNECTED") {
        return setConnection({ status: "CONNECTED", accountId: result.account_id, connectedAt: Date.now(), secret: null, qrPayload: null, lastError: null });
      }
      if (["EXPIRED", "CANCELLED", "NOT_FOUND"].includes(result.status)) return setConnection({ status: result.status });
      return connection;
    } catch (error) {
      return setConnection({ status: "ERROR", lastError: error.message });
    }
  }

  async function cancelPairing() {
    const connection = await getConnection();
    if (connection.requestId && connection.status === "PAIRING") {
      try { await api.rpc("cancel_pairing_request_v2", { p_request_id: connection.requestId }); } catch (_) { /* Local mode must remain available offline. */ }
    }
    return setConnection({ status: "LOCAL_ONLY", requestId: null, secret: null, qrPayload: null, expiresAt: null, lastError: null });
  }

  const intervalPayload = item => ({
    record_id: item.recordId,
    installation_id: item.installationId,
    source_platform: item.sourcePlatform,
    source_type: item.sourceType,
    source_identifier: item.sourceIdentifier,
    usage_date: item.usageDate,
    start_time_utc: iso(item.startTimeUtc),
    end_time_utc: iso(item.endTimeUtc),
    duration_millis: item.durationMillis,
    timezone_id: item.timezoneId,
    utc_offset_minutes: item.utcOffsetMinutes,
    client_created_at: iso(item.createdAtUtc),
    client_updated_at: iso(item.updatedAtUtc),
    local_revision: item.localRevision || 1,
    is_deleted: Boolean(item.isDeleted)
  });

  async function pushIntervals() {
    let pending = await db.getPending(250);
    while (pending.length) {
      let result;
      try {
        result = await api.rpc("sync_push_intervals_v3", { p_intervals: pending.map(intervalPayload) });
      } catch (error) {
        await db.recordAttempts(pending, "activity_intervals", error);
        throw error;
      }
      await db.markAcknowledged((result.acknowledgements || []).filter(item => item.status === "ACCEPTED"));
      if (pending.length < 250) break;
      pending = await db.getPending(250);
    }
  }

  async function pushState(local) {
    const sources = Object.entries(local.domains || {}).map(([domain, value]) => ({
      source_type: "CHROME_DOMAIN", source_identifier: domain,
      display_name: value.displayName || domain, category: value.category || "Other",
      is_category_manually_set: Boolean(value.manuallyCategorized),
      client_updated_at: iso(value.updatedAtUtc || 0), local_revision: value.localRevision || 1,
      is_deleted: false
    }));
    const limits = Object.entries(local.limits || {}).map(([domain, value]) => ({
      record_id: value.recordId || `chrome-domain-limit:${domain}`,
      installation_id: local.installationId, target_type: "SOURCE", source_platform: "CHROME",
      target_identifier: domain, daily_limit_minutes: value.limitMinutes || null,
      blocking_style: value.style || "STRICT", snooze_minutes: value.snoozeMinutes || 5,
      is_enabled: Boolean(value.enabled), client_updated_at: iso(value.updatedAtUtc || 0),
      local_revision: value.localRevision || 1,
      base_server_revision: Number(value.serverRevision || 0),
      is_deleted: Boolean(value.isDeleted)
    }));
    Object.entries(local.categoryLimits || {}).forEach(([category, value]) => limits.push({
      record_id: value.recordId || `shared-category-limit:${category}`,
      installation_id: local.installationId, target_type: "CATEGORY", source_platform: null,
      target_identifier: category, daily_limit_minutes: value.limitMinutes || null,
      blocking_style: value.style || "STRICT", snooze_minutes: value.snoozeMinutes || 5,
      is_enabled: Boolean(value.enabled), client_updated_at: iso(value.updatedAtUtc || 0),
      local_revision: value.localRevision || 1,
      base_server_revision: Number(value.serverRevision || 0),
      is_deleted: Boolean(value.isDeleted)
    }));
    const events = (local.blockEvents || []).map(value => ({
      record_id: value.recordId, installation_id: local.installationId,
      source_platform: "CHROME", source_identifier: value.domain,
      category: value.category || null, usage_date: value.usageDate,
      event_type: value.eventType, event_time_utc: iso(value.timestamp),
      extra_minutes: value.extraMinutes || 0, local_revision: value.localRevision || 1,
      is_deleted: Boolean(value.isDeleted)
    }));
    const mappings = core.LOGICAL_SERVICES.flatMap(service => service.chrome.map(domain => ({
      record_id: `default:CHROME_DOMAIN:${domain}`,
      source_type: "CHROME_DOMAIN",
      source_identifier: domain,
      logical_service_id: service.id,
      client_updated_at: iso(0),
      local_revision: 1,
      is_deleted: false
    })));
    const result = await api.rpc("sync_push_state_v3", {
      p_sources: sources, p_limits: limits, p_block_events: events, p_source_mappings: mappings
    });
    const acknowledged = new Map((result.limit_acknowledgements || []).map(item => [item.record_id, Number(item.server_revision)]));
    Object.entries(local.limits || {}).forEach(([domain, value]) => {
      const revision = acknowledged.get(value.recordId || `chrome-domain-limit:${domain}`);
      if (revision) Object.assign(value, { serverRevision: revision, syncState: "SYNCED" });
    });
    Object.entries(local.categoryLimits || {}).forEach(([category, value]) => {
      const revision = acknowledged.get(value.recordId || `shared-category-limit:${category}`);
      if (revision) Object.assign(value, { serverRevision: revision, syncState: "SYNCED" });
    });
    await db.resolveConflicts([...acknowledged.keys()]);
    const conflicts = (result.conflicts || []).map(item => ({
      recordId: item.record_id,
      collection: "limit_settings",
      localPayload: JSON.stringify(limits.find(limit => limit.record_id === item.record_id) || {}),
      serverPayload: JSON.stringify(item.server_record || {}),
      serverRevision: Number(item.server_record?.server_revision || 0),
      detectedAtUtc: Date.now(),
      resolvedAtUtc: null
    }));
    await db.putConflicts(conflicts);
    for (const item of result.conflicts || []) applyLimitRecord(item.server_record, local.limits, local.categoryLimits);
    await chrome.storage.local.set({ limits: local.limits, categoryLimits: local.categoryLimits });
  }

  function applyLimitRecord(item, limits, categoryLimits) {
    if (!item) return;
    const value = {
      recordId: item.record_id, enabled: Boolean(item.is_enabled),
      limitMinutes: item.daily_limit_minutes, style: item.blocking_style,
      snoozeMinutes: item.snooze_minutes, updatedAtUtc: Date.parse(item.client_updated_at),
      localRevision: Number(item.local_revision), serverRevision: Number(item.server_revision),
      syncState: "SYNCED", isDeleted: Boolean(item.is_deleted)
    };
    if (item.target_type === "CATEGORY") categoryLimits[item.target_identifier] = { ...value, category: item.target_identifier };
    if (item.target_type === "SOURCE" && item.source_platform === "CHROME") limits[item.target_identifier] = value;
  }

  async function applyPull(payload) {
    const stored = await chrome.storage.local.get(["installationId", "domains", "limits", "categoryLimits", "blockEvents", "sourceMappings"]);
    const domains = stored.domains || {};
    const limits = stored.limits || {};
    const categoryLimits = stored.categoryLimits || {};
    const blockEvents = stored.blockEvents || [];
    (payload.sources || []).filter(item => item.source_type === "CHROME_DOMAIN").forEach(item => {
      domains[item.source_identifier] = {
        displayName: item.display_name || item.source_identifier,
        category: item.category || "Other",
        manuallyCategorized: Boolean(item.is_category_manually_set),
        updatedAtUtc: Date.parse(item.client_updated_at), localRevision: Number(item.local_revision),
        syncState: "SYNCED"
      };
    });
    (payload.limits || []).forEach(item => applyLimitRecord(item, limits, categoryLimits));
    const eventIds = new Set(blockEvents.map(item => item.recordId));
    (payload.block_events || []).filter(item => item.source_platform === "CHROME").forEach(item => {
      if (!eventIds.has(item.record_id)) blockEvents.push({
        recordId: item.record_id, usageDate: item.usage_date, domain: item.source_identifier,
        category: item.category, timestamp: Date.parse(item.event_time_utc),
        eventType: item.event_type, extraMinutes: item.extra_minutes,
        localRevision: Number(item.local_revision), syncState: "SYNCED"
      });
    });
    const intervalRecords = (payload.intervals || []).map(camelInterval);
    const localIntervals = intervalRecords.filter(item => item.sourcePlatform === "CHROME" && item.installationId === stored.installationId);
    const remoteIntervals = intervalRecords.filter(item => !(item.sourcePlatform === "CHROME" && item.installationId === stored.installationId));
    const sourceMappings = { ...(stored.sourceMappings || {}) };
    (payload.source_mappings || []).filter(item => !item.is_deleted).forEach(item => {
      sourceMappings[`${item.source_type}:${item.source_identifier}`] = item.logical_service_id;
    });
    const pulledState = { domains, limits, categoryLimits, blockEvents, sourceMappings };
    await db.applySyncPage(localIntervals, remoteIntervals, payload.next_revision, pulledState);
    const dailyTotals = {};
    (await db.getAll()).filter(item => !item.isDeleted).forEach(item => {
      dailyTotals[item.usageDate] ||= {};
      dailyTotals[item.usageDate][item.sourceIdentifier] = (dailyTotals[item.usageDate][item.sourceIdentifier] || 0) + item.durationMillis;
    });
    const remoteDailyTotals = {};
    (await db.getRemoteAll()).filter(item => !item.isDeleted).forEach(item => {
      remoteDailyTotals[item.usageDate] ||= {};
      remoteDailyTotals[item.usageDate][item.sourceIdentifier] = (remoteDailyTotals[item.usageDate][item.sourceIdentifier] || 0) + item.durationMillis;
    });
    await chrome.storage.local.set({ domains, limits, categoryLimits, blockEvents, sourceMappings, dailyTotals, remoteDailyTotals });
  }

  async function pullAll() {
    let cursor = Number(await db.getMeta("accountCursor", 0));
    let page;
    do {
      page = await api.rpc("sync_pull_v2", { p_after_revision: cursor, p_limit: 1000 });
      await applyPull(page);
      cursor = Number(page.next_revision || cursor);
    } while (page.has_more);
  }

  async function hydrateDurableState() {
    const state = await db.getMeta("pulledState", null);
    if (state) await chrome.storage.local.set(state);
  }

  async function syncNow() {
    const connection = await getConnection();
    if (!["CONNECTED", "SYNCING", "OFFLINE"].includes(connection.status)) return connection;
    try {
      await setConnection({ status: "SYNCING", lastError: null });
      await hydrateDurableState();
      const local = await chrome.storage.local.get(["installationId", "domains", "limits", "categoryLimits", "blockEvents"]);
      await api.rpc("sync_register_installation_v2", {
        p_installation_id: local.installationId, p_platform: "CHROME",
        p_display_name: "Chrome browser", p_client_version: chrome.runtime.getManifest().version
      });
      await pushIntervals();
      await pushState(local);
      await pullAll();
      const conflicts = await db.getConflicts();
      return setConnection({ status: "CONNECTED", lastSyncedAt: Date.now(), lastError: null, conflictCount: conflicts.length });
    } catch (error) {
      const revoked = /Connected SyncOn account required/i.test(error.message);
      return setConnection({ status: revoked ? "REVOKED" : "OFFLINE", lastError: error.message });
    }
  }

  root.SyncOnSync = { getConnection, startPairing, pollPairing, cancelPairing, syncNow, hydrateDurableState };
})(typeof globalThis !== "undefined" ? globalThis : this);
