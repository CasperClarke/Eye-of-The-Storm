# Eye of the Storm (NeoForge)

Full storm simulation + client wall/vignette for **Minecraft 1.21.1**.

This **replaces** the datapack. Disable the datapack when using the mod.

## Highlights (mod-native, not datapack ports)

- **0.1 HP/tick** real float damage outside the eye
- Smooth double-precision movement (angular velocity, not scoreboard tiers)
- World spawn follows the eye via **Heightmap** (no Y=512 hack)
- **Mesh wall** + **red vignette** on the client (cheap vs particle spokes)
- `/storm` Brigadier commands

## Commands

```
/storm init_here
/storm set_radius 2000
/storm set_speed 0.05
/storm set_damage 0.1
/storm give_compass
/storm toggle_immunity
/storm help
```

## Build

```bat
gradlew.bat build
```

Copy `build/libs/eyeofthestorm-1.0.0.jar` into the profile `mods/` folder.

## Docs

See **[AGENT.md](AGENT.md)** for architecture and handoff notes for other agents.
