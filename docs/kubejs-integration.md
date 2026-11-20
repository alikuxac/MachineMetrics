# KubeJS Integration

Machine Metrics exposes two KubeJS client events so modpack authors can add custom HUD lines from arbitrary block entities and gate the telemetry HUD behind progression/stage checks. KubeJS is a **soft dependency**: the mod behaves exactly as before when KubeJS is absent.

---

## How it works

The mod jar ships `kubejs.plugins.txt`, which KubeJS uses to discover `MachineMetricsKubeJsPlugin`. The plugin class is only loaded when KubeJS is present, and core rendering code talks to it through the loader-agnostic `KubeJsBridge` interface (`compat/kubejs/`). No KubeJS class is ever referenced by the always-loaded HUD code, so there is no hard runtime dependency.

Both events are **client events**. Register handlers in `kubejs/client_scripts/`. Reload with `/kubejs reload client_scripts`.

---

## `MachineMetricsEvents.registerBlockMetrics`

Fired for every hovered (or pinned) block while the HUD is active. Read custom NBT or block entity fields, then call `addLine` to append rows to the telemetry HUD.

### Event properties

| Property | Type | Description |
| :--- | :--- | :--- |
| `level` | `Level` | Client level containing the target |
| `pos` | `BlockPos` | Target block position |
| `blockEntity` | `BlockEntity` | Target block entity, or `null` |
| `face` | `Direction` | Targeted face, or `null` |
| `blockId` | `String` | Block description id, e.g. `block.modmachine.name` |
| `data` | `CompoundTag` | Persisted block entity NBT (empty when no block entity) |

### Methods

| Method | Description |
| :--- | :--- |
| `addLine(label, value)` | Append a white HUD row |
| `addLine(label, value, color)` | Append a row with an RGB int color |
| `addLine(label, value, "#RRGGBB")` | Append a row with a hex color string |

### Example

```js
MachineMetricsEvents.registerBlockMetrics(event => {
    const nbt = event.data
    if (nbt.isEmpty()) return

    const progress = nbt.getInt('progress')
    if (progress > 0) {
        event.addLine('Progress', `${progress}%`, '#4FC3F7')
    }

    const owner = nbt.getString('owner')
    if (owner) {
        event.addLine('Owner', owner)
    }
})
```

Rows appear below the standard telemetry lines. If the block has no built-in telemetry, custom rows still render on their own.

---

## `MachineMetricsEvents.shouldInspect`

Fired each HUD frame before targeting, with the current hovered position (`pos` is `null` when the crosshair is not on a block). Call `event.cancel()` to suppress the entire HUD (hover, pinned overlay, and outbound requests). Leaving the event untouched allows inspection.

### Event properties

| Property | Type | Description |
| :--- | :--- | :--- |
| `player` | `Player` | Local player |
| `pos` | `BlockPos` | Hovered block position, or `null` |
| `dimension` | `String` | Dimension id, e.g. `minecraft:overworld` |

### Example (GameStage gate)

```js
MachineMetricsEvents.shouldInspect(event => {
    if (!event.player.stages.has('machinery_2')) {
        event.cancel()
    }
})
```

`player.stages` assumes a stage mod such as GameStages that exposes stages on players; substitute the modpack's own stage API as needed. Any script error is isolated and falls back to allowing inspection, so a broken script cannot lock the HUD permanently.

---

## Performance notes

- `registerBlockMetrics` runs at most once per rendered frame per target. Keep handlers cheap and cache parsed values in the script when possible.
- `shouldInspect` runs once per frame. Return early unless the stage/dimension actually needs gating.
- Both events are wrapped in `try/catch`; failures are swallowed rather than crashing rendering.
