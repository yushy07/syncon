(function (root) {
  const DB_NAME = "syncon-extension";
  const DB_VERSION = 3;
  const STORE = "usage_intervals";
  const REMOTE_STORE = "remote_usage_intervals";
  const META_STORE = "sync_metadata";
  const CONFLICT_STORE = "sync_conflicts";
  const ATTEMPT_STORE = "sync_upload_attempts";

  function open() {
    return new Promise((resolve, reject) => {
      const request = indexedDB.open(DB_NAME, DB_VERSION);
      request.onupgradeneeded = () => {
        const db = request.result;
        if (!db.objectStoreNames.contains(STORE)) {
          const store = db.createObjectStore(STORE, { keyPath: "recordId" });
          store.createIndex("usageDate", "usageDate", { unique: false });
          store.createIndex("endTimeUtc", "endTimeUtc", { unique: false });
          store.createIndex("syncState", "syncState", { unique: false });
        }
        if (!db.objectStoreNames.contains(REMOTE_STORE)) {
          const remote = db.createObjectStore(REMOTE_STORE, { keyPath: "recordId" });
          remote.createIndex("usageDate", "usageDate", { unique: false });
          remote.createIndex("sourcePlatform", "sourcePlatform", { unique: false });
        }
        if (!db.objectStoreNames.contains(META_STORE)) db.createObjectStore(META_STORE, { keyPath: "key" });
        if (!db.objectStoreNames.contains(CONFLICT_STORE)) {
          const conflicts = db.createObjectStore(CONFLICT_STORE, { keyPath: "recordId" });
          conflicts.createIndex("resolvedAtUtc", "resolvedAtUtc", { unique: false });
        }
        if (!db.objectStoreNames.contains(ATTEMPT_STORE)) db.createObjectStore(ATTEMPT_STORE, { keyPath: "recordId" });
      };
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
  }

  async function addIntervals(intervals) {
    if (!intervals.length) return [];
    const db = await open();
    return new Promise((resolve, reject) => {
      const inserted = [];
      const transaction = db.transaction(STORE, "readwrite");
      const store = transaction.objectStore(STORE);
      intervals.forEach(interval => {
        const request = store.add(interval);
        request.onsuccess = () => inserted.push(interval);
        request.onerror = event => {
          if (request.error?.name === "ConstraintError") {
            event.preventDefault();
            event.stopPropagation();
          }
        };
      });
      transaction.oncomplete = () => { db.close(); resolve(inserted); };
      transaction.onerror = () => { db.close(); reject(transaction.error); };
      transaction.onabort = () => { db.close(); reject(transaction.error); };
    });
  }

  async function putIntervals(intervals) {
    if (!intervals.length) return;
    const db = await open();
    return new Promise((resolve, reject) => {
      const transaction = db.transaction(STORE, "readwrite");
      const store = transaction.objectStore(STORE);
      intervals.forEach(interval => store.put(interval));
      transaction.oncomplete = () => { db.close(); resolve(); };
      transaction.onerror = () => { db.close(); reject(transaction.error); };
      transaction.onabort = () => { db.close(); reject(transaction.error); };
    });
  }

  async function putRemoteIntervals(intervals) {
    if (!intervals.length) return;
    const db = await open();
    return new Promise((resolve, reject) => {
      const transaction = db.transaction(REMOTE_STORE, "readwrite");
      const store = transaction.objectStore(REMOTE_STORE);
      intervals.forEach(interval => store.put(interval));
      transaction.oncomplete = () => { db.close(); resolve(); };
      transaction.onerror = () => { db.close(); reject(transaction.error); };
      transaction.onabort = () => { db.close(); reject(transaction.error); };
    });
  }

  async function getPending(limit = 250) {
    return (await getAll()).filter(item => item.syncState !== "SYNCED").slice(0, limit);
  }

  async function markSynced(recordIds) {
    if (!recordIds.length) return;
    const ids = new Set(recordIds);
    const records = (await getAll()).filter(item => ids.has(item.recordId)).map(item => ({ ...item, syncState: "SYNCED" }));
    await putIntervals(records);
  }

  async function markAcknowledged(acknowledgements) {
    if (!acknowledgements.length) return;
    const byId = new Map(acknowledgements.map(item => [item.record_id, item]));
    const records = (await getAll()).filter(item => byId.has(item.recordId)).map(item => ({
      ...item,
      syncState: "SYNCED",
      serverRevision: Number(byId.get(item.recordId).server_revision)
    }));
    await putIntervals(records);
    await clearAttempts(records.map(item => item.recordId));
  }

  async function getAll() {
    const db = await open();
    return new Promise((resolve, reject) => {
      const transaction = db.transaction(STORE, "readonly");
      const request = transaction.objectStore(STORE).getAll();
      request.onsuccess = () => resolve(request.result || []);
      request.onerror = () => reject(request.error);
      transaction.oncomplete = () => db.close();
    });
  }

  async function getRemoteAll() {
    const db = await open();
    return new Promise((resolve, reject) => {
      const transaction = db.transaction(REMOTE_STORE, "readonly");
      const request = transaction.objectStore(REMOTE_STORE).getAll();
      request.onsuccess = () => resolve(request.result || []);
      request.onerror = () => reject(request.error);
      transaction.oncomplete = () => db.close();
    });
  }

  async function applySyncPage(localIntervals, remoteIntervals, nextRevision, pulledState) {
    const db = await open();
    return new Promise((resolve, reject) => {
      const transaction = db.transaction([STORE, REMOTE_STORE, META_STORE], "readwrite");
      const local = transaction.objectStore(STORE);
      const remote = transaction.objectStore(REMOTE_STORE);
      localIntervals.forEach(interval => local.put(interval));
      remoteIntervals.forEach(interval => remote.put(interval));
      transaction.objectStore(META_STORE).put({ key: "accountCursor", value: Number(nextRevision), updatedAtUtc: Date.now() });
      transaction.objectStore(META_STORE).put({ key: "pulledState", value: pulledState, updatedAtUtc: Date.now() });
      transaction.oncomplete = () => { db.close(); resolve(); };
      transaction.onerror = () => { db.close(); reject(transaction.error); };
      transaction.onabort = () => { db.close(); reject(transaction.error); };
    });
  }

  async function getMeta(key, fallback = null) {
    const db = await open();
    return new Promise((resolve, reject) => {
      const transaction = db.transaction(META_STORE, "readonly");
      const request = transaction.objectStore(META_STORE).get(key);
      request.onsuccess = () => resolve(request.result?.value ?? fallback);
      request.onerror = () => reject(request.error);
      transaction.oncomplete = () => db.close();
    });
  }

  async function putConflicts(conflicts) {
    if (!conflicts.length) return;
    const db = await open();
    return new Promise((resolve, reject) => {
      const transaction = db.transaction(CONFLICT_STORE, "readwrite");
      conflicts.forEach(conflict => transaction.objectStore(CONFLICT_STORE).put(conflict));
      transaction.oncomplete = () => { db.close(); resolve(); };
      transaction.onerror = () => { db.close(); reject(transaction.error); };
    });
  }

  async function getConflicts() {
    const db = await open();
    return new Promise((resolve, reject) => {
      const transaction = db.transaction(CONFLICT_STORE, "readonly");
      const request = transaction.objectStore(CONFLICT_STORE).getAll();
      request.onsuccess = () => resolve((request.result || []).filter(item => !item.resolvedAtUtc));
      request.onerror = () => reject(request.error);
      transaction.oncomplete = () => db.close();
    });
  }

  async function resolveConflicts(recordIds) {
    if (!recordIds.length) return;
    const db = await open();
    return new Promise((resolve, reject) => {
      const transaction = db.transaction(CONFLICT_STORE, "readwrite");
      const store = transaction.objectStore(CONFLICT_STORE);
      recordIds.forEach(recordId => {
        const request = store.get(recordId);
        request.onsuccess = () => {
          if (request.result) store.put({ ...request.result, resolvedAtUtc: Date.now() });
        };
      });
      transaction.oncomplete = () => { db.close(); resolve(); };
      transaction.onerror = () => { db.close(); reject(transaction.error); };
    });
  }

  async function recordAttempts(records, collection, error) {
    if (!records.length) return;
    const db = await open();
    return new Promise((resolve, reject) => {
      const transaction = db.transaction(ATTEMPT_STORE, "readwrite");
      const store = transaction.objectStore(ATTEMPT_STORE);
      const now = Date.now();
      records.forEach(record => {
        const request = store.get(record.recordId);
        request.onsuccess = () => {
          const count = Number(request.result?.attemptCount || 0) + 1;
          store.put({
            recordId: record.recordId,
            collection,
            attemptCount: count,
            lastAttemptAtUtc: now,
            nextAttemptAtUtc: now + 30_000 * (2 ** Math.min(count, 6)),
            lastError: error?.message || String(error || "Upload failed")
          });
        };
      });
      transaction.oncomplete = () => { db.close(); resolve(); };
      transaction.onerror = () => { db.close(); reject(transaction.error); };
    });
  }

  async function clearAttempts(recordIds) {
    if (!recordIds.length) return;
    const db = await open();
    return new Promise((resolve, reject) => {
      const transaction = db.transaction(ATTEMPT_STORE, "readwrite");
      recordIds.forEach(id => transaction.objectStore(ATTEMPT_STORE).delete(id));
      transaction.oncomplete = () => { db.close(); resolve(); };
      transaction.onerror = () => { db.close(); reject(transaction.error); };
    });
  }

  async function activeDigitalSpanForDate(usageDate, includeRemote = true) {
    const local = (await getAll()).filter(item => item.usageDate === usageDate);
    const remote = includeRemote ? (await getRemoteAll()).filter(item => item.usageDate === usageDate) : [];
    return root.SyncOnCore.activeDigitalSpan([...local, ...remote]);
  }

  async function count() {
    const db = await open();
    return new Promise((resolve, reject) => {
      const transaction = db.transaction(STORE, "readonly");
      const request = transaction.objectStore(STORE).count();
      request.onsuccess = () => resolve(request.result || 0);
      request.onerror = () => reject(request.error);
      transaction.oncomplete = () => db.close();
    });
  }

  async function deleteOlderThan(cutoffUtc) {
    const db = await open();
    return new Promise((resolve, reject) => {
      let deleted = 0;
      const transaction = db.transaction(STORE, "readwrite");
      const index = transaction.objectStore(STORE).index("endTimeUtc");
      const request = index.openCursor(IDBKeyRange.upperBound(cutoffUtc, true));
      request.onsuccess = () => {
        const cursor = request.result;
        if (!cursor) return;
        cursor.delete(); deleted += 1; cursor.continue();
      };
      transaction.oncomplete = () => { db.close(); resolve(deleted); };
      transaction.onerror = () => { db.close(); reject(transaction.error); };
    });
  }

  async function clear() {
    const db = await open();
    return new Promise((resolve, reject) => {
      const transaction = db.transaction(STORE, "readwrite");
      transaction.objectStore(STORE).clear();
      transaction.oncomplete = () => { db.close(); resolve(); };
      transaction.onerror = () => { db.close(); reject(transaction.error); };
    });
  }

  root.SyncOnDb = {
    addIntervals, putIntervals, putRemoteIntervals, applySyncPage,
    getPending, markSynced, markAcknowledged, getAll, getRemoteAll,
    getMeta, putConflicts, getConflicts, resolveConflicts, recordAttempts, clearAttempts,
    activeDigitalSpanForDate, count, deleteOlderThan, clear
  };
})(typeof globalThis !== "undefined" ? globalThis : this);
