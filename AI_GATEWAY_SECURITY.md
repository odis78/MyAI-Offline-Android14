# Security rules

1. No OpenAI API key in Android source, resources, Gradle properties, APK, or GitHub artifacts.
2. The Android client talks only to a configured gateway endpoint.
3. The agent accepts only explicitly allowlisted actions.
4. Unknown model tools are rejected.
5. Tool results contain request IDs so the gateway can correlate execution.
6. Network timeouts are bounded: 8 seconds connect, 15 seconds read.
7. Offline/local command execution remains available when the gateway is unavailable.
