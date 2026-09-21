const params = new URLSearchParams(location.search);
const domain = params.get("domain") || "this website";
const style = params.get("style") || "STRICT";
const snooze = Number(params.get("snooze")) || 5;
const used = Number(params.get("used")) || 0;
const limit = Number(params.get("limit")) || 0;
const category = params.get("category");

document.body.classList.toggle("soft", style === "SOFT");
document.querySelector("#blockIcon").innerHTML = style === "SOFT"
  ? '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M7 2h10v3c0 2.1-1.1 4.1-3 5.2 1.9 1.1 3 3.1 3 5.2V19h2v2H5v-2h2v-3.6c0-2.1 1.1-4.1 3-5.2C8.1 9.1 7 7.1 7 5V2Zm2 2v1c0 1.8 1.2 3.5 3 4 1.8-.5 3-2.2 3-4V4H9Zm3 7.3c-1.8.5-3 2.2-3 4V19h6v-3.7c0-1.8-1.2-3.5-3-4Z"/></svg>'
  : '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M17 8h-1V6a4 4 0 0 0-8 0v2H7a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-9a2 2 0 0 0-2-2Zm-7-2a2 2 0 1 1 4 0v2h-4V6Zm3 9.7V18h-2v-2.3a2 2 0 1 1 2 0Z"/></svg>';
document.querySelector("#title").textContent = style === "SOFT" ? "You've reached today's limit" : `${domain} is blocked`;
document.querySelector("#message").textContent = style === "SOFT"
  ? `You've reached your limit on ${domain}. You can take a short break or snooze for a bit longer.`
  : `You've reached your daily limit. ${domain} will be available again at 4:00 AM.`;
document.querySelector("#tagline").textContent = style === "SOFT"
  ? "“A few extra minutes. Use them well.”"
  : "“Take a break. You’ll be back stronger.”";
document.querySelector("#usage").textContent = `${used}m / ${limit}m`;

const actions = document.querySelector("#actions");
if (style === "SOFT") {
  const snoozeButton = document.createElement("button");
  snoozeButton.className = "pill";
  snoozeButton.textContent = `Snooze + ${snooze} min`;
  snoozeButton.addEventListener("click", async () => {
    await chrome.runtime.sendMessage(category
      ? { type: "SNOOZE_CATEGORY", category, minutes: snooze }
      : { type: "SNOOZE_DOMAIN", domain, minutes: snooze });
    history.back();
  });
  actions.appendChild(snoozeButton);
}

const leaveButton = document.createElement("button");
leaveButton.className = "pill outlined";
leaveButton.textContent = "Leave website";
leaveButton.addEventListener("click", () => chrome.tabs.getCurrent(tab => chrome.tabs.remove(tab.id)));
actions.appendChild(leaveButton);
