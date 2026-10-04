# MyAI Infinix — AI Gateway protocol

The Android app is the **agent/executor**, not the place where an OpenAI API key is stored.

## Request

POST the configured gateway URL:

```json
{
  "session_id": "phone-1",
  "content": "Пожалуйста, открой YouTube"
}
```

## Response

The gateway returns normal assistant text and, when an action is required, a strict tool call:

```json
{
  "content": "Открываю YouTube.",
  "tool_call": "{"type":"tool_call","request_id":"42","tool":"open_app","arguments":{"name":"YouTube"}}"
}
```

The Android side validates the tool name against its allowlist. Unknown tools are rejected and are never executed.

After execution, the Android agent serializes the result as:

```json
{
  "type": "tool_result",
  "request_id": "42",
  "tool": "open_app",
  "success": true,
  "message": "Готово: открыл YouTube."
}
```

A future gateway can use OpenAI Responses API as its model backend and perform the normal tool-call loop. The API key must remain on the gateway/backend, never in the APK.


<!-- CI protocol revision: 2026-10-04 -->

<!-- sanity checker fix revision -->

<!-- gradle-format validation revision -->

<!-- sanity path revision -->

<!-- diagnostic path revision -->

<!-- workflow rewrite validation -->

<!-- final sanity newline guard -->

<!-- codec compile fix -->
