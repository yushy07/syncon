(function (root) {
  const config = root.SyncOnBackendConfig;
  const SESSION_KEY = "backendSession";

  async function request(path, { method = "GET", body, accessToken } = {}) {
    const response = await fetch(`${config.url}${path}`, {
      method,
      headers: {
        apikey: config.publishableKey,
        Authorization: `Bearer ${accessToken || config.publishableKey}`,
        "Content-Type": "application/json"
      },
      body: body === undefined ? undefined : JSON.stringify(body)
    });
    const text = await response.text();
    const payload = text ? JSON.parse(text) : null;
    if (!response.ok) {
      const error = new Error(payload?.message || payload?.msg || payload?.error_description || `Backend request failed (${response.status})`);
      error.status = response.status;
      error.payload = payload;
      throw error;
    }
    return payload;
  }

  async function saveSession(payload) {
    const session = {
      accessToken: payload.access_token,
      refreshToken: payload.refresh_token,
      expiresAt: Date.now() + Math.max(60, Number(payload.expires_in) || 3600) * 1000,
      userId: payload.user?.id || null,
      isAnonymous: payload.user?.is_anonymous === true
    };
    await chrome.storage.local.set({ [SESSION_KEY]: session });
    return session;
  }

  async function getSession() {
    return (await chrome.storage.local.get(SESSION_KEY))[SESSION_KEY] || null;
  }

  async function refreshSession(session) {
    if (!session?.refreshToken) return null;
    try {
      const payload = await request("/auth/v1/token?grant_type=refresh_token", {
        method: "POST",
        body: { refresh_token: session.refreshToken }
      });
      return saveSession(payload);
    } catch (error) {
      if (error.status === 400 || error.status === 401) await chrome.storage.local.remove(SESSION_KEY);
      throw error;
    }
  }

  async function ensureAnonymousSession() {
    let session = await getSession();
    if (session && session.expiresAt > Date.now() + 60_000) return session;
    if (session?.refreshToken) session = await refreshSession(session);
    if (session) return session;
    return saveSession(await request("/auth/v1/signup", { method: "POST", body: {} }));
  }

  async function rpc(name, body = {}) {
    let session = await ensureAnonymousSession();
    try {
      return await request(`/rest/v1/rpc/${name}`, { method: "POST", body, accessToken: session.accessToken });
    } catch (error) {
      if (error.status !== 401) throw error;
      session = await refreshSession(session);
      return request(`/rest/v1/rpc/${name}`, { method: "POST", body, accessToken: session.accessToken });
    }
  }

  root.SyncOnSupabase = { ensureAnonymousSession, getSession, rpc };
})(typeof globalThis !== "undefined" ? globalThis : this);
