# Configuration Guide

Machine Metrics settings are split between client-side HUD options and server-side metric permissions.

---

## In-Game HUD Editor

You can configure HUD placement directly from the UI without entering a world:
1. **From Main Menu:** Go to `Mods` -> select `Machine Metrics` -> click **Config**.
2. **In-Game:** Open the pause menu (`ESC`) and click **Machinemetrics HUD** in the top-right corner.

### Controls:
- **Move:** Click and drag the HUD anywhere on screen. Offsets calculate relative to the active anchor.
- **Anchor:** Click `Anchor: ...` to cycle points (`TOP_LEFT`, `TOP_RIGHT`, `BOTTOM_LEFT`, `BOTTOM_RIGHT`, `CROSSHAIR`).
- **Reset:** Click `Reset to Default` to restore offset defaults.
- **Block Name:** Click `Block Name: SHOW / HIDE` to toggle the machine title line.
- **Presets:** Click `Preset: ...` to pick a starting layout:
   - **Full Diagnostic:** Shows all metrics (Title, Status, Items, Fluids, Energy, and mod metrics if installed).
   - **Minimalist Bar:** Compact single-line template mode.
   - **Create Only:** Kinetic diagnostics (Speed, Direction, Network SU, Headroom) (requires Create).
   - **Chemicals Only:** Chemical transfer rates and tank capacities (requires Mekanism).
   - **Energy Only:** Power rate (FE/t).
   - **Items Only:** Item transfer rate (Items/s).
   - **Fluids Only:** Fluid transfer rates (mB/s, B/s) without chemicals.

---

## Client Config (`config/machinemetrics-client.toml`)

```toml
[hud]
  # Enable or disable HUD
  hud_enabled = true
  # Anchor: TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CROSSHAIR
  hud_anchor = "TOP_LEFT"
  # Horizontal offset from anchor
  hud_x_offset = 10
  # Vertical offset from anchor
  hud_y_offset = 10
  # HUD scale multiplier (0.5 to 3.0)
  hudScale = 1.0
  # Background color (hex ARGB/RGB)
  background_color = "#90000000"
  # Border color (hex ARGB/RGB)
  border_color = "#FF00FFCC"

[elements]
  # Show block name
  show_machine_title = true
  # Hide title when Jade/Waila/WTHIT/TOP is present
  auto_hide_title_with_info_mods = true
  # Show status (OPTIMAL, IDLE, etc.)
  show_status_badge = true
  # Show items throughput (Items/s)
  show_items_throughput = true
  # Show fluids throughput (mB/s)
  show_fluids_throughput = true
  # Show Mekanism chemicals throughput
  show_chemicals_throughput = true
  # Show Create speed (RPM)
  show_speed = true
  # Show rotation direction (CW/CCW)
  show_rotation_direction = true
  # Show local block stress impact
  show_local_impact = false
  # Show total network stress & capacity (SU)
  show_network_stress = true
  # Show remaining network capacity
  show_headroom = true
  # Show Forge Energy rate (FE/t)
  show_energy_fe = true
  # Show per-side rates when active
  show_sides = true

[formatting]
  # Enable single-line compact HUD
  compact_mode = false
  # Placeholders: {name}, {speed}, {dir}, {stress}, {cap}, {headroom}, {load_pct}, {state}, {items}, {fluids}
  compact_template = "{name} | {speed} RPM {dir} | {stress}/{cap} SU ({load_pct}%) | {state}"

[colors]
  headerColor = "#00FFCC"
  itemsColor = "#FFD54F"
  fluidsColor = "#4FC3F7"
  chemicalsColor = "#AEEA00"
  kineticColor = "#FF9E59"
  energyPositiveColor = "#69F0AE"
  energyNegativeColor = "#FF5252"
  energyNeutralColor = "#E040FB"
  sidesColor = "#69F0AE"
  stateOptimalColor = "#55FF55"
  stateStarvedColor = "#FFFF55"
  stateCloggedColor = "#FF5555"
  stateOverloadedColor = "#FF3333"
  stateIdleColor = "#AAAAAA"
```

---

## Compact Template Placeholders

When `compact_mode = true`, the HUD renders as a single line using `compact_template`:

| Placeholder | Output | Example |
| :--- | :--- | :--- |
| `{name}` | Target block name | `Crushing Wheel`, `Electric Furnace` |
| `{speed}` | Rotational speed (RPM) | `128.0` |
| `{dir}` | Rotation direction | `CW ↻`, `CCW ↺`, `—` |
| `{stress}` | Kinetic stress draw | `8.2k`, `65.5k` |
| `{cap}` | Network kinetic capacity | `65.5k`, `1.2M` |
| `{headroom}` | Remaining capacity | `+57.3k`, `0` |
| `{load_pct}` | Network stress load percentage | `12%`, `100%` |
| `{state}` | Bottleneck state tag | `§aOPTIMAL`, `§cOVERSTRESSED`, `§7IDLE` |
| `{items}` | Item transfer rate | `12.5 /s`, `0.0 /s` |
| `{fluids}` | Fluid transfer rate | `250.0 mB/s`, `1.20 B/s` |

---

## Server Config (`config/machinemetrics-server.toml`)

Servers can restrict which metrics get sampled and sent to clients:

```toml
[features]
  # Allow sending item transfer throughput metrics to clients (default: true)
  allow_items_telemetry = true
  # Allow sending fluid transfer throughput metrics to clients (default: true)
  allow_fluids_telemetry = true
  # Allow sending Forge Energy (FE/t) rate metrics to clients (default: true)
  allow_energy_telemetry = true
  # Allow sending Create kinetic rotational metrics to clients (default: true)
  allow_kinetic_telemetry = true
  # Allow sending Mekanism chemical metrics to clients (default: true)
  allow_chemicals_telemetry = true
  # Allow sending per-side throughput metrics to clients (default: true)
  allow_side_metrics_telemetry = true

[debug]
  # Enable telemetry debug logging in game console (default: false)
  enableDebugLogging = false
```

When a metric is disabled on the server, the client shows `[Disabled by the server]` in place of values, and the server skips querying those capabilities to save tick time. Mod metrics (Create, Mekanism) still require the corresponding mod installed on both server and client.
