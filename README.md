# Machine Metrics

Machine Metrics is a client-side and server-synced telemetry HUD for Minecraft. It shows real-time throughput, energy rates, and machine states without needing an external tooltip mod like Jade or WTHIT.

> **Note:** This mod is currently in beta. Features and network protocols may change between updates. Back up your worlds before updating.

**Documentation:** [docs.alikuxac.xyz/mods/machine-metrics](https://docs.alikuxac.xyz/mods/machine-metrics/)

---

### Features

- **Real-Time Throughput:** Measures transfer rates for items (Items/s), fluids (mB/s, B/s), and energy (FE/t).
- **Per-Side Tracking:** Checks individual block faces to show throughput per side.
- **Machine State Badges:**
  - `OPTIMAL`: Machine is processing or moving materials.
  - `STARVED`: Lacking input materials or power.
  - `CLOGGED`: Output buffer or connected pipe is full.
  - `IDLE`: Machine is inactive (throughput numbers hide automatically).
- **HUD Layout:**
  - Auto-resizing box based on active lines.
  - Drag-and-drop position editor in the pause menu.
  - Switches between anchors (`TOP_LEFT`, `TOP_RIGHT`, `BOTTOM_LEFT`, `BOTTOM_RIGHT`, `CROSSHAIR`).
  - Ignores static storage containers like chests, barrels, drawers, and AE2 cables.
- **Client & Server Sync:**
  - Works on vanilla or unmodded servers using client-side capability reads.
  - Syncs server-side measurements when installed on dedicated servers for higher precision.

---

### Compatibility

Machine Metrics targets **NeoForge on Minecraft 26.1.2 (Java 25)**.

- **Standard Capabilities:** Works with any block exposing standard NeoForge transfer capabilities (`Capabilities.Item.BLOCK`, `Capabilities.Fluid.BLOCK`, `Capabilities.Energy.BLOCK`).
- **Create:** Kinetic speed (RPM), rotation direction, network stress, capacity (SU), overstress warning, and headroom. No official 26.1.2 build exists; the inspector is reflective and inert when Create is absent.
- **Mekanism:** Chemical throughput and tank capacities (Gases, Infuse Types, Pigments, Slurries). No official 26.1.2 build exists; the adapter is compile-decoupled and falls back to generic capability reads when absent.
- **Tooltip Mods:** Automatically hides the block title line when Jade, Waila, WTHIT, or The One Probe is installed.

> Migrating from the 1.21.1 line? See [Migrating to Minecraft 26.1.2](docs/26.1.2-migration.md) for the loader, capability, and identifier deltas.

---

### Client & Server Setup

| Setup | Behavior |
| :--- | :--- |
| **Client and Server** | Full accuracy. The client requests metrics from the server, and the server samples block throughput and face rates directly. |
| **Client Only** | Works on vanilla and third-party servers without the mod. Samples block entities locally on the client. |
| **Server Only** | Idle. The server only samples blocks when clients send requests. Generates zero overhead without modded clients. |

---

### Controls & Configuration

- **Toggle HUD:** `H` (changeable in `Options -> Controls -> Key Binds`).
- **Open HUD Editor:**
  - In Main Menu: Go to `Mods` -> select `Machine Metrics` -> click **Config**.
  - In-Game: Press `ESC` and click **Machinemetrics HUD** in the top right.
- **Settings:**
  - Client visual settings, offsets, presets, and colors: `config/machinemetrics-client.toml` or via the in-game editor.
  - Server metrics permissions and toggles: `config/machinemetrics-server.toml`.
  - See the [Configuration Guide](docs/configuration.md) for keys and compact template placeholders.
  - See the [Telemetry Data Flow](docs/data-flow.md) for network packet flow and capability sampling architecture.

---

### Notes

- This is a personal side project. Updates come as time allows.
- No backports to older Minecraft versions or loaders are planned.
- Built for NeoForge on Minecraft 26.1.2 (Java 25).

---

### License

Available under the [MIT License](LICENSE).