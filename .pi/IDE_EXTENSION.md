# Pi IDE Extension — Spec

## Overview

JetBrains plugin that bridges IDE state (current file, selection, workspace) to Pi via a shared directory of JSON files. Each IDE instance writes its own file; Pi reads all files and picks the most relevant one.

## Directory Structure

```
~/.pi/ide/
  ├── intellij-abc123.json
  ├── goland-def456.json
  └── webstorm-ghi789.json
```

- Created automatically on first plugin run
- Each file named `<ide-name>-<instance-id>.json`
- `<instance-id>` = random UUID, generated once per IDE installation (stored in IDE prefs)
- File deleted when IDE closes

## JSON Schema

```json
{
  "pid": 12345,
  "ideName": "intellij",
  "ideVersion": "2025.1",
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

Fields:
- `pid` — OS process ID (for ancestry matching)
- `ideName` — lowercase IDE identifier (`intellij`, `pycharm`, `goland`, `webstorm`, etc.)
- `ideVersion` — IDE version string
- `workspaceFolders` — array of open project roots
- `currentFile` — currently active editor file path (null if no file open)
- `selection` — null when no selection, object with text + line range when selected
- `timestamp` — Unix ms, updated on every change

## Plugin Architecture

### Core Components

1. **`PiSelectionService`** — Application-level service
   - Manages file I/O
   - Debounces writes (100ms)
   - Cleans up on dispose

2. **`PiStartupActivity`** — Project startup hook
   - Registers listeners on all editors
   - Sets up selection + file change listeners

3. **`PiPreferences`** — Persistent settings
   - Stores instance UUID
   - Generated on first run

### Listeners

- **`SelectionListener`** — fires on selection change in any editor
- **`FileEditorManagerListener`** — fires when active file changes
- **`EditorFactoryListener`** — catches newly opened editors

### Write Strategy

- **Debounced**: 100ms delay after last change (avoids spam)
- **Atomic**: write to temp file, then rename (prevents partial reads)
- **Silent**: catch and log all errors, never crash IDE

## Pi-Side Matching

### Priority Order

1. **PID Ancestry** — walk parent process tree, match against `pid` field
   - Works when Pi runs from IDE's built-in terminal
   - Most accurate match

2. **Workspace Match** — check if `process.cwd()` is inside any `workspaceFolders`
   - Works for external terminals
   - Handles multiple IDEs with overlapping projects

3. **Most Recent** — pick file with highest `timestamp`
   - Fallback when no clear match
   - Still useful for showing "something is active"

### Implementation

```typescript
async function findMatchingIde(): Promise<IdeFile | null> {
  const ideDir = join(homedir(), '.pi', 'ide')
  const files = await readdir(ideDir)
  
  for (const file of files) {
    if (!file.endsWith('.json')) continue
    const data = await readJson(join(ideDir, file))
    
    // Priority 1: PID ancestry match
    if (isParentPid(data.pid)) return data
    
    // Priority 2: Workspace match
    if (workspaceContains(data.workspaceFolders, process.cwd())) return data
  }
  
  // Priority 3: Most recent
  const all = await Promise.all(files.map(f => readJson(join(ideDir, f))))
  return all.sort((a, b) => b.timestamp - a.timestamp)[0] || null
}
```

## Features

### Phase 1 (MVP)
- [x] Current file tracking
- [x] Selection tracking
- [x] Multi-IDE support
- [x] Pi-side matching logic
- [ ] `@currentFile` slash command (reads matched IDE's current file)
- [ ] `@selection` slash command (imports selection text)
- [ ] Footer display showing active IDE state

### Phase 2 (Nice-to-have)
- [ ] `@diagnostics` — import IDE errors/warnings for current file
- [ ] `@openFiles` — list all open editor tabs
- [ ] Real-time sync (optional WebSocket upgrade path)
- [ ] Multiple selection support (multi-cursor)

## Pi Integration Points

### New Commands
- `@currentFile` — imports current file content from matched IDE
- `@selection` — imports selected text
- `@ide` — shows current IDE state (file, selection, workspace)

### Footer Display
```
🔗 IntelliJ: src/main.ts (lines 10-15 selected)
```

### Session Context
- Auto-include current file path in session metadata
- Optional: auto-include selection in conversation context

## Error Handling

- Plugin never crashes IDE on write errors
- Pi gracefully handles missing/empty directory
- Stale files cleaned up (IDE deletes on close, Pi can prune files > 24h old)
- JSON parse errors silently skipped

## Security

- Files only readable by current user (0600 permissions)
- No network exposure (filesystem-only)
- No sensitive data (just paths and selection text)
- Plugin doesn't read file contents (just tracks active file path)
