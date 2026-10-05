/**
 * Pure helpers for the new-message push, kept apart from Firebase so they can
 * be tested with node --test.
 */

const MAX_TEXT = 500;

/** The other participant of a two-person chat id ("uidA_uidB"), or null. */
function recipientOf(chatId, senderId) {
  const participants = chatId.split("_");
  if (participants.length !== 2 || !participants.includes(senderId)) return null;
  return participants.find((uid) => uid !== senderId) ?? null;
}

/**
 * The data-only FCM message the Android app turns into a notification.
 * Data-only, so the app decides whether to show it (not for the chat on screen).
 */
function buildPush(token, chatId, recipientId, message) {
  const type = message.type ?? "text";
  const text = type === "text" ? String(message.text ?? "").slice(0, MAX_TEXT) : "";
  return {
    token,
    android: { priority: "high" },
    data: {
      chatId,
      recipientId,
      senderId: String(message.senderId ?? ""),
      senderName: String(message.senderName ?? ""),
      type,
      text,
    },
  };
}

/** FCM errors that mean the stored token will never work again. */
function isDeadToken(code) {
  return code === "messaging/registration-token-not-registered" ||
    code === "messaging/invalid-registration-token";
}

module.exports = { recipientOf, buildPush, isDeadToken, MAX_TEXT };
