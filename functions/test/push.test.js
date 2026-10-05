const test = require("node:test");
const assert = require("node:assert/strict");
const { recipientOf, buildPush, isDeadToken, MAX_TEXT } = require("../push");

test("recipientOf returns the other participant", () => {
  assert.equal(recipientOf("alice_bob", "alice"), "bob");
  assert.equal(recipientOf("alice_bob", "bob"), "alice");
});

test("recipientOf rejects a sender outside the chat or a malformed id", () => {
  assert.equal(recipientOf("alice_bob", "eve"), null);
  assert.equal(recipientOf("alice", "alice"), null);
  assert.equal(recipientOf("a_b_c", "a"), null);
});

test("buildPush sends text as data, trimmed to the limit", () => {
  const push = buildPush("tok", "alice_bob", "bob", { senderId: "alice", senderName: "Alice", text: "x".repeat(900) });
  assert.equal(push.token, "tok");
  assert.equal(push.android.priority, "high");
  assert.equal(push.notification, undefined);
  assert.equal(push.data.type, "text");
  assert.equal(push.data.text.length, MAX_TEXT);
  assert.deepEqual(Object.values(push.data).map((v) => typeof v), Array(6).fill("string"));
});

test("buildPush leaves the text empty for photos and videos", () => {
  const push = buildPush("tok", "alice_bob", "bob", { senderId: "alice", senderName: "Alice", type: "video", text: "" });
  assert.equal(push.data.type, "video");
  assert.equal(push.data.text, "");
});

test("isDeadToken only matches errors that make a token useless", () => {
  assert.ok(isDeadToken("messaging/registration-token-not-registered"));
  assert.ok(isDeadToken("messaging/invalid-registration-token"));
  assert.ok(!isDeadToken("messaging/internal-error"));
});
