# MyAI Infinix — AI Gateway protocol

The Android app is the **agent/executor**, not the place where an OpenAI API key is stored.

## 1. User request

POST the configured gateway URL:

```json
{
  "session_id": "phone-1",
  "content": "Пожалуйста, открой YouTube"
}
```

## 2. Model response

The gateway returns normal assistant text and, when an action is required, a strict tool call. `tool_call` is a JSON **string** in this first protocol revision:

```json
{
  "content": "Открываю YouTube.",
  "tool_call": "{\"type\":\"tool_call\",\"request_id\":\"42\",\"tool\":\"open_app\",\"arguments\":{\"name\":\"YouTube\"}}"
}
```

Android parses the tool call with a real JSON parser and validates the tool name against the allowlist. Unknown or malformed tools are rejected and are never executed.

## 3. Tool result

After execution Android POSTs the result back to the same configured Gateway URL:

```json
{
  "session_id": "phone-1",
  "type": "tool_result",
  "tool_result": {
    "type": "tool_result",
    "request_id": "42",
    "tool": "open_app",
    "success": true,
    "message": "Готово: открыл YouTube."
  }
}
```

The Gateway may answer this tool-result request with normal assistant text and an optional next `tool_call`. Android validates that next call again before executing it.

## 4. Security rules

- No OpenAI API key is stored in the APK.
- Only the configured Gateway endpoint is contacted.
- Android executes only the allowlisted actions.
- Malformed JSON, unknown tools, and missing required arguments are rejected.
- Request IDs are preserved across tool results.
- Network timeouts remain bounded: 8 seconds connect, 15 seconds read.
- Offline/local command execution remains available if the Gateway is unavailable.

A future Gateway can use the OpenAI Responses API as its model backend. The API key must remain on the gateway/backend, never in the APK.
