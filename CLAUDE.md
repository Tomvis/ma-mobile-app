@.claude/project.md

## Part of the Music Assistant workspace

This repo is the **official native mobile client** (Android/iOS) and one of four sibling repos that make up Music Assistant:

- **`server`** — the Python backend this app is a client of (providers, players, queues, streaming, WebSocket + REST API on `:8095`).
- **`frontend`** — the Vue web UI (the sibling client).
- **`models`** — the Python dataclasses that define the server's wire protocol.

When this repo is checked out inside the `music-assistant/` workspace, the canonical cross-repo overview is `../CLAUDE.md` — read it for the full picture. The points that matter most when editing *here*:

- **The wire protocol is a hand-maintained mirror.** This app does **not** consume the Python `models` package. The Kotlin DTOs under `composeApp/src/commonMain/kotlin/io/music_assistant/client/data/model/` (`client/` + `server/`) mirror the server's wire format by hand — exactly as `frontend` does in its TypeScript `interfaces.ts`. When a protocol change lands in `models`/`server`, mirror it here manually.
- **The built-in player rides Sendspin.** On-device playback implements the Sendspin streaming protocol (`composeApp/src/commonMain/kotlin/io/music_assistant/client/player/sendspin/`, WebRTC + WebSocket transports). The server side of Sendspin depends on the `aiosendspin` library, so Sendspin/metadata changes can span `server`, `aiosendspin`, and these Kotlin DTOs at once.
- **Branches are coordinated across repos.** Cross-repo feature work uses a matching branch name in every repo; this one is currently on `enhanced`. Run `git branch --show-current` in each sibling rather than assuming `dev`/`main`.
