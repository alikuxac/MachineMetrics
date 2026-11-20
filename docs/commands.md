# Commands

Machine Metrics registers `/machinemetrics` and its alias `/mm`. Client utilities are registered on the client command dispatcher, so they work in singleplayer and on vanilla servers; server diagnostics require permission level 2.

---

## Client (permission level 0)

| Command | Effect |
| :--- | :--- |
| `/mm hud toggle` | Toggle the telemetry HUD overlay. |
| `/mm hud position` | Open the HUD position editor screen. |
| `/mm pin clear` | Clear the pinned machine target. |
| `/mm pin pos <x> <y> <z>` | Pin a machine by block coordinates. |

---

## Diagnostics (permission level 2)

| Command | Effect |
| :--- | :--- |
| `/mm inspect` | Dump capabilities for the targeted block. |
| `/mm inspect <x> <y> <z> [side]` | Dump capabilities for explicit coordinates and optional face (`up`, `down`, `north`, `south`, `west`, `east`). |
| `/mm compat` | List detected compatibility adapters in the current session. |
| `/mm reload` | Reload client and server configuration files in place. |

`inspect` reports item, fluid, and energy handler contents plus any matching adapter data (Mekanism chemicals/transporters, Create kinetics, AE2 grids, drawers, sophisticated storage). Adapter failures are isolated; generic capability data still prints.

---

## Server administration (permission level 2)

| Command | Effect |
| :--- | :--- |
| `/mm server stats` | Show active polling clients, packet dispatch rate (Hz), average capability scan duration (µs), and the current request interval. |
| `/mm server ratelimit <ticks>` | Set the minimum interval between a player's telemetry requests, in ticks (`0` disables the interval). The value is runtime-only and resets on restart. |

The tick interval complements `max_requests_per_second` in `config/machinemetrics-server.toml`. Both limits must pass before a request is sampled.
