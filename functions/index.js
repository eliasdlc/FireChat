const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { logger } = require("firebase-functions");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const { recipientOf, buildPush, isDeadToken } = require("./push");

initializeApp();

/**
 * On every new chat message, sends a push to the other participant's device,
 * whose token the app keeps in fcmTokens/{uid}. A token FCM no longer accepts
 * is deleted so it is not tried again.
 */
exports.notifyNewMessage = onDocumentCreated("chats/{chatId}/messages/{messageId}", async (event) => {
  const message = event.data?.data();
  if (!message) return;
  const { chatId } = event.params;
  const recipientId = recipientOf(chatId, message.senderId);
  if (!recipientId) return;

  const tokenRef = getFirestore().doc(`fcmTokens/${recipientId}`);
  const token = (await tokenRef.get()).get("token");
  if (!token) return;

  try {
    await getMessaging().send(buildPush(token, chatId, recipientId, message));
  } catch (error) {
    if (!isDeadToken(error.code)) throw error;
    logger.info("Deleting an expired FCM token", { recipientId });
    await tokenRef.delete();
  }
});
