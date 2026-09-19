(function (root, factory) {
  const api = factory();
  if (typeof module === "object" && module.exports) module.exports = api;
  root.SyncOnCore = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  const RESET_HOUR = 4;
  const DAY_MS = 86_400_000;

  function usageDate(timestamp = Date.now()) {
    const date = new Date(timestamp);
    if (date.getHours() < RESET_HOUR) date.setDate(date.getDate() - 1);
    return [date.getFullYear(), String(date.getMonth() + 1).padStart(2, "0"), String(date.getDate()).padStart(2, "0")].join("-");
  }

  function domainFromUrl(url) {
    try {
      const parsed = new URL(url);
      if (!["http:", "https:"].includes(parsed.protocol)) return null;
      return parsed.hostname.toLowerCase().replace(/^www\./, "");
    } catch (_) {
      return null;
    }
  }

  function categoryForDomain(domain) {
    const groups = {
      "Social Media": ["instagram.com", "facebook.com", "x.com", "twitter.com", "reddit.com", "linkedin.com", "tiktok.com", "threads.net"],
      "Communication": ["web.whatsapp.com", "discord.com", "slack.com", "teams.microsoft.com", "mail.google.com", "outlook.live.com"],
      "Entertainment": ["youtube.com", "netflix.com", "primevideo.com", "hotstar.com", "spotify.com", "twitch.tv"],
      "Education": ["khanacademy.org", "coursera.org", "udemy.com", "edx.org", "wikipedia.org"],
      "Productivity": ["docs.google.com", "notion.so", "github.com", "figma.com", "linear.app", "trello.com"]
    };
    for (const [category, domains] of Object.entries(groups)) {
      if (domains.some(item => domain === item || domain.endsWith(`.${item}`))) return category;
    }
    return "Other";
  }

  function stableId(parts) {
    const text = parts.join("|");
    let hashA = 2166136261;
    let hashB = 0x9e3779b9;
    for (let i = 0; i < text.length; i += 1) {
      hashA = Math.imul(hashA ^ text.charCodeAt(i), 16777619);
      hashB = Math.imul(hashB ^ text.charCodeAt(i), 2246822519);
    }
    return `${(hashA >>> 0).toString(16).padStart(8, "0")}-${(hashB >>> 0).toString(16).padStart(8, "0")}`;
  }

  function formatDuration(milliseconds) {
    const minutes = Math.floor(Math.max(0, milliseconds) / 60_000);
    if (minutes < 60) return `${minutes}m`;
    return `${Math.floor(minutes / 60)}h ${minutes % 60}m`;
  }

  function recentUsageDates(count, now = Date.now()) {
    const result = [];
    const base = new Date(`${usageDate(now)}T12:00:00`);
    for (let offset = count - 1; offset >= 0; offset -= 1) {
      const item = new Date(base.getTime() - offset * DAY_MS);
      result.push([item.getFullYear(), String(item.getMonth() + 1).padStart(2, "0"), String(item.getDate()).padStart(2, "0")].join("-"));
    }
    return result;
  }

  function splitUsageInterval(startTime, endTime) {
    if (!Number.isFinite(startTime) || !Number.isFinite(endTime) || endTime <= startTime) return [];
    const segments = [];
    let cursor = startTime;
    while (cursor < endTime) {
      const day = usageDate(cursor);
      const [year, month, date] = day.split("-").map(Number);
      const boundary = new Date(year, month - 1, date + 1, RESET_HOUR, 0, 0, 0).getTime();
      const segmentEnd = Math.min(endTime, boundary);
      if (segmentEnd <= cursor) break;
      segments.push({ usageDate: day, startTimeUtc: cursor, endTimeUtc: segmentEnd, durationMillis: segmentEnd - cursor });
      cursor = segmentEnd;
    }
    return segments;
  }

  function effectiveLimit(limit, dailyState) {
    return (limit?.limitMinutes || 0) + (dailyState?.extraMinutes || 0);
  }

  function evaluateLimit(usedMillis, limit, dailyState = {}) {
    if (!limit || limit.enabled === false || !Number.isFinite(limit.limitMinutes)) return null;
    const usedMinutes = Math.floor(usedMillis / 60_000);
    const limitMinutes = effectiveLimit(limit, dailyState);
    const remainingMinutes = Math.max(0, limitMinutes - usedMinutes);
    return {
      usedMinutes,
      limitMinutes,
      remainingMinutes,
      shouldWarn: remainingMinutes > 0 && remainingMinutes <= 5 && !dailyState.warningShown,
      shouldBlock: usedMinutes >= limitMinutes,
      style: limit.style || "STRICT",
      snoozeMinutes: limit.snoozeMinutes || 5
    };
  }

  return { RESET_HOUR, usageDate, domainFromUrl, categoryForDomain, stableId, formatDuration, recentUsageDates, splitUsageInterval, evaluateLimit };
});
