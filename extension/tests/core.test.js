const test = require("node:test");
const assert = require("node:assert/strict");
const C = require("../lib/core.js");

test("normalizes trackable domains and rejects internal pages", () => {
  assert.equal(C.domainFromUrl("https://www.YouTube.com/watch?v=1"), "youtube.com");
  assert.equal(C.domainFromUrl("chrome://extensions"), null);
});

test("assigns activity before 4 AM to the previous usage day", () => {
  const timestamp = new Date(2026, 8, 19, 3, 30).getTime();
  assert.equal(C.usageDate(timestamp), "2026-09-18");
});

test("splits a browsing interval at the 4 AM boundary", () => {
  const start = new Date(2026, 8, 19, 3, 58).getTime();
  const end = new Date(2026, 8, 19, 4, 3).getTime();
  const segments = C.splitUsageInterval(start, end);
  assert.deepEqual(segments.map(item => [item.usageDate, item.durationMillis]), [
    ["2026-09-18", 2 * 60_000],
    ["2026-09-19", 3 * 60_000]
  ]);
});

test("evaluates strict and snoozed limits", () => {
  const limit = { enabled: true, limitMinutes: 60, style: "STRICT", snoozeMinutes: 5 };
  assert.equal(C.evaluateLimit(60 * 60_000, limit).shouldBlock, true);
  assert.equal(C.evaluateLimit(60 * 60_000, limit, { extraMinutes: 5 }).shouldBlock, false);
});

test("stable interval IDs are deterministic", () => {
  assert.equal(C.stableId(["device", "youtube.com", 1, 2]), C.stableId(["device", "youtube.com", 1, 2]));
  assert.notEqual(C.stableId(["device", "youtube.com", 1, 2]), C.stableId(["device", "youtube.com", 1, 3]));
});

test("automatically categorizes common services", () => {
  assert.equal(C.categoryForDomain("youtube.com"), "Entertainment");
  assert.equal(C.categoryForDomain("docs.google.com"), "Productivity");
});

test("merges Android and Chrome sources into one logical service", () => {
  const merged = C.mergeSourceTotals([
    { sourceIdentifier: "youtube.com", sourceType: "CHROME_DOMAIN", durationMillis: 10_000 },
    { sourceIdentifier: "com.google.android.youtube", sourceType: "ANDROID_APP", durationMillis: 20_000 }
  ]);
  assert.deepEqual(merged, [{ displayName: "YouTube", durationMillis: 30_000, logicalServiceId: "youtube" }]);
});

test("calculates overlap-adjusted active digital span", () => {
  assert.equal(C.activeDigitalSpan([
    { startTimeUtc: 0, endTimeUtc: 10, isDeleted: false },
    { startTimeUtc: 5, endTimeUtc: 15, isDeleted: false },
    { startTimeUtc: 20, endTimeUtc: 25, isDeleted: false }
  ]), 20);
});
