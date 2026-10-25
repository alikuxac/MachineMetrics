# Mod Support Criteria

Machine Metrics provides dedicated adapters for selected high-traffic mods and relies on standard capability inspection for everything else. This document outlines active mod support and the requirements for adding new adapters.

---

## Support Tiers

| Tier | Coverage | Mechanism | Maintenance Cost |
| :--- | :--- | :--- | :--- |
| **Tier 1: Dedicated** | Full metrics for target mod | Dedicated adapter in `compat/`, gated by `Class.forName` | Code updates per mod version |
| **Tier 2: Generic** | Standard capabilities | `BlockCapacityReader` fallback | None |
| **Tier 3: Community** | Contributed niche mods | Reflection heuristics | Review and merge |

Tier 2 applies automatically to any block exposing item, fluid, or energy capabilities. Blocks without dedicated adapters run on this fallback. Loader-agnostic capability access goes through the `platform.storage` abstraction (`StorageAccess` + `ItemStorage`/`FluidStorage`/`EnergyStorage`), implemented per loader (`NeoForgeStorageLookup`, `ForgeStorageLookup`), so adapters and readers compiled in `common/` never reference `net.neoforged.*` or `net.minecraftforge.*` directly.

---

## Supported Mods (Tier 1)

| Mod | Namespace | Adapter | Detection Gate |
| :--- | :--- | :--- | :--- |
| **Applied Energistics 2** | `appeng` | `AE2NetworkInspector` | `appeng.api.networking.IGridNode` |
| **Mekanism** | `mekanism` | `MekanismChemicalInspector`, `MekanismTransporterInspector` | `mekanism.api.chemical.IChemicalHandler` |
| **Create** | `create` | `CreateKineticInspector` | `com.simibubi.create.content.kinetics.base.KineticBlockEntity` |
| **Functional Storage** | `functionalstorage` | `DrawerStorageAdapter` | `com.buuz135.functionalstorage.block.tile.ControllableDrawerTile` |
| **Storage Drawers** | `storagedrawers` | `DrawerStorageAdapter` | `com.jaquadro.minecraft.storagedrawers.api.storage.IDrawerGroup` |
| **Sophisticated Storage** | `sophisticatedstorage` | `SophisticatedStorageAdapter` | Namespace match |

Tier 1 detection combines `CompatRegistry` namespace checks with reflective class checks. Adapters stay inert when their target mod is absent and introduce no compile-time dependencies.

---

## Acceptance Criteria for New Mods

A mod requires a dedicated adapter only when it meets all five criteria:

1. **Exposes an API or accessor.** The mod provides interfaces, an `api.*` package, or accessible internal state. Blocks that only expose standard capabilities already work under Tier 2.
2. **Maps to supported HUD metrics.** Telemetry must fit items, fluids, energy, chemicals, kinetics, networks, or container capacity.
3. **Provides stable reflection targets.** Class and method names must remain consistent across releases. Official API methods take precedence over internal fields.
4. **Maintains high player adoption.** The mod appears in major modpacks or shows high download volume to justify maintenance.
5. **Functions on NeoForge and Forge.** Shared adapters live in `common/` and must pass verification on both loaders.

---

## Design Rules for Adapters

- **Prefer missing data over inaccurate data.** Return `Optional.empty()` to trigger generic fallback when state is ambiguous.
- **Run adapter reflection before generic capability checks.** Specialized inspectors extract accurate drawer and tank structures that generic readers misidentify.
- **Isolate reflection calls in `Throwable` try-catch blocks.** Missing classes or altered signatures must fall back to Tier 2 without throwing uncaught errors.
- **Key mod detection by block registry namespace.** Registry namespaces survive internal package moves and remain identical across mod loaders.
- **Use method-name candidate arrays for reflection targets.** Fallback name arrays allow adapters to tolerate minor upstream renames without modifying core samplers.

---

## Requesting a Mod

New mod requests are checked against these criteria. Qualifying mods receive an adapter under `common/src/main/java/com/alikuxac/machinemetrics/compat/<mod>/`. Unqualified mods continue through Tier 2 capability probing.

---

## Scaling Note

`CompatRegistry` uses explicit namespace constants and `isXBlock` methods. When total supported mods exceeds ten, migrate to a namespace-to-inspector dispatch map so new additions require a single registration entry.