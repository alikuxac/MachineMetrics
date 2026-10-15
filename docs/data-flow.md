# Telemetry Data Flow

Machine Metrics uses an on-demand polling model. The server does not scan chunks, track active block entities in the background, or push unsolicited metrics. Telemetry calculations execute only when a player looks at a machine with the HUD enabled.

---

## Architecture Overview

```mermaid
sequenceDiagram
    autonumber
    actor Player as Player Crosshair
    participant ClientHUD as TelemetryHudRenderer
    participant ClientCache as ClientTelemetryCache
    participant Net as NetworkHandler
    participant Sampler as ServerTelemetrySampler
    participant Block as BlockEntity / Capabilities

    Player->>ClientHUD: Raycast hits BlockPos (120ms debounce)
    alt Server has Machine Metrics installed
        ClientHUD->>Net: RequestTelemetryPayload(pos, hitFace) [Max 5 Hz]
        Net->>Sampler: sample(serverLevel, pos, hitFace)
        Sampler->>Block: Query Forge/NeoForge Capabilities & Mod APIs
        Block-->>Sampler: Raw slot counts, tank levels, stored energy
        Sampler->>Sampler: Compute delta rates & bottleneck state
        Sampler-->>Net: TelemetryData + serverDisabledMask
        Net->>ClientCache: SyncTelemetryPayload (Bitmask compressed)
        ClientCache-->>ClientHUD: Synced TelemetryData
    else Vanilla / Unmodded Server
        ClientHUD->>ClientHUD: Fallback to ClientSampler (Local Capability Read)
    end
    ClientHUD->>Player: Render adaptive HUD / Compact template
```

---

## 1. Client Raycast & Debounce

When the player points their crosshair at a block:

1. **Raycast Verification**: `TelemetryHudRenderer` checks `Minecraft.getInstance().hitResult`. Non-block hits immediately invalidate active targets.
2. **Debounce (120 ms)**: Prevents sending packets when rapidly sweeping the crosshair across multiple blocks. The player must rest their crosshair on a block entity for at least `120ms`.
3. **Throttling (200 ms / 5 Hz)**: Once locked on a target, requests are sent at most once every `200ms` to keep network overhead low.
4. **Packet Dispatch**: The client sends a `RequestTelemetryPayload` containing:
   - `BlockPos targetPos` (8 bytes)
   - `Direction hitFace` (1 byte, nullable)

---

## 2. Server Sampling & State Deduction

The server handles `RequestTelemetryPayload` on the main server thread via `NetworkHandler`:

```mermaid
flowchart TD
    Req["RequestTelemetryPayload<br/>(targetPos, hitFace)"] --> CacheCheck{"Inert Block Cache?<br/>(Within 5s)"}
    CacheCheck -- "Yes (No Capabilities)" --> ReturnEmpty["Return Empty Data"]
    CacheCheck -- "No" --> PermCheck["Check ServerConfig permissions"]

    PermCheck --> CapQueries["Query Block Capabilities:<br/>- IItemHandler<br/>- IFluidHandler<br/>- IEnergyStorage<br/>- Create Kinetic Inspector<br/>- Mekanism Chemical Inspector<br/>- AE2 Network Inspector"]

    CapQueries --> HistoryDiff["Compare with SampleHistory:<br/>Delta = (Current - Previous) / DeltaSeconds"]
    HistoryDiff --> StateAnalysis["Deduce Machine State:<br/>- OPTIMAL (Active throughput)<br/>- STARVED (Empty inputs, unpowered)<br/>- CLOGGED (Full output buffers)<br/>- IDLE (Zero throughput)"]
    StateAnalysis --> BitmaskPack["Encode SyncTelemetryPayload<br/>(Bitmask header + Active fields)"]
```

### Capability Sampling

The sampler queries supported capabilities on all 6 faces plus the center block:
- **NeoForge/Forge Capabilities**:
  - `Capabilities.ItemHandler.BLOCK`: Item inventory and slot counts.
  - `Capabilities.FluidHandler.BLOCK`: Tank capacities and fluid amounts.
  - `Capabilities.EnergyStorage.BLOCK`: Stored FE amounts.
- **Mod Integrations**:
  - **Create**: Reads rotational speed, network stress draw, capacity (SU), and overstress flags directly from `KineticBlockEntity`.
  - **Mekanism**: Inspects chemical tanks (Gas, Infuse, Pigment, Slurry) via Mekanism API.
  - **Applied Energistics 2**: Traverses the grid via `IGridNodeHost` to calculate net power rate, active drain, buffer percentage, stability states (`STABLE`, `BROWNOUT`, `BOOTING`, `DISCHARGING`, `CREATIVE`), and connected quartz fiber subnet drain.

### Rate Calculation

Rates use differential time sampling against `SampleHistory`:
- The server stores the previous sample timestamp and contents for active targets.
- Throughput is calculated as:
  $$\text{Rate} = \frac{\Delta \text{Transferred}}{\Delta t}$$
- If no throughput occurs for 3 consecutive samples while inventories remain constant, the machine transitions to `IDLE`.

---

## 3. Network Payload Compression

`SyncTelemetryPayload` uses bitmask serialization. Instead of writing every metric field over the network, a 1-byte presence mask indicates which data fields follow in the packet:

| Bit Flag | Mask Name | Payload Written if Bit Set |
| :--- | :--- | :--- |
| `0x01` | Items | `float itemThroughput`, `boolean hasItems` |
| `0x02` | Fluids | `float fluidThroughput`, `boolean hasFluids` |
| `0x04` | Energy | `float energyDelta`, `boolean hasEnergy` |
| `0x08` | Kinetic | `float speed`, `stress`, `capacity`, `byte direction` |
| `0x10` | Chemicals | `float chemicalThroughput`, `boolean hasChemicals` |
| `0x20` | AE2 | `float powerRate`, `powerDrain`, `subnetDrain`, `short subnets`, `double stored`, `double max`, `byte stability`, `int usedChannels`, `int maxChannels` |
| `0x40` | Directional Sides | Variable length array of per-face active rates |
| `0x80` | Server Disabled Mask | `short serverDisabledMask` (Flags restricted features) |

In typical operation on inactive or simple blocks, packets require under 16 bytes of network payload. Full multi-mod machines require approximately 28 to 44 bytes per sync packet.

---

## 4. Client Caching & HUD Presentation

```mermaid
flowchart LR
    Packet["SyncTelemetryPayload"] --> Cache["ClientTelemetryCache"]
    Cache --> Resolver["MetricResolver"]
    Resolver --> Renderer["TelemetryHudRenderer"]
    Renderer --> HUD["Screen Output<br/>(Full Box or Compact Line)"]
```

1. **Cache Update**: `handleSyncPayload` updates `ClientTelemetryCache` under `targetPos`.
2. **Formatting**: `MetricResolver` converts numerical values to compact units (`k`, `M`, `RPM`, `mB/s`, `FE/t`, `AE/t`) and applies configured color codes.
3. **Rendering**:
   - **Full Box**: Calculates dynamic width and height based on visible element count, draws background box, borders, and text lines.
   - **Compact Mode**: Evaluates `compact_template` placeholders and renders a single-line string.

---

## 5. Lifecycle and Cache Eviction

- **Target Switching**: When the player's crosshair leaves a block, `ClientTelemetryCache.remove(targetPos)` immediately clears the client cache to prevent stale data when returning to that position.
- **Server Cache Expiration**: Inactive targets in `ServerTelemetrySampler.CACHE` expire after 10 seconds without requests.
- **Inert Blocks**: Blocks lacking compatible capability providers are stored in `INERT_BLOCK_CACHE` for 5 seconds to bypass redundant capability checks on ordinary building blocks.
