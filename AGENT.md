# AGENT.md — Eye of the Storm Mod (Handoff)

> For the next agent: this is a **NeoForge mod**, not a datapack. Do not reintroduce scoreboard/macro/particle-spoke workarounds.

## Location

```
c:\Users\Camer\AppData\Roaming\ModrinthApp\profiles\NeoForge 1.21.1\eye-of-the-storm-mod\
```

Legacy datapack (disable when using the mod):

```
...\saves\New World\datapacks\Eye of the Storm\
```

**MC 1.21.1 / NeoForge 21.1.176** — see `gradle.properties`.

---

## Design principles (post-datapack rewrite)

| Datapack leftover (avoid) | Mod approach (current) |
|---------------------------|-------------------------|
| Milli-HP damage accumulator | `player.hurt(..., 0.1f)` each tick |
| Scoreboard int math / case-switch tp | `double` motion + angular velocity |
| Y=512 spawn hack | `Heightmap` surface under the eye |
| Marker entities | Pure `StormData` center Vec3 |
| Dust spoke particles | Client translucent mesh (`StormWallRenderer`) |
| `turn_scale` × `heading_delta` scores | `StormConfig` steering noise + damping |
| `/function storm:...` macros | Brigadier `/storm ...` |

Tunables live in **`StormConfig`** (static fields for now; Cloth/FZZy config is a natural next step).

---

## Package map

| Class | Role |
|-------|------|
| `EyeOfTheStormMod` | Entry; registers payload + game bus |
| `StormConfig` | Runtime tunables (damage, wall view, vignette, sync rate) |
| `storm.StormData` | `SavedData` — center, radius, speed, yawRad, angularVelocity |
| `storm.StormLogic` | Tick: motion, fractional damage, spawn, sounds |
| `storm.StormCompass` | Lodestone refresh for tagged compasses |
| `storm.StormEvents` | Overworld `LevelTickEvent.Post` |
| `command.StormCommands` | `/storm` admin commands |
| `network.StormSyncPayload` | Server → client sync |
| `client.ClientStormState` | Client mirror |
| `client.StormWallRenderer` | Mesh curtain (circle ∩ view) |
| `client.StormVignetteOverlay` | Red border-style vignette outside eye |

---

## Runtime behavior

1. **Zone:** horizontal cylinder, radius `StormData.radius`.
2. **Damage:** `StormConfig.damagePerTick` HP every tick outside (default `0.1`). Creative/spectator/immune skipped.
3. **Motion:** integrate `forward(yawRad) * speed`; steer with damped random angular velocity (`StormConfig.turnNoise` / `maxAngularVelocity` / `angularDamping`). **Y does not snap to terrain each tick.**
4. **Spawn:** every `spawnUpdateIntervalTicks`, set overworld spawn to Heightmap under center.
5. **Immunity:** entity tag `storm_immune`.
6. **Client wall:** only the arc (or full ring if view contains storm) within `wallViewDistance` of the player; height centered on player Y ± `wallHalfHeight`.
7. **Vignette:** strength from distance past radius, full at `vignetteFullAtGap`.

---

## Commands

```
/storm init_here | init <x> <y> <z>
/storm teleport <x> <y> <z>
/storm set_radius <r>
/storm set_speed <blocks/tick>
/storm set_damage <hp/tick>     # runtime StormConfig; not saved to world yet
/storm pause | resume | status
/storm give_compass | toggle_immunity | help
```

Permission level 2.

---

## Build / install

```bat
gradlew.bat build
copy build\libs\eyeofthestorm-1.0.0.jar "..\mods\"
```

Java **21** required. First build downloads NeoForm — needs RAM (`org.gradle.jvmargs=-Xmx2G` in gradle.properties; raise to 4G if the daemon dies).

**Remove the datapack** before testing or you get double systems.

---

## Networking

- Id: `eyeofthestorm:sync`
- Fields: active, paused, center XYZ, radius, wallSpinDeg
- Sent via `PacketDistributor.sendToPlayersInDimension` on interval `StormConfig.syncIntervalTicks`

---

## Suggested next work

- [ ] Persist damage/view settings (ModConfigSpec or world NBT)
- [ ] Migrate old datapack `storm:data` command storage once on load
- [ ] Iris/Sodium render-type pass if wall invisible under shaders
- [ ] Recipe / creative tab for Storm Compass
- [ ] Optional: client-only ambient loop instead of server `playSound` spam

---

## Do not do

- Do not bring back particle spoke systems for the main wall
- Do not use scoreboards for storm math
- Do not set spawn to a fixed Y=512
- Do not accumulate “milli damage” unless Mojang breaks fractional `hurt` (it works)
