package dev.schematicprogress;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A loaded schematic as a box of palette indices. Coordinates are relative to the
 * schematic origin; the box starts at (minX, minY, minZ). Index -1 means "nothing required".
 * Supports Litematica (.litematic), Sponge/WorldEdit (.schem v1-v3) and vanilla structures (.nbt).
 */
final class Schematic {
    final String name;
    final int minX, minY, minZ;
    final int sizeX, sizeY, sizeZ;
    final int[] blocks;          // index = (y * sizeZ + z) * sizeX + x
    final List<String> palette;  // full block state strings, e.g. minecraft:oak_stairs[facing=north]

    private Schematic(String name, int minX, int minY, int minZ, int sx, int sy, int sz, int[] blocks, List<String> palette) {
        this.name = name;
        this.minX = minX; this.minY = minY; this.minZ = minZ;
        this.sizeX = sx; this.sizeY = sy; this.sizeZ = sz;
        this.blocks = blocks;
        this.palette = palette;
    }

    int index(int x, int y, int z) {
        return (y * sizeZ + z) * sizeX + x;
    }

    static Schematic load(Path file) throws IOException {
        String fn = file.getFileName().toString();
        String lower = fn.toLowerCase();
        Map<String, Object> root = Nbt.read(file);
        if (lower.endsWith(".litematic")) return litematic(fn, root);
        if (lower.endsWith(".schem") || lower.endsWith(".schematic")) return sponge(fn, root);
        if (lower.endsWith(".nbt")) return structure(fn, root);
        // Guess by content.
        if (root.containsKey("Regions")) return litematic(fn, root);
        if (root.containsKey("blocks") && root.containsKey("size")) return structure(fn, root);
        return sponge(fn, root);
    }

    // ---------------- helpers ----------------

    private static final class Builder {
        final Map<String, Integer> ids = new HashMap<>();
        final List<String> palette = new ArrayList<>();

        int id(String state) {
            return ids.computeIfAbsent(state, s -> { palette.add(s); return palette.size() - 1; });
        }
    }

    static String stateString(Map<String, Object> entry) {
        Object name = entry.get("Name");
        if (!(name instanceof String s)) return "minecraft:air";
        Map<String, Object> props = Nbt.map(entry.get("Properties"));
        if (props == null || props.isEmpty()) return s;
        StringBuilder sb = new StringBuilder(s).append('[');
        boolean first = true;
        for (Map.Entry<String, Object> e : new TreeMap<>(props).entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.append(']').toString();
    }

    static String blockId(String state) {
        int i = state.indexOf('[');
        String id = i < 0 ? state : state.substring(0, i);
        return id.contains(":") ? id : "minecraft:" + id;
    }

    static boolean isAirId(String id) {
        return id.equals("minecraft:air") || id.equals("minecraft:cave_air")
                || id.equals("minecraft:void_air") || id.equals("minecraft:structure_void");
    }

    // ---------------- Litematica ----------------

    private record Region(int x, int y, int z, int sx, int sy, int sz, int[] local, List<String> pal) {}

    private static Schematic litematic(String fn, Map<String, Object> root) throws IOException {
        Map<String, Object> regions = Nbt.map(root.get("Regions"));
        if (regions == null || regions.isEmpty()) throw new IOException("Litematic without regions");
        List<Region> list = new ArrayList<>();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Object o : regions.values()) {
            Map<String, Object> r = Nbt.map(o);
            if (r == null) continue;
            Map<String, Object> pos = Nbt.map(r.get("Position"));
            Map<String, Object> size = Nbt.map(r.get("Size"));
            if (pos == null || size == null) continue;
            int px = Nbt.num(pos.get("x"), 0), py = Nbt.num(pos.get("y"), 0), pz = Nbt.num(pos.get("z"), 0);
            int sx = Nbt.num(size.get("x"), 0), sy = Nbt.num(size.get("y"), 0), sz = Nbt.num(size.get("z"), 0);
            int ax = Math.abs(sx), ay = Math.abs(sy), az = Math.abs(sz);
            if (ax == 0 || ay == 0 || az == 0) continue;
            int rx = px + (sx < 0 ? sx + 1 : 0), ry = py + (sy < 0 ? sy + 1 : 0), rz = pz + (sz < 0 ? sz + 1 : 0);

            List<String> pal = new ArrayList<>();
            List<Object> palTag = Nbt.list(r.get("BlockStatePalette"));
            if (palTag != null) for (Object p : palTag) pal.add(stateString(Nbt.map(p) == null ? Map.of() : Nbt.map(p)));
            if (pal.isEmpty()) pal.add("minecraft:air");
            long[] packed = r.get("BlockStates") instanceof long[] l ? l : new long[0];
            int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(pal.size() - 1));
            long mask = (1L << bits) - 1;
            long volume = (long) ax * ay * az;
            if (volume > 64_000_000L) throw new IOException("Region too big");
            int[] local = new int[(int) volume];
            for (int i = 0; i < local.length; i++) {
                long start = (long) i * bits;
                int si = (int) (start >> 6);
                int ei = (int) ((start + bits - 1) >> 6);
                int off = (int) (start & 63);
                long v;
                if (si >= packed.length) v = 0;
                else if (si == ei || ei >= packed.length) v = (packed[si] >>> off) & mask;
                else v = ((packed[si] >>> off) | (packed[ei] << (64 - off))) & mask;
                local[i] = (int) Math.min(v, pal.size() - 1);
            }
            list.add(new Region(rx, ry, rz, ax, ay, az, local, pal));
            minX = Math.min(minX, rx); minY = Math.min(minY, ry); minZ = Math.min(minZ, rz);
            maxX = Math.max(maxX, rx + ax); maxY = Math.max(maxY, ry + ay); maxZ = Math.max(maxZ, rz + az);
        }
        if (list.isEmpty()) throw new IOException("Litematic has no usable regions");
        int sx = maxX - minX, sy = maxY - minY, sz = maxZ - minZ;
        checkVolume(sx, sy, sz);
        int[] blocks = new int[sx * sy * sz];
        java.util.Arrays.fill(blocks, -1);
        Builder b = new Builder();
        for (Region r : list) {
            int[] map = new int[r.pal.size()];
            for (int i = 0; i < map.length; i++) map[i] = b.id(r.pal.get(i));
            for (int y = 0; y < r.sy; y++)
                for (int z = 0; z < r.sz; z++)
                    for (int x = 0; x < r.sx; x++) {
                        int v = map[r.local[(y * r.sz + z) * r.sx + x]];
                        if (isAirId(blockId(b.palette.get(v)))) continue;
                        int gx = r.x + x - minX, gy = r.y + y - minY, gz = r.z + z - minZ;
                        blocks[(gy * sz + gz) * sx + gx] = v;
                    }
        }
        String name = fn;
        Map<String, Object> meta = Nbt.map(root.get("Metadata"));
        if (meta != null && meta.get("Name") instanceof String s && !s.isBlank()) name = s + " (" + fn + ")";
        return new Schematic(name, minX, minY, minZ, sx, sy, sz, blocks, b.palette);
    }

    // ---------------- Sponge / WorldEdit ----------------

    private static Schematic sponge(String fn, Map<String, Object> root) throws IOException {
        if (Nbt.map(root.get("Schematic")) != null) root = Nbt.map(root.get("Schematic"));
        int sx = Nbt.num(root.get("Width"), 0), sy = Nbt.num(root.get("Height"), 0), sz = Nbt.num(root.get("Length"), 0);
        // Width/Height/Length are unsigned shorts.
        sx &= 0xFFFF; sy &= 0xFFFF; sz &= 0xFFFF;
        checkVolume(sx, sy, sz);
        Map<String, Object> pal;
        Object data;
        Map<String, Object> blocksTag = Nbt.map(root.get("Blocks"));
        if (blocksTag != null) { // v3
            pal = Nbt.map(blocksTag.get("Palette"));
            data = blocksTag.get("Data");
        } else {                 // v1 / v2
            pal = Nbt.map(root.get("Palette"));
            data = root.get("BlockData");
        }
        if (pal == null || !(data instanceof byte[] bytes)) {
            if (root.containsKey("Blocks") && root.get("Blocks") instanceof byte[])
                throw new IOException("Old MCEdit .schematic (pre-1.13) is not supported");
            throw new IOException("Unknown .schem format");
        }
        int maxId = -1;
        for (Object v : pal.values()) maxId = Math.max(maxId, Nbt.num(v, -1));
        if (maxId > 1_000_000) throw new IOException("Bad palette");
        String[] byId = new String[maxId + 1];
        for (Map.Entry<String, Object> e : pal.entrySet()) {
            int id = Nbt.num(e.getValue(), -1);
            if (id >= 0) byId[id] = e.getKey();
        }
        Builder b = new Builder();
        int[] map = new int[byId.length];
        for (int i = 0; i < byId.length; i++) {
            String s = byId[i] == null ? "minecraft:air" : byId[i];
            map[i] = isAirId(blockId(s)) ? -1 : b.id(s);
        }
        int[] blocks = new int[sx * sy * sz];
        int pos = 0;
        for (int i = 0; i < blocks.length; i++) {
            int value = 0, shift = 0;
            while (true) { // varint
                if (pos >= bytes.length) throw new IOException("Block data too short");
                int cur = bytes[pos++];
                value |= (cur & 0x7F) << shift;
                if ((cur & 0x80) == 0) break;
                shift += 7;
                if (shift > 35) throw new IOException("Bad varint");
            }
            blocks[i] = value >= 0 && value < map.length ? map[value] : -1;
        }
        // Sponge order is (y * Length + z) * Width + x, the same as ours.
        return new Schematic(fn, 0, 0, 0, sx, sy, sz, blocks, b.palette);
    }

    // ---------------- vanilla structure ----------------

    private static Schematic structure(String fn, Map<String, Object> root) throws IOException {
        List<Object> size = Nbt.list(root.get("size"));
        if (size == null || size.size() < 3) throw new IOException("Structure without size");
        int sx = Nbt.num(size.get(0), 0), sy = Nbt.num(size.get(1), 0), sz = Nbt.num(size.get(2), 0);
        checkVolume(sx, sy, sz);
        List<Object> palTag = Nbt.list(root.get("palette"));
        if (palTag == null) {
            List<Object> palettes = Nbt.list(root.get("palettes"));
            if (palettes != null && !palettes.isEmpty()) palTag = Nbt.list(palettes.get(0));
        }
        if (palTag == null) throw new IOException("Structure without palette");
        Builder b = new Builder();
        int[] map = new int[palTag.size()];
        for (int i = 0; i < map.length; i++) {
            Map<String, Object> e = Nbt.map(palTag.get(i));
            String s = e == null ? "minecraft:air" : stateString(e);
            map[i] = isAirId(blockId(s)) ? -1 : b.id(s);
        }
        int[] blocks = new int[sx * sy * sz];
        java.util.Arrays.fill(blocks, -1);
        List<Object> list = Nbt.list(root.get("blocks"));
        if (list != null) for (Object o : list) {
            Map<String, Object> e = Nbt.map(o);
            if (e == null) continue;
            List<Object> p = Nbt.list(e.get("pos"));
            int st = Nbt.num(e.get("state"), -1);
            if (p == null || p.size() < 3 || st < 0 || st >= map.length) continue;
            int x = Nbt.num(p.get(0), -1), y = Nbt.num(p.get(1), -1), z = Nbt.num(p.get(2), -1);
            if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz) continue;
            blocks[(y * sz + z) * sx + x] = map[st];
        }
        return new Schematic(fn, 0, 0, 0, sx, sy, sz, blocks, b.palette);
    }

    private static void checkVolume(int sx, int sy, int sz) throws IOException {
        if (sx <= 0 || sy <= 0 || sz <= 0) throw new IOException("Empty schematic");
        if ((long) sx * sy * sz > 64_000_000L) throw new IOException("Schematic is too big (" + sx + "x" + sy + "x" + sz + ")");
    }
}
