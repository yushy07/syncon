const params = new URLSearchParams(location.search);
const domain = params.get("domain") || "this website";
const style = params.get("style") || "STRICT";
const snooze = Number(params.get("snooze")) || 5;
const used = Number(params.get("used")) || 0;
const limit = Number(params.get("limit")) || 0;
const category = params.get("category");

document.querySelector("#title").textContent = `${domain} is paused`;
document.querySelector("#message").textContent = style === "SOFT"
  ? "You reached the limit you set. Take a break, or use a short extension if this visit is intentional."
  : "You reached your strict daily limit. This website will remain paused until the next usage day.";
document.querySelector("#usage").textContent = `${used}m / ${limit}m`;

const actions = document.querySelector("#actions");
if (style === "SOFT") {
  const snoozeButton = document.createElement("button");
  snoozeButton.className = "pill";
  snoozeButton.textContent = `Use ${snooze} more minutes`;
  snoozeButton.addEventListener("click", async () => {
    await chrome.runtime.sendMessage(category
      ? { type: "SNOOZE_CATEGORY", category, minutes: snooze }
      : { type: "SNOOZE_DOMAIN", domain, minutes: snooze });
    history.back();
  });
  actions.appendChild(snoozeButton);
}

const leaveButton = document.createElement("button");
leaveButton.className = "pill secondary";
leaveButton.textContent = "Leave website";
leaveButton.addEventListener("click", () => chrome.tabs.getCurrent(tab => chrome.tabs.remove(tab.id)));
actions.appendChild(leaveButton);
