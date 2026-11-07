package dev.schematicprogress;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Compares the loaded schematic with the world. All changes happen on the client thread;
 * the web server only reads the published (volatile) snapshots.
 */
final class Tracker {
    private Tracker() {}

    static final byte NONE = 0, DONE = 1, MISSING = 2, WRONG = 3, UNKNOWN = 4;
    private static final int BUDGET_PER_TICK = 40_000;

    /** What one palette entry means in this game. */
    record Info(String state, String id, Block block, String key, String name, int mult, int color) {}

    /** Result of one full pass over the schematic. */
    record Stats(int[] doneByPalette, int[] layerDone, int[] layerMissing, int[] layerWrong, int[] layerUnknown) {}

    static final class Active {
        final Schematic s;
        final Path file;
        final int ox, oy, oz, rot;
        final String dimension;
        final Info[] info;
        final byte[] status;
        final int[] nonAir;
        final int[] layerTotal;
        final int[] needByPalette;
        volatile Stats stats;
        volatile int manualLayer = -1; // -1 = auto
        // pass state (client thread only)
        int cursor;
        int[] curDone, curLDone, curLMissing, curLWrong, curLUnknown;

        Active(Schematic s, Path file, int ox, int oy, int oz, int rot, String dimension, Info[] info) {
            this.s = s; this.file = file; this.ox = ox; this.oy = oy; this.oz = oz; this.rot = rot;
            this.dimension = dimension; this.info = info;
            status = new byte[s.blocks.length];
            Arrays.fill(status, UNKNOWN);
            int n = 0;
            for (int b : s.blocks) if (b >= 0) n++;
            nonAir = new int[n];
            layerTotal = new int[s.sizeY];
            needByPalette = new int[s.palette.size()];
            n = 0;
            for (int i = 0; i < s.blocks.length; i++) {
                int b = s.blocks[i];
                if (b < 0) { status[i] = NONE; continue; }
                nonAir[n++] = i;
                layerTotal[i / (s.sizeX * s.sizeZ)]++;
                needByPalette[b]++;
            }
            resetPass();
        }

        void resetPass() {
            cursor = 0;
            curDone = new int[info.length];
            curLDone = new int[s.sizeY];
            curLMissing = new int[s.sizeY];
            curLWrong = new int[s.sizeY];
            curLUnknown = new int[s.sizeY];
        }

        /** World x/z offset of a schematic-relative x/z after rotation. */
        int wx(int x, int z) {
            return ox + switch (rot) { case 1 -> -z; case 2 -> -x; case 3 -> z; default -> x; };
        }

        int wz(int x, int z) {
            return oz + switch (rot) { case 1 -> x; case 2 -> -z; case 3 -> -x; default -> z; };
        }

        int currentLayer() {
            if (manualLayer >= 0 && manualLayer < s.sizeY) return manualLayer;
            Stats st = stats;
            for (int y = 0; y < s.sizeY; y++) {
                if (layerTotal[y] == 0) continue;
                if (st == null || st.layerDone[y] < layerTotal[y]) return y;
            }
            for (int y = s.sizeY - 1; y >= 0; y--) if (layerTotal[y] > 0) return y;
            return 0;
        }
    }

    static volatile Active active;
    static volatile String lastError = "";
    static volatile String lastMessage = "";
    static volatile double px, py, pz;
    static volatile boolean inWorld;
    static volatile String currentDimension = "";
    static volatile Map<String, Integer> inventory = Map.of();
    private static int tickCount;
    private static final Map<String, Block> BLOCKS_BY_ID = new HashMap<>();

    // ---------------- commands (client thread) ----------------

    static void activate(Schematic s, Path file) {
        Minecraft mc = Minecraft.getInstance();
        if (!Config.hasOrigin || !Config.file.equals(relative(file))) {
            if (mc.player != null) {
                Config.ox = (int) Math.floor(mc.player.getX());
                Config.oy = (int) Math.floor(mc.player.getY());
                Config.oz = (int) Math.floor(mc.player.getZ());
                Config.hasOrigin = true;
            }
        }
        Config.file = relative(file);
        if (mc.level != null) Config.dimension = dim(mc.level);
        Config.save();
        active = new Active(s, file, Config.ox, Config.oy, Config.oz, Config.rot, Config.dimension, buildInfo(s));
        lastError = "";
        lastMessage = "Загружено: " + s.name;
    }

    static void unload() {
        active = null;
        Config.file = "";
        Config.save();
        lastMessage = "Схематика выгружена";
    }

    static void setOrigin(int x, int y, int z) {
        Config.ox = x; Config.oy = y; Config.oz = z; Config.hasOrigin = true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) Config.dimension = dim(mc.level);
        replace();
        lastMessage = "Точка начала: " + x + " " + y + " " + z;
    }

    static void originHere() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) { lastError = "Сначала зайди в мир"; return; }
        setOrigin((int) Math.floor(p.getX()), (int) Math.floor(p.getY()), (int) Math.floor(p.getZ()));
    }

    static void nudge(int dx, int dy, int dz) {
        setOrigin(Config.ox + dx, Config.oy + dy, Config.oz + dz);
    }

    static void rotate(int r) {
        Config.rot = Math.floorMod(r, 4);
        replace();
    }

    static void setLayer(int localY) {
        Active a = active;
        if (a != null) a.manualLayer = localY;
    }

    private static void replace() {
        Config.save();
        Active a = active;
        if (a == null) return;
        Active n = new Active(a.s, a.file, Config.ox, Config.oy, Config.oz, Config.rot, Config.dimension, a.info);
        n.manualLayer = a.manualLayer;
        active = n;
    }

    static String relative(Path file) {
        try {
            return Config.schematicsDir().toAbsolutePath().normalize().relativize(file.toAbsolutePath().normalize())
                    .toString().replace('\\', '/');
        } catch (Exception e) {
            return file.getFileName().toString();
        }
    }

    private static String dim(ClientLevel level) {
        return String.valueOf(level.dimension());
    }

    private static Info[] buildInfo(Schematic s) {
        if (BLOCKS_BY_ID.isEmpty()) {
            for (Block b : BuiltInRegistries.BLOCK) BLOCKS_BY_ID.put(BuiltInRegistries.BLOCK.getKey(b).toString(), b);
        }
        Info[] out = new Info[s.palette.size()];
        for (int i = 0; i < out.length; i++) {
            String state = s.palette.get(i);
            String id = Schematic.blockId(state);
            Block block = BLOCKS_BY_ID.get(id);
            String key = "block:" + id;
            String name = id;
            int mult = materialCount(state);
            int color = 0x8A8A8A;
            if (block != null) {
                name = block.getName().getString();
                try {
                    int c = block.defaultMapColor().col;
                    if (c != 0) color = c;
                } catch (Throwable ignored) {}
                Item item = block.asItem();
                if (item != null && item != Items.AIR) {
                    key = BuiltInRegistries.ITEM.getKey(item).toString();
                    name = new ItemStack(item).getHoverName().getString();
                } else {
                    mult = 0; // water, fire, portals... nothing to collect
                }
            }
            out[i] = new Info(state, id, block, key, name, mult, color);
        }
        return out;
    }

    /** How many items one block of this state costs (0 for the second half of doors and beds). */
    static int materialCount(String state) {
        int b = state.indexOf('[');
        if (b < 0) return 1;
        Map<String, String> props = new HashMap<>();
        for (String kv : state.substring(b + 1, state.length() - 1).split(",")) {
            int e = kv.indexOf('=');
            if (e > 0) props.put(kv.substring(0, e), kv.substring(e + 1));
        }
        if ("upper".equals(props.get("half")) || "head".equals(props.get("part"))) return 0;
        if ("double".equals(props.get("type"))) return 2;
        for (String k : new String[]{"candles", "pickles", "eggs", "layers", "flower_amount", "segment_amount"}) {
            String v = props.get(k);
            if (v != null) try { return Math.max(1, Integer.parseInt(v)); } catch (NumberFormatException ignored) {}
        }
        return 1;
    }

    // ---------------- tick (client thread) ----------------

    static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        inWorld = level != null && player != null;
        if (!inWorld) return;
        px = player.getX(); py = player.getY(); pz = player.getZ();
        currentDimension = dim(level);
        if (++tickCount % 10 == 0) inventory = countInventory(player);

        Active a = active;
        if (a == null || !a.dimension.equals(currentDimension)) return;
        Schematic s = a.s;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int layerArea = s.sizeX * s.sizeZ;
        int end = Math.min(a.nonAir.length, a.cursor + BUDGET_PER_TICK);
        for (int n = a.cursor; n < end; n++) {
            int idx = a.nonAir[n];
            int x = idx % s.sizeX;
            int y = idx / layerArea;
            int z = (idx / s.sizeX) % s.sizeZ;
            int pal = s.blocks[idx];
            int rx = s.minX + x, rz = s.minZ + z;
            int wx = a.wx(rx, rz), wy = a.oy + s.minY + y, wz = a.wz(rx, rz);
            byte st;
            if (!level.hasChunk(wx >> 4, wz >> 4)) {
                st = UNKNOWN;
                a.curLUnknown[y]++;
            } else {
                pos.set(wx, wy, wz);
                BlockState bs = level.getBlockState(pos);
                Block want = a.info[pal].block();
                if (want != null && bs.getBlock() == want) {
                    st = DONE;
                    a.curDone[pal]++;
                    a.curLDone[y]++;
                } else if (bs.isAir() || bs.canBeReplaced()) {
                    st = MISSING;
                    a.curLMissing[y]++;
                } else {
                    st = WRONG;
                    a.curLWrong[y]++;
                }
            }
            a.status[idx] = st;
        }
        a.cursor = end;
        if (a.cursor >= a.nonAir.length) {
            a.stats = new Stats(a.curDone, a.curLDone, a.curLMissing, a.curLWrong, a.curLUnknown);
            a.resetPass();
        }
    }

    private static Map<String, Integer> countInventory(LocalPlayer player) {
        Map<String, Integer> out = new HashMap<>();
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty()) continue;
            out.merge(BuiltInRegistries.ITEM.getKey(st.getItem()).toString(), st.getCount(), Integer::sum);
        }
        return out;
    }

    // ---------------- JSON for the web page (any thread) ----------------

    static String stateJson() {
        Active a = active;
        Json j = new Json().open();
        j.kv("inWorld", inWorld).kv("error", lastError).kv("message", lastMessage);
        j.key("player").open().kv("x", (int) Math.floor(px)).kv("y", (int) Math.floor(py)).kv("z", (int) Math.floor(pz)).close();
        j.kv("loaded", a != null);
        if (a == null) return j.close().toString();
        Schematic s = a.s;
        Stats st = a.stats;
        j.kv("name", s.name).kv("file", Config.file);
        j.key("size").arr().v(s.sizeX).v(s.sizeY).v(s.sizeZ).end();
        j.key("origin").arr().v(a.ox).v(a.oy).v(a.oz).end();
        j.kv("rotation", a.rot).kv("dimensionOk", a.dimension.equals(currentDimension)).kv("scanned", st != null);
        int total = a.nonAir.length, done = 0, missing = 0, wrong = 0, unknown = 0;
        if (st != null) for (int y = 0; y < s.sizeY; y++) {
            done += st.layerDone[y]; missing += st.layerMissing[y]; wrong += st.layerWrong[y]; unknown += st.layerUnknown[y];
        }
        j.key("progress").open().kv("total", total).kv("done", done).kv("missing", missing)
                .kv("wrong", wrong).kv("unknown", unknown).close();
        int cur = a.currentLayer();
        j.kv("layer", cur).kv("layerAuto", a.manualLayer < 0).kv("baseY", a.oy + s.minY);
        j.key("layers").arr();
        for (int y = 0; y < s.sizeY; y++) {
            j.arr().v(a.layerTotal[y]).v(st == null ? 0 : st.layerDone[y]).v(st == null ? 0 : st.layerWrong[y])
                    .v(st == null ? a.layerTotal[y] : st.layerUnknown[y]).end();
        }
        j.end();

        // Materials for the whole build and for the current layer.
        Map<String, long[]> mats = new LinkedHashMap<>(); // total, placed, layerRemaining
        Map<String, Info> names = new HashMap<>();
        for (int p = 0; p < a.info.length; p++) {
            Info in = a.info[p];
            if (in.mult() == 0) continue;
            long[] m = mats.computeIfAbsent(in.key(), k -> new long[3]);
            names.putIfAbsent(in.key(), in);
            m[0] += (long) a.needByPalette[p] * in.mult();
            if (st != null) m[1] += (long) st.doneByPalette[p] * in.mult();
        }
        int base = cur * s.sizeX * s.sizeZ;
        for (int i = base; i < base + s.sizeX * s.sizeZ && i < s.blocks.length; i++) {
            int p = s.blocks[i];
            if (p < 0 || a.status[i] == DONE) continue;
            Info in = a.info[p];
            if (in.mult() == 0) continue;
            mats.get(in.key())[2] += in.mult();
        }
        Map<String, Integer> inv = inventory;
        List<Map.Entry<String, long[]>> list = new ArrayList<>(mats.entrySet());
        list.sort((x, y) -> Long.compare(y.getValue()[0] - y.getValue()[1], x.getValue()[0] - x.getValue()[1]));
        j.key("materials").arr();
        for (Map.Entry<String, long[]> e : list) {
            Info in = names.get(e.getKey());
            long[] m = e.getValue();
            j.open().kv("key", e.getKey()).kv("name", in.name()).kv("color", in.color())
                    .kv("total", m[0]).kv("placed", m[1]).kv("layer", m[2])
                    .kv("inv", inv.getOrDefault(e.getKey(), 0)).close();
        }
        j.end();
        return j.close().toString();
    }

    static String layerJson(int localY) {
        Active a = active;
        if (a == null) return "{\"loaded\":false}";
        Schematic s = a.s;
        if (localY < 0 || localY >= s.sizeY) localY = a.currentLayer();
        // World-space box of this layer after rotation.
        int x0 = a.wx(s.minX, s.minZ), x1 = a.wx(s.minX + s.sizeX - 1, s.minZ + s.sizeZ - 1);
        int z0 = a.wz(s.minX, s.minZ), z1 = a.wz(s.minX + s.sizeX - 1, s.minZ + s.sizeZ - 1);
        int minX = Math.min(x0, x1), minZ = Math.min(z0, z1);
        int w = Math.abs(x1 - x0) + 1, h = Math.abs(z1 - z0) + 1;
        int[] cellPal = new int[w * h];
        Arrays.fill(cellPal, -1);
        char[] cellSt = new char[w * h];
        Arrays.fill(cellSt, '.');
        int wy = a.oy + s.minY + localY;
        Map<Integer, List<int[]>> todo = new LinkedHashMap<>();
        int base = localY * s.sizeX * s.sizeZ;
        for (int z = 0; z < s.sizeZ; z++) {
            for (int x = 0; x < s.sizeX; x++) {
                int idx = base + z * s.sizeX + x;
                int p = s.blocks[idx];
                if (p < 0) continue;
                int rx = s.minX + x, rz = s.minZ + z;
                int wx = a.wx(rx, rz), wz = a.wz(rx, rz);
                int cell = (wz - minZ) * w + (wx - minX);
                cellPal[cell] = p;
                byte st = a.status[idx];
                cellSt[cell] = switch (st) { case DONE -> 'd'; case MISSING -> 'm'; case WRONG -> 'w'; default -> 'u'; };
                if (st == MISSING || st == WRONG) todo.computeIfAbsent(p, k -> new ArrayList<>()).add(new int[]{wx, wy, wz, st});
            }
        }
        Json j = new Json().open();
        j.kv("loaded", true).kv("layer", localY).kv("y", wy).kv("minX", minX).kv("minZ", minZ).kv("w", w).kv("h", h);
        j.key("palette").arr();
        for (Info in : a.info) j.open().kv("name", in.name()).kv("id", in.id()).kv("color", in.color()).close();
        j.end();
        j.key("cells").arr();
        for (int c : cellPal) j.v(c);
        j.end();
        j.kv("status", new String(cellSt));
        // Blocks still to place, grouped by block, nearest first.
        double ppx = px, ppz = pz;
        List<Map.Entry<Integer, List<int[]>>> groups = new ArrayList<>(todo.entrySet());
        groups.sort((g1, g2) -> g2.getValue().size() - g1.getValue().size());
        j.key("todo").arr();
        for (Map.Entry<Integer, List<int[]>> g : groups) {
            Info in = a.info[g.getKey()];
            List<int[]> ps = g.getValue();
            ps.sort((p1, p2) -> Double.compare(dist(p1, ppx, ppz), dist(p2, ppx, ppz)));
            j.open().kv("pal", g.getKey()).kv("name", in.name()).kv("state", in.state()).kv("color", in.color())
                    .kv("count", ps.size());
            j.key("pos").arr();
            for (int i = 0; i < Math.min(ps.size(), 200); i++) {
                int[] q = ps.get(i);
                j.arr().v(q[0]).v(q[1]).v(q[2]).v(q[3]).end();
            }
            j.end().close();
        }
        j.end();
        return j.close().toString();
    }

    private static double dist(int[] p, double x, double z) {
        double dx = p[0] + 0.5 - x, dz = p[2] + 0.5 - z;
        return dx * dx + dz * dz;
    }
}
