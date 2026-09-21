(function (root) {
  const api = root.SyncOnSupabase;
  const config = root.SyncOnBackendConfig;
  let socket = null;
  let heartbeat = null;
  let activeTopic = null;
  let onWake = null;
  let ref = 1;

  function stop() {
    if (heartbeat) clearInterval(heartbeat);
    heartbeat = null;
    if (socket) socket.close(1000, "SyncOn reconnect");
    socket = null;
    activeTopic = null;
  }

  async function ensure(callback) {
    onWake = callback || onWake;
    const connection = await root.SyncOnSync.getConnection();
    if (!["CONNECTED", "SYNCING", "OFFLINE"].includes(connection.status)) {
      stop();
      return false;
    }
    if (socket && socket.readyState <= WebSocket.OPEN) return true;

    const [session, syncContext] = await Promise.all([
      api.ensureAnonymousSession(),
      api.rpc("get_sync_context_v3")
    ]);
    const topic = syncContext?.topic;
    if (!topic) return false;
    activeTopic = `realtime:${topic}`;
    const url = `${config.url.replace(/^http/, "ws")}/realtime/v1/websocket?apikey=${encodeURIComponent(config.publishableKey)}&vsn=1.0.0`;
    socket = new WebSocket(url);
    socket.addEventListener("open", () => {
      const joinRef = String(ref++);
      socket.send(JSON.stringify({
        topic: activeTopic,
        event: "phx_join",
        payload: {
          config: {
            broadcast: { ack: false, self: false },
            presence: { key: "" },
            postgres_changes: [],
            private: true
          },
          access_token: session.accessToken
        },
        ref: joinRef,
        join_ref: joinRef
      }));
      heartbeat = setInterval(() => {
        if (socket?.readyState === WebSocket.OPEN) {
          socket.send(JSON.stringify({ topic: "phoenix", event: "heartbeat", payload: {}, ref: String(ref++) }));
        }
      }, 25_000);
    });
    socket.addEventListener("message", event => {
      let message;
      try { message = JSON.parse(event.data); } catch (_) { return; }
      if (message.event === "broadcast" && message.payload?.event === "sync_changed") {
        Promise.resolve(onWake?.()).catch(() => {});
      }
    });
    socket.addEventListener("close", () => {
      if (heartbeat) clearInterval(heartbeat);
      heartbeat = null;
      socket = null;
      activeTopic = null;
    });
    socket.addEventListener("error", () => socket?.close());
    return true;
  }

  root.SyncOnRealtime = { ensure, stop };
})(typeof globalThis !== "undefined" ? globalThis : this);
