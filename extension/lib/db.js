(function (root) {
  const DB_NAME = "syncon-extension";
  const DB_VERSION = 1;
  const STORE = "usage_intervals";

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

  root.SyncOnDb = { addIntervals, getAll, count, deleteOlderThan, clear };
})(typeof globalThis !== "undefined" ? globalThis : this);
