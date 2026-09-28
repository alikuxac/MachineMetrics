# ⚡ Machine Metrics

**Machine Metrics** is a lightweight, pure **client-side & server-sync capable** telemetry and diagnostics HUD for Minecraft. It provides real-time insights into industrial machines, automation lines, and energy networks without relying on external tooltip frameworks.

> ⚠️ **CAUTION:** This mod is currently in **BETA** state. Features and network protocols are subject to active refinement. **Please remember to backup your worlds and saves before installing or updating!**

---

### ✨ Key Features

* **⚡ Real-Time Machine Telemetry:** Instantly inspect dynamic throughput (**Items/s**, **Fluids/s**) and net energy delta (**FE/t**).
* **🎯 Side-Specific Directional Tracking:** Analyzes capability changes per block face (`UP`, `DOWN`, `NORTH`, `SOUTH`, `WEST`, `EAST`) to pinpoint exact item & fluid throughput on each individual side.
* **🔍 Bottleneck State Diagnostics:** Automatically categorizes machine status:
  * 🟢 **OPTIMAL:** Operating with active throughput.
  * 🟡 **STARVED:** Lacking required inputs or energy.
  * 🔴 **CLOGGED:** Output buffers or conduits congested.
  * ⚪ **IDLE:** Standby mode (automatically hides rate metrics when inactive).
* **🖥️ Dynamic & Responsive HUD:**
  * **Auto-Adaptive Sizing:** HUD dimensions dynamically auto-fit based on content and line count.
  * **GUI Scale Sync:** Native compatibility with Minecraft's GUI Scale (including `Auto`) and custom scale multiplier (`hudScale`).
  * **Dual-State View:** Compact overview by default, expands detailed metrics when holding `Shift`.
  * **Smart Filtering:** Automatically ignores static storage containers (Chests, Drawers, Vaults) and AE2 cables.
* **🌐 Hybrid Network Architecture:**
  * **Client-Side Standalone:** Fully functional on Vanilla/No-Mod servers via local capability sampling.
  * **Server Synchronization:** Uses custom payload packets (`RequestTelemetryPayload` / `SyncTelemetryPayload`) when installed on NeoForge servers.
  * **Raycast Debouncing:** Built-in 2-3 tick hover delay to prevent network packet spam.
* **🔧 Configurable Debugging:** Configurable `enableDebugLogging` option to keep game console logs clean.

---

### 🎮 Compatibility

Designed for heavy technical modpacks (ATM, All The Mods, SkyFactory, etc.):
* NeoForge & Forge Block Capabilities (`EnergyStorage`, `ItemHandler`, `FluidHandler`).
* Works with **Mekanism**, **Applied Energistics 2**, **Industrial Foregoing**, **Ender IO**, and more.

---

### 🌐 Client / Server Setup Breakdown

This mod is designed with a **flexible hybrid architecture**. You can install it on the client, server, or both depending on your environment:

| Installation Setup | Behavior & Functionality |
| :--- | :--- |
| **Both Client & Server (Recommended)** | 🌟 **Full Capability.** The client sends network telemetry requests (`RequestTelemetryPayload`) to the server. The server computes precise server-side block capabilities, throughput rates, and side-specific metrics, sending back `SyncTelemetryPayload` for maximum accuracy. |
| **Client Only (Vanilla / Dedicated Server without Mod)** | ⚡ **Client-Side Standalone Mode.** The HUD remains functional! Telemetry capabilities are sampled locally via client-side block entities and capabilities. Perfect for playing on public servers or vanilla servers. |
| **Server Only (No Client Installed)** | 😴 **Inactive / No Effect.** The server registers payload channels and capability listeners, but since no client sends telemetry requests, no HUD is rendered and zero network overhead is generated. |

---

### 🛠️ Configuration & Controls

* **Toggle HUD:** Press keybind in `Options -> Controls -> Key Binds` (Default: `H`).
* **Detailed View:** Hold `Shift` while targeting a machine.
* **Config File:** `config/machinemetrics-common.toml` (supports `hudScale` & `enableDebugLogging`).

---

### 📌 Project Status & Maintenance Note

* **Personal Project:** This mod is maintained as a personal side-project. Updates and feature additions will be released as time permits.
* **No Backports:** There are **no plans to backport** this mod to older Minecraft versions or legacy loader versions.
* **Mod Loaders & Support:** Currently focused on **NeoForge**. Support for other mod loaders (e.g. Fabric, Forge) or newer MC versions will only be considered if there is sufficient community demand.

---

### 📄 License

Open-source and available under the terms of the [MIT License](LICENSE).