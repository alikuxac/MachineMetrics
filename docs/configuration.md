# Configuration Guide

Machine Metrics settings are split between client HUD options and server telemetry permissions.

---

## In-Game HUD Editor

Configure HUD placement and presets directly in-game without editing config files:
- **Main Menu**: Open `Mods` > select `Machine Metrics` > click **Config**.
- **In-Game**: Open the pause menu (`ESC`) and click **Machinemetrics HUD** in the top-right corner.

### Controls

| Control | Action | Description |
| :--- | :--- | :--- |
| **Drag HUD** | Click and drag | Moves HUD across the screen. Offsets calculate from the active anchor. |
| **Anchor** | Click button | Cycles anchor points: `TOP_LEFT`, `TOP_RIGHT`, `BOTTOM_LEFT`, `BOTTOM_RIGHT`, `CROSSHAIR`. |
| **Reset** | Click button | Resets offsets to defaults (`x: 10`, `y: 10`). |
| **Block Name** | Click toggle | Toggles the targeted block name line on or off. |
| **Preset** | Click button | Applies a layout preset. |

### Presets

- **Full Diagnostic**: Displays title, status badges, items, fluids, energy, and all installed mod metrics.
- **Minimalist Bar**: Enables compact single-line mode.
- **Create Only**: Shows rotational speed, direction, network stress, and headroom (requires Create).
- **Chemicals Only**: Shows chemical throughput and container capacities (requires Mekanism).
- **Energy Only**: Shows Forge Energy throughput (`FE/t`).
- **Items Only**: Shows item transfer rate (`Items/s`).
- **Fluids Only**: Shows fluid transfer rate (`mB/s` or `B/s`).

---

## Client Config (`config/machinemetrics-client.toml`)

### HUD Placement (`[hud]`)

```toml
[hud]
  # Enable or disable HUD rendering
  hud_enabled = true
  # Anchor point: TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CROSSHAIR
  hud_anchor = "TOP_LEFT"
  # Horizontal and vertical offsets from anchor (range: -4000 to 4000)
  hud_x_offset = 10
  hud_y_offset = 10
  # HUD scale multiplier (range: 0.5 to 3.0)
  hudScale = 1.0
  # Background and border colors in ARGB/RGB hex
  background_color = "#90000000"
  border_color = "#FF00FFCC"
```

### Metric Toggles (`[elements]`)

| Config Key | Default | Description |
| :--- | :--- | :--- |
| `show_machine_title` | `true` | Shows targeted block or machine name. |
| `auto_hide_title_with_info_mods` | `true` | Hides title line when Jade, WAILA, WTHIT, or The One Probe is installed. |
| `show_status_badge` | `true` | Shows bottleneck state (`OPTIMAL`, `STARVED`, `CLOGGED`, `IDLE`). |
| `show_items_throughput` | `true` | Shows item transfer throughput (`Items/s`). |
| `show_fluids_throughput` | `true` | Shows fluid transfer throughput (`mB/s` or `B/s`). |
| `show_chemicals_throughput` | `true` | Shows Mekanism chemical throughput. |
| `show_speed` | `true` | Shows Create rotational speed (`RPM`). |
| `show_rotation_direction` | `true` | Shows Create rotation direction (`CW ↻` or `CCW ↺`). |
| `show_local_impact` | `false` | Shows local block kinetic stress draw. |
| `show_network_stress` | `true` | Shows total network kinetic stress and capacity (`SU`). |
| `show_headroom` | `true` | Shows remaining network kinetic capacity. |
| `show_energy_fe` | `true` | Shows Forge Energy transfer rate (`FE/t`). |
| `show_sides` | `true` | Shows directional per-face transfer rates when active. |
| `show_ae2_power` | `true` | Shows AE2 network power rate and buffer fill percentage. |
| `show_ae2_stability` | `true` | Shows AE2 grid stability state and brownout warnings. |
| `auto_hide_ae2_channels_with_info_mods` | `true` | Hides AE2 channel count if Jade or WTHIT is installed. |
| `show_ae2_channels` | `true` | Shows AE2 node used and max channels. |
| `ae2_power_unit` | `"AUTO"` | Power unit for AE2 metrics: `"AUTO"` (matches AE2 client button), `"AE"`, or `"FE"`. |

### Colors (`[colors]`)

Colors accept `#RRGGBB` or `#AARRGGBB` hex values:

```toml
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
  ae2PowerColor = "#A259FF"
  ae2BrownoutColor = "#FF3333"

  # Bottleneck states
  stateOptimalColor = "#55FF55"
  stateStarvedColor = "#FFFF55"
  stateCloggedColor = "#FF5555"
  stateOverloadedColor = "#FF3333"
  stateIdleColor = "#AAAAAA"
```

---

## Compact Mode (`[formatting]`)

Single-line mode renders all active metrics as one continuous line formatted with `compact_template`:

```toml
[formatting]
  compact_mode = false
  compact_template = "{name} | {speed} RPM {dir} | {stress}/{cap} SU ({load_pct}%) | {state}"
```

### Template Placeholders

| Placeholder | Output | Example |
| :--- | :--- | :--- |
| `{name}` | Target block name | `Crushing Wheel`, `Electric Furnace` |
| `{speed}` | Rotational speed (RPM) | `128.0` |
| `{dir}` | Rotation direction | `CW ↻`, `CCW ↺`, `—` |
| `{stress}` | Kinetic stress draw | `8.2k`, `65.5k` |
| `{cap}` | Network kinetic capacity | `65.5k`, `1.2M` |
| `{headroom}` | Remaining kinetic capacity | `+57.3k`, `0` |
| `{load_pct}` | Network stress percentage | `12%`, `100%` |
| `{state}` | Bottleneck state badge with color code | `§aOPTIMAL`, `§cOVERLOADED`, `§7IDLE` |
| `{items}` | Item transfer rate | `12.5 /s`, `0.0 /s` |
| `{fluids}` | Fluid transfer rate | `250.0 mB/s`, `1.20 B/s` |
| `{ae_power}` | AE2 network net power rate | `-3.4k AE/t`, `+1.2k AE/t` |
| `{ae_drain}` | AE2 grid power drain | `180.0 AE/t` |
| `{ae_subnets}` | AE2 connected subnet count and drain | `2 (+40.0 AE/t)` |
| `{ae_state}` | AE2 grid stability state | `STABLE`, `BROWNOUT`, `DISCHARGING` |

---

## Server Config (`config/machinemetrics-server.toml`)

Servers control which telemetry data is sampled and transmitted to connected clients:

```toml
[features]
  # Send item transfer throughput metrics to clients
  allow_items_telemetry = true
  # Send fluid transfer throughput metrics to clients
  allow_fluids_telemetry = true
  # Send Forge Energy (FE/t) rate metrics to clients
  allow_energy_telemetry = true
  # Send Create rotational speed, stress, and capacity to clients
  allow_kinetic_telemetry = true
  # Send Mekanism chemical throughput and capacities to clients
  allow_chemicals_telemetry = true
  # Send directional per-face throughput metrics to clients
  allow_side_metrics_telemetry = true
  # Send AE2 grid power, subnets, and stability states to clients
  allow_ae2_telemetry = true

[debug]
  # Log telemetry queries and payloads to server console
  enableDebugLogging = false
```

### Behavior when disabled

When a metric is disabled on the server:
- The server stops sampling capability deltas for that metric, saving tick time.
- The client HUD displays `[Disabled by the server]` in place of values.
- Mod-specific metrics (Create, Mekanism, AE2) require the corresponding mod installed on both server and client.
