<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Pi IDE Bridge Changelog

## [Unreleased]

## 0.0.1 — 2025-04-25
### Added
- Core plugin: tracks current file, selection, and workspace folders
- Multi-IDE support: each IDE instance gets its own `<ide-name>-<uuid>.json` file
- Per-instance UUID stored in IDE preferences (generated once)
- Debounced writes (100ms) with atomic file I/O (temp + rename)
- Auto-detection of 11+ JetBrains IDEs (IntelliJ, GoLand, WebStorm, PyCharm, Rider, CLion, etc.)
- PID tracking for Pi-side ancestry matching
- Graceful cleanup: IDE state file deleted on IDE close
- Error-safe: all write failures silently caught, never crashes the IDE
