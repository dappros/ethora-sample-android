// Maestro JS helper for `05-receive-text.yaml`.
//
// Posts a chat message as bob (the second test user) so alice — who's
// running in this Maestro session — sees it arrive in real-time via
// XMPP. Validates the receive path end-to-end without needing a
// second emulator.
//
// Maestro's `runScript:` step exposes:
//   `http`  — built-in HTTP client (`http.post(url, opts)`)
//   `json`  — JSON parsing helper
//   `output`— shared map writable by scripts, readable by flow steps
//   `MESSAGE` etc. — env vars from the calling flow
//
// Required env vars (passed via the `env:` block on `runScript:`):
//   MESSAGE              text of the message bob will send
//
// Required ambient secrets (set on the CI runner, propagated to
// Maestro via `env:` at the workflow level):
//   ETHORA_API_BASE_URL  https://api.chat-qa.ethora.com/v1
//   ETHORA_APP_TOKEN     JWT app token (must match the app behind
//                        MAESTRO_TEST_ROOM_JID)
//   MAESTRO_TEST_BOB_JWT bob's user-session JWT — obtained ahead of
//                        time via login-with-email; pass it in
//                        instead of the password so the helper
//                        doesn't have to authenticate every run
//   MAESTRO_TEST_ROOM_JID room JID alice and bob both belong to
//
// IMPORTANT: this script targets the *muc message* endpoint which on
// the chat.ethora.com cluster is `POST /messages`. If that endpoint
// is renamed or its request schema changes, this helper needs an
// update — keep it in lockstep with the SDK's outgoing send.

const apiBaseUrl = MAESTRO_API_BASE_URL || 'https://api.chat-qa.ethora.com/v1';
const appToken = MAESTRO_APP_TOKEN;
const userJwt = MAESTRO_TEST_BOB_JWT;
const roomJid = MAESTRO_TEST_ROOM_JID;
const message = MESSAGE || '(no message)';

if (!appToken || !userJwt || !roomJid) {
    output.error =
        'sendAsBob: missing one of MAESTRO_APP_TOKEN, MAESTRO_TEST_BOB_JWT, MAESTRO_TEST_ROOM_JID';
    throw new Error(output.error);
}

const response = http.post(`${apiBaseUrl}/messages`, {
    headers: {
        'Authorization': userJwt,
        'X-App-Token': appToken,
        'Content-Type': 'application/json',
    },
    body: JSON.stringify({
        roomJid,
        text: message,
    }),
});

if (response.status < 200 || response.status >= 300) {
    output.error = `sendAsBob: POST /messages returned ${response.status} — ${response.body}`;
    throw new Error(output.error);
}

output.messageId = (json.parse(response.body) || {}).messageId || '';
output.sentText = message;
