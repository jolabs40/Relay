<img src="client/windows/packaging/relay-256.png" alt="" width="96" align="right">

# Relay

**Drive Claude Code without watching a terminal.** You type what you want done; Claude works in your
project; only what needs you comes back — its multiple-choice questions, permission requests, its
plan, and the summary at the end of each turn. Everything else (file reads, edits, thinking) is
reduced to a single status line.

Several sessions can run side by side, each in its own project. Sessions live in a small background
service, so closing the window stops nothing: you get a Windows notification when Claude needs you.

[Version française](README.fr.md)

![A question from Claude, with its options, in Relay for Windows](docs/relay-question.png)

## What comes back to you

| Card | When | What you can do |
|---|---|---|
| **Question** | Claude calls `AskUserQuestion` | pick one or several options, or type your own answer |
| **Permission** | a tool needs approval (outside *Full autonomy*) | allow, or deny with a reason sent to Claude |
| **Plan** | in *Plan first* mode, Claude submits its plan | approve and choose how to run it, or request changes |
| **Summary** | a turn ends | read it (Markdown), then answer or give the next instruction |

**Attachments**, as in the terminal: paste a screenshot or copied files with **Ctrl+V**, drop files
on the window, or use the paperclip. Images are shown to Claude directly (scaled down to what it
reads); other files — logs, PDFs, code — are handed over for Claude to read.

Four modes, switchable at any time: *Ask before acting*, *Accept edits*, *Plan first*, *Full
autonomy*. Questions still reach you in *Full autonomy*.

![A plan waiting for approval](docs/relay-plan.png)

## How it works

```
Relay (Windows app) ──WebSocket──▶ relay service (Python) ──Claude Agent SDK──▶ Claude Code
```

- **`relais/`** — the service. It runs each session through the
  [Claude Agent SDK](https://docs.claude.com/en/docs/agent-sdk/overview), turns questions,
  permissions and plans into events, and waits for your answer. It listens on `127.0.0.1` only.
- **`client/`** — Compose Multiplatform. `shared` holds the protocol, connection, view model and
  screens; `windows` adds the window, the tray icon and starts the service when needed. An Android
  client (home Wi-Fi) is planned and will reuse `shared`.

## Requirements

- Windows 10 or 11
- Python 3.12+ on the `PATH`
- [Claude Code](https://docs.claude.com/en/docs/claude-code/overview), signed in — Relay uses **your
  own** Claude Code authentication (or `ANTHROPIC_API_KEY` if set). Your use remains subject to
  Anthropic's terms.
- To build: JDK 21 (the one bundled with Android Studio works)

## Build and run

```bash
pip install -r relais/requirements.txt

cd client
./gradlew :windows:run                  # run
./gradlew :windows:createDistributable  # standalone app in windows/build/compose/binaries/main/app/
./gradlew :windows:packageMsi           # per-user installer
```

The app starts the service by itself. The path of `relais/` is recorded at build time: rebuild if
you move the folder.

## Configuration

On first start the service writes `%APPDATA%\Relay\config.json`:

| Key | Default | Meaning |
|---|---|---|
| `racine` | your home folder | **Claude only works below this folder**; its sub-folders are offered as projects. Point it at your projects folder. |
| `port` | `8787` | local port |
| `jeton` | random | access token required by every client |
| `reseau_local` | `false` | listen on the local network (for the future Android client) |

Logs: `%APPDATA%\Relay\relais.log`.

## Security

- The service listens on `127.0.0.1` unless `reseau_local` is enabled.
- Every connection needs the token; it is compared in constant time.
- Any request carrying an `Origin` header is refused, so no web page can drive Claude through
  `ws://127.0.0.1`.
- Sessions can only be opened below `racine`.

Remember what *Full autonomy* means: Claude acts on your files and runs commands without asking.

## Tests

```bash
cd relais && python -m pytest -q          # service, with a fake Claude
cd client && ./gradlew :shared:jvmTest    # protocol
./gradlew :shared:jvmTest -Pplanche=1     # renders every screen to shared/build/captures/
```

## Licence

[Apache 2.0](LICENSE). Relay is an independent project, not affiliated with Anthropic. Claude and
Claude Code are trademarks of Anthropic.
