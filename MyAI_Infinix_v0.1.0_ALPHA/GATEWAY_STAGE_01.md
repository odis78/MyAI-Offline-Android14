# AI Gateway Stage 01

This experimental stage adds a dedicated Gateway screen.

Pipeline:
1. Android sends session_id + content to the configured gateway endpoint.
2. Gateway returns assistant content and an optional JSON tool_call.
3. MyAI validates the tool against the allowlisted AgentCommandParser.
4. Control Bridge executes the validated action.
5. MyAI records the request_id and tool_result in the journal.

The Android APK contains no OpenAI API key. The gateway endpoint is configured by the user. OpenAI integrations should use the Responses API on the server side; the Assistants API was sunset on August 26, 2026.

The stable infinix branch is not modified by this experiment.
