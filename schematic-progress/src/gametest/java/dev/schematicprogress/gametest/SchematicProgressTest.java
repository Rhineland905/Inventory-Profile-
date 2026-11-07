package dev.schematicprogress.gametest;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Writes a 5x3x5 test house (.schem), builds part of it with commands, loads it through the
 * web API and saves the API answers to schematic-progress-test/ for checking.
 */
public class SchematicProgressTest implements FabricClientGameTest {
    private static final String BASE = "http://127.0.0.1:8765";

    @Override
    public void runTest(ClientGameTestContext context) {
        Path game = FabricLoader.getInstance().getGameDir();
        Path out = game.resolve("schematic-progress-test");
        try {
            Files.createDirectories(out);
            writeSchem(game.resolve("schematics").resolve("test_house.schem"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try (TestSingleplayerContext sp = context.worldBuilder().create()) {
            context.waitTicks(60);
            for (String c : new String[]{
                    "gamemode creative @a", "time set day", "weather clear",
                    "fill 0 100 0 4 100 4 stone_bricks",
                    "setblock 2 100 2 air",
                    "setblock 0 100 1 dirt",
                    "setblock 0 101 0 stone_bricks", "setblock 4 101 0 stone_bricks", "setblock 1 101 0 glass",
                    "give @a stone_bricks 10", "give @a glass 3", "give @a oak_planks 64",
                    "tp @a 2 104 -5 0 45"}) {
                sp.getServer().runCommand(c);
            }
            context.waitTicks(60);
            save(out, "files.json", get("/api/files"));
            save(out, "load.json", post("/api/load?file=test_house.schem"));
            context.waitTicks(10);
            save(out, "origin.json", post("/api/origin?x=0&y=100&z=0"));
            context.waitTicks(40);
            save(out, "state.json", get("/api/state"));
            save(out, "layer0.json", get("/api/layer?y=0"));
            save(out, "layer1.json", get("/api/layer?y=1"));
            save(out, "page.html", get("/"));
            post("/api/rotate?r=1");
            context.waitTicks(40);
            save(out, "state_rot90.json", get("/api/state"));
            save(out, "layer0_rot90.json", get("/api/layer?y=0"));
            context.takeScreenshot("sp_world");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void save(Path dir, String name, String body) throws IOException {
        Files.writeString(dir.resolve(name), body);
        System.out.println("[sp-test] " + name + ": " + (body.length() > 600 ? body.substring(0, 600) + "..." : body));
    }

    private static String get(String path) throws IOException {
        return call(path, "GET");
    }

    private static String post(String path) throws IOException {
        return call(path, "POST");
    }

    private static String call(String path, String method) throws IOException {
        HttpURLConnection c = (HttpURLConnection) URI.create(BASE + path).toURL().openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(3000);
        c.setReadTimeout(5000);
        try (InputStream in = c.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Sponge v2: floor of stone bricks, ring of bricks and glass, plank roof. */
    private static void writeSchem(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        int w = 5, h = 3, l = 5;
        byte[] data = new byte[w * h * l];
        for (int y = 0; y < h; y++)
            for (int z = 0; z < l; z++)
                for (int x = 0; x < w; x++) {
                    boolean edgeX = x == 0 || x == w - 1, edgeZ = z == 0 || z == l - 1;
                    int v;
                    if (y == 0) v = 1;
                    else if (y == 2) v = 3;
                    else if (edgeX && edgeZ) v = 1;
                    else if (edgeX || edgeZ) v = 2;
                    else v = 0;
                    data[(y * l + z) * w + x] = (byte) v;
                }
        try (DataOutputStream o = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(file)))) {
            o.writeByte(10); o.writeUTF("Schematic");
            o.writeByte(3); o.writeUTF("Version"); o.writeInt(2);
            o.writeByte(2); o.writeUTF("Width"); o.writeShort(w);
            o.writeByte(2); o.writeUTF("Height"); o.writeShort(h);
            o.writeByte(2); o.writeUTF("Length"); o.writeShort(l);
            o.writeByte(10); o.writeUTF("Palette");
            String[] pal = {"minecraft:air", "minecraft:stone_bricks", "minecraft:glass", "minecraft:oak_planks"};
            for (int i = 0; i < pal.length; i++) { o.writeByte(3); o.writeUTF(pal[i]); o.writeInt(i); }
            o.writeByte(0);
            o.writeByte(7); o.writeUTF("BlockData"); o.writeInt(data.length); o.write(data);
            o.writeByte(0);
        }
    }
}
