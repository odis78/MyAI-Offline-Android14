# MyAI Infinix — Agent Architecture

Target: Infinix Note 30 Pro / Android 14.

## Goal

MyAI is one integrated phone AI agent. It works offline and online, understands a user request, plans actions, operates Android, observes results, verifies them, and continues until the task is complete.

The existing Control Bridge is an internal Control Engine, not the final product.

## Layers

1. UI — chat, voice input/output, status and permissions.
2. AI Provider — replaceable local/offline model and optional online provider.
3. Agent Engine — task state, planning, tool calls, bounded retries and completion.
4. Observation — accessibility tree, visible text, current package/window and later permitted screenshots.
5. Control Engine — Accessibility, Android Intents, later optional Shizuku/ADB.
6. Skills — reusable app-specific and system tasks.
7. Memory — local conversation/task/preferences storage.
8. Update layer — model/skill updates; internet is optional for core operation.

## Execution loop

USER -> UNDERSTAND -> PLAN -> TOOL -> OBSERVE -> VERIFY -> NEXT TOOL -> DONE

A successful Android API call is not enough to declare a task complete. The next observation verifies the state.

## Offline/online policy

- Offline mode is first-class.
- Online mode is optional.
- Phone control remains local even when an online model is used.
- The app must not depend on one AI vendor.

## Safety

Calls, SMS, deleting files, sending messages, purchases, account changes and other externally visible/destructive actions require explicit confirmation.

## Initial tool families

- system.home
- system.back
- system.scroll
- system.open_settings
- apps.open
- screen.observe
- screen.find
- screen.tap
- screen.type

First milestone: reliable agent core plus a small, testable tool set — not a collection of buttons.
