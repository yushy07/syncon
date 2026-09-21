const qrNode = document.querySelector("#qr");
const statusNode = document.querySelector("#status");
const refreshButton = document.querySelector("#refresh");
const expiresNode = document.querySelector("#expires");
let timer = null;

function showQr(payload) {
  const code = qrcode(0, "M");
  code.addData(payload);
  code.make();
  qrNode.className = "qr";
  qrNode.innerHTML = code.createSvgTag(7, 4);
}

function render(connection) {
  const status = connection?.status || "LOCAL_ONLY";
  statusNode.className = "status-card";
  refreshButton.hidden = true;
  if (status === "PAIRING" && connection.qrPayload) {
    showQr(connection.qrPayload);
    statusNode.textContent = "Waiting for your phone to approve this browser…";
    const minutes = Math.max(1, Math.ceil((connection.expiresAt - Date.now()) / 60_000));
    expiresNode.textContent = `Expires in about ${minutes} minute${minutes === 1 ? "" : "s"}`;
    return;
  }
  if (SyncOnCore.isExtensionActivated(connection)) {
    qrNode.className = "qr complete";
    qrNode.textContent = "✓";
    statusNode.classList.add("connected");
    statusNode.textContent = status === "SYNCING"
      ? "Connected — bringing your devices up to date…"
      : status === "OFFLINE"
        ? "Connected. Sync is temporarily offline and will resume automatically."
        : "Connected. Chrome and Android will now sync automatically.";
    expiresNode.textContent = connection.lastSyncedAt ? `Last synced ${new Date(connection.lastSyncedAt).toLocaleTimeString()}` : "Ready to sync";
    document.querySelector("#dashboard").hidden = false;
    return;
  }
  document.querySelector("#dashboard").hidden = true;
  statusNode.classList.add("error");
  statusNode.textContent = status === "OFFLINE" ? "The connection is offline. Local tracking is still working." : connection.lastError || "This pairing code is no longer active.";
  refreshButton.hidden = false;
  expiresNode.textContent = "Generate a new one-time code";
}

async function load({ forceNew = false } = {}) {
  statusNode.textContent = "Preparing a secure pairing code…";
  let connection = forceNew ? null : await chrome.runtime.sendMessage({ type: "GET_CONNECTION" });
  if (!connection || ["LOCAL_ONLY", "EXPIRED", "CANCELLED", "NOT_FOUND", "ERROR", "REVOKED"].includes(connection.status)) {
    connection = await chrome.runtime.sendMessage({ type: "START_PAIRING" });
  }
  render(connection);
}

refreshButton.addEventListener("click", () => load({ forceNew: true }));
document.querySelector("#dashboard").addEventListener("click", () => chrome.runtime.openOptionsPage());
timer = setInterval(async () => render(await chrome.runtime.sendMessage({ type: "GET_CONNECTION" })), 2500);
window.addEventListener("beforeunload", () => clearInterval(timer));
load();
