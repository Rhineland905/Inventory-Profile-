# Schematic Progress (Fabric 26.3, client)

Loads a schematic, compares it with the world and shows a web dashboard at
`http://localhost:8765/` (key **N** opens it in the browser):

- all materials: total, placed, left, in your inventory, missing, needed for the current layer;
- progress of the whole build and of the current layer (auto = lowest unfinished layer, or pick one);
- a top-down map of the layer (placed / to place / wrong block / not loaded) and the coordinates
  of every block still to place, nearest first.

Put `.litematic`, `.schem` (Sponge v1-v3, WorldEdit) or `.nbt` (vanilla structure) files into
`.minecraft/schematics/`, pick one on the page. It is placed with its origin where you stand;
move it with the X/Y/Z buttons or type the origin from your Litematica placement, rotate with 0/90/180/270.

Display only: the mod never places or breaks blocks. Blocks are compared by block type (not by facing).

Config: `config/schematic-progress.properties` (`port`, `allowLan=true` to open the page from a phone).
