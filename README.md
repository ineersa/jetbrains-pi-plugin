# Pi IDE Bridge — JetBrains Plugin

> Bridges IDE state (current file, selection, workspace) to Pi via `~/.pi/ide/` JSON files.

## Overview

This plugin tracks your active IDE state and writes it to a JSON file that Pi reads to understand context: which file you're editing, what you've selected, and which project you're working in. It supports **multiple IDEs simultaneously** — each IDE instance gets its own file.

## Installation

### From source

```bash
git clone https://github.com/ineersa/jetbrains-pi-plugin.git
cd jetbrains-pi-plugin
./gradlew buildPlugin
```

The plugin ZIP is at `build/distributions/jetbrains-pi-plugin-*.zip`.

### Install in IDE

1. Open your JetBrains IDE
2. Go to **Settings/Preferences** → **Plugins** → **⚙️ (gear icon)** → **Install plugin from disk...**
3. Select the `jetbrains-pi-plugin-*.zip` file
4. Restart the IDE

### Development mode (run from source)

```bash
./gradlew runIde
```

This launches a sandbox IntelliJ IDEA with the plugin pre-installed. Changes to source code are picked up on the next `runIde` run.

## How It Works

### File Format

On first run, the plugin creates `~/.pi/ide/` and writes a JSON file named `<ide-name>-<instance-uuid>.json`:

```
~/.pi/ide/
  ├── intellij-abc123.json
  ├── goland-def456.json
  └── webstorm-ghi789.json
```

Each file contains compact JSON (shown here formatted for readability):

```json
{
  "pid": 12345,
  "ideName": "intellij",
  "ideVersion": "2024.1",
  "workspaceFolders": ["/home/user/my-project"],
  "currentFile": "/home/user/my-project/src/main.ts",
  "selection": {
    "text": "function hello() { ... }",
    "startLine": 10,
    "endLine": 15
  },
  "timestamp": 1714000000000
}
```

| Field | Description |
|-------|-------------|
| `pid` | OS process ID (for ancestry matching in Pi) |
| `ideName` | Lowercase IDE identifier (`intellij`, `goland`, `webstorm`, etc.) |
| `ideVersion` | IDE version string (e.g. `2024.1`) |
| `workspaceFolders` | Array of open project root paths |
| `currentFile` | Active editor file path (null if no file open) |
| `selection` | Selected text + line range (null when nothing selected) |
| `timestamp` | Unix ms, updated on every change |

### Write Strategy

- **Debounced**: 100ms delay after last change (avoids filesystem spam)
- **Atomic**: writes to temp file then renames (prevents partial reads)
- **Silent**: all errors caught and logged — never crashes the IDE
- **Cleanup**: file deleted when IDE closes

### Supported IDEs

The plugin auto-detects the running IDE from `ApplicationInfo`:

| IDE | `ideName` output |
|-----|------------------|
| IntelliJ IDEA | `intellij` |
| GoLand | `goland` |
| WebStorm | `webstorm` |
| PyCharm | `pycharm` |
| Rider | `rider` |
| CLion | `clion` |
| RubyMine | `rubymine` |
| PhpStorm | `phpstorm` |
| Android Studio | `android-studio` |
| DataGrip | `datagrip` |
| Other JetBrains | `jetbrains` |

## Testing

### Quick smoke test

```bash
# 1. Run the plugin in a sandbox IDE
./gradlew runIde

# 2. In the sandbox IDE, open any file and select some text

# 3. In another terminal, check the output file
ls -la ~/.pi/ide/
cat ~/.pi/ide/*.json
```

You should see a JSON file with the current file path and selection.

### Verify behavior

| Action | Expected result |
|--------|-----------------|
| Open a file | `currentFile` updates, `selection` is null |
| Select text | `selection` populated with text + line range |
| Deselect | `selection` becomes null, `currentFile` stays |
| Switch file | `currentFile` updates, `selection` cleared |
| Close all editors | File deleted from `~/.pi/ide/` |
| Open second IDE | Second `.json` file created with different UUID |

### Programmatic test

```bash
# Watch for changes in real-time
watch -n 1 'cat ~/.pi/ide/*.json 2>/dev/null || echo "no files yet"'
```

## Architecture

```
PiStartupActivity (project startup)
  ├── EditorFactoryListener → SelectionListener (per editor)
  └── FileEditorManagerListener → active file changes

PiIdeService (application-level singleton)
  ├── Debounced writes (100ms)
  ├── Atomic file I/O (temp + rename)
  └── Cleanup on dispose

PiPreferences (persistent state)
  └── Instance UUID (generated once)
```

## Pi-Side Matching

When Pi reads the IDE files, it matches using this priority:

1. **PID ancestry** — walk parent process tree, match `pid` field
2. **Workspace match** — check if `process.cwd()` is inside `workspaceFolders`
3. **Most recent** — highest `timestamp` fallback

## Security

- Files are only readable by the current user
- No network exposure (filesystem-only)
- No sensitive data (just paths and selection text)
- Plugin doesn't read file contents (tracks active file path only)

<!-- Plugin description -->
Pi IDE Bridge — tracks your active file, selection, and workspace in JetBrains IDEs. Writes state to `~/.pi/ide/` as JSON for Pi to read. Supports multiple IDEs simultaneously with per-instance files.
<!-- Plugin description end -->

## License

See [LICENSE](LICENSE) for details.
