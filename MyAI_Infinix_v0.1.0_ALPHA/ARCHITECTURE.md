# MyAI — Инфиникс v0.1.0 ALPHA
Target: Infinix Note 30 Pro / Android 14.

This branch reuses the proven Control Bridge architecture while keeping the Realme branch untouched. Home server is deliberately deferred.

Layers: UI -> Agent -> Memory/Skills -> Tool Registry -> Control Bridge -> Android. Optional Online Gateway is isolated from local execution.

Current ALPHA verifies the Android control foundation (Accessibility, Home, Back, scroll), local memory boundary, and online boundary. Local LLM/model manager is the next module and is intentionally not bundled until its runtime and model format are validated on the actual device.
