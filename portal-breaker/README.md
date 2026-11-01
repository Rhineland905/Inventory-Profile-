# Portal Breaker (Fabric, Minecraft 26.3)

Hit the chosen block with any pickaxe and blocks of that type around it break at once;
the blocks go to your inventory. Sneak while hitting to break only one block.
Does nothing in creative mode.

Commands (no cheats needed):
- `/pb` shows the settings
- `/pb block <block>` picks the block, e.g. `/pb block minecraft:reinforced_deepslate` (default)
- `/pb area <radius>` breaks every such block in a cube of that radius around the hit block (0..16)
- `/pb area connected` breaks the whole connected group (default)

Settings are saved in `config/portalbreaker.properties`.

Works in singleplayer. On a multiplayer server it works only if the server also has the mod.
Needs Fabric Loader and Fabric API.
