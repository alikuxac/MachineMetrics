# Configuration Guide

Machine Metrics splits configuration between client HUD options and server telemetry permissions.

> **Target:** NeoForge 26.1.2 (Java 25). Config keys are unchanged from the earlier 1.21.1 line; the server config gained request-validation keys (`max_request_range`, `max_requests_per_second`) in this line. See [Migrating to Minecraft 26.1.2](26.1.2-migration.md) for the upgrade summary.

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

- **Full Diagnostic**: Shows title, status badges, items, fluids, energy, and all installed mod metrics.
- **Minimalist Bar**: Enables compact single-line mode.
- **Create Only**: Shows rotational speed, direction, network stress, and headroom (requires Create).
- **Chemicals Only**: Shows chemical throughput, chemical name, and container capacities (requires Mekanism).
- **Energy Only**: Shows Forge Energy throughput (`FE/t`).
- **Items Only**: Shows item transfer rate (`Items/s`).
- **Fluids Only**: Shows fluid transfer rate and, on tanks, fluid name with fill/capacity (`mB/s` or `B/s`).

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
| `auto_hide_redundant_with_info_mods` | `true` | Hides zero-rate metrics when Jade, WAILA, WTHIT, or The One Probe is installed. |
| `show_status_badge` | `true` | Shows bottleneck state (`OPTIMAL`, `STARVED`, `CLOGGED`, `IDLE`). |
| `show_items_throughput` | `true` | Shows item transfer throughput (`Items/s`). |
| `show_fluids_throughput` | `true` | Shows fluid transfer throughput (`mB/s` or `B/s`), plus fluid name and fill/capacity on tanks. |
| `show_chemicals_throughput` | `true` | Shows Mekanism chemical throughput, chemical name, and tank fill/capacity. |
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
| `show_storage_capacity` | `true` | Shows container capacity progress bar. |
| `show_time_to_full` | `true` | Shows estimated time until storage fills or empties (ETA). |

### Thresholds & Compact Settings (`[formatting]`)

```toml
[formatting]
  compact_mode = false
  compact_template = "{name} | {speed} RPM {dir} | {stress}/{cap} SU ({load_pct}%) | {state}"

  # Storage capacity warning and critical percentage thresholds
  storage_warning_threshold = 80.0
  storage_critical_threshold = 95.0

  # ETA alert thresholds in seconds
  eta_warning_threshold_seconds = 60
  eta_critical_threshold_seconds = 15
```

### Storage Progress Bar & ETA Behavior

- **Progress Bar**: `[████░░░░░░] 41.9% (1,450 / 3,456)` (10 blocks, 1 block = 10%)
  - Standard containers display exact slot or item counts up to 10M, and compact metric units (`10.5M`) above.
  - Infinite or creative containers render as `[██████████] ∞% (∞ / ∞)`.
  - Color thresholds:
    - `< storage_warning_threshold` (default 80%): `storageNormalColor` (`#55FF55`)
    - `storage_warning_threshold` to `storage_critical_threshold` (80% to 95%): `storageWarningColor` (`#FFA726`)
    - `≥ storage_critical_threshold` (default 95%): `storageCriticalColor` (`#FF5555`)
- **ETA Status Line**:
  - `Full in: <time>` when filling, `Empty in: <time>` when draining.
  - Formats duration using up to 3 non-zero units across years, months, days, hours, minutes, and seconds (for example: `45s`, `2m 40s`, `1h 15m 10s`, `3d 4h 12m`, `1y 2mo 5d`).
  - Color thresholds:
    - `≥ eta_warning_threshold_seconds` (default 60s): `etaNormalColor` (`#55FF55`)
    - `< eta_warning_threshold_seconds`: `etaWarningColor` (`#FFA726`)
    - `< eta_critical_threshold_seconds` (default 15s): flashes between `etaCriticalColor` (`#FF3333`) and `etaWarningColor` at 2.5 Hz.
  - When transfer rate is 0, the HUD omits the ETA line.
- **Transport Cables**:
  - Item transport cables (such as Mekanism logistical transporters) display active in-flight items against standard stack capacity (64 items).
  - The sampler holds net rate at 0 for in-transit items to suppress false empty or full ETA alerts while items pass through.

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

  # Storage capacity & ETA
  storageNormalColor = "#55FF55"
  storageWarningColor = "#FFA726"
  storageCriticalColor = "#FF5555"
  etaNormalColor = "#55FF55"
  etaWarningColor = "#FFA726"
  etaCriticalColor = "#FF3333"

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

When targeting a storage container with `compact_mode = true`, the HUD compresses capacity and ETA into an icon summary (`📦 42% | ⏳ 2m 40s`) instead of rendering the multi-character bar.

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
| `{fluids}` | Fluid transfer rate, prefixed with fluid name on tanks | `250.0 mB/s`, `Water 250.0 mB/s` |
| `{fluid_name}` | Fluid display name (empty tank shows `—`) | `Water`, `Lava`, `—` |
| `{fluid_amount}` | Stored fluid quantity | `250 mB`, `2.50 B` |
| `{fluid_capacity}` | Fluid tank capacity (`—` when unknown) | `10.00 B`, `∞`, `—` |
| `{fluid_pct}` | Fluid fill percentage | `25.0`, `100.0` |
| `{chemicals}` | Mekanism chemical transfer rate, prefixed with chemical name | `0.0 mB/s`, `Hydrogen 50.0 mB/s` |
| `{chemical_amount}` | Stored chemical quantity | `500 mB`, `1.50 B` |
| `{chemical_capacity}` | Chemical tank capacity (`—` when unknown) | `1.00 B`, `∞`, `—` |
| `{chemical_pct}` | Chemical fill percentage | `50.0`, `100.0` |
| `{storage}` | Compact storage percentage icon | `📦 42%`, `📦 ∞%` |
| `{eta}` | Compact ETA duration icon | `⏳ 2m 40s`, `—` |
| `{ae_power}` | AE2 network net power rate | `-3.4k AE/t`, `+1.2k AE/t` |
| `{ae_drain}` | AE2 grid power drain | `180.0 AE/t` |
| `{ae_subnets}` | AE2 connected subnet count and drain | `2 (+40.0 AE/t)` |
| `{ae_state}` | AE2 grid stability state | `STABLE`, `BROWNOUT`, `DISCHARGING` |

> **Fluid and chemical tanks:** On tank blocks, the HUD displays the stored fluid or chemical name with fill and capacity (for example, `Fluids: Water 2.50 B / 10.00 B | 250.0 mB/s`). The storage progress bar tracks tank fill instead of internal bucket slots. Creative or infinite tanks show `∞ / ∞`. The `{fluid_*}` and `{chemical_*}` placeholders display `—` when unavailable and `[Disabled by the server]` when disabled by server settings.

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

[sync]
  # Minimum container fill percentage change required to sync storage
  storage_sync_deadband_percent = 1.0
  # Max distance (blocks) a player may request telemetry for (range: 1.0 to 64.0)
  max_request_range = 12.0
  # Max telemetry requests processed per player per second (0 = unlimited, range: 0 to 200)
  max_requests_per_second = 20
```

### Request Validation

The server validates every `RequestTelemetryPayload` before sampling:
- **Range check**: Requests for blocks farther than `max_request_range` from the player are ignored.
- **Loaded check**: Requests for unloaded positions are ignored.
- **Rate limit**: Each player is limited to `max_requests_per_second` requests per one-second window, applied before the expensive capability sampling so a modified client cannot force unbounded server work.
- **Cache scope**: Server-side delta caching is keyed per `(player, block position)`, so one player's requests do not suppress another player's sync.

### Behavior when disabled

When a metric is disabled on the server:
- The server stops sampling capability deltas for that metric to save tick time.
- The client HUD displays `[Disabled by the server]` in place of values.
- Mod-specific metrics (Create, Mekanism, AE2) require the corresponding mod installed on both server and client.
