package dev.schematicprogress;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Small standalone NBT reader. Compounds become Map, lists become List,
 * numbers become boxed Java numbers. Gzip is detected automatically.
 */
final class Nbt {
    private Nbt() {}

    static Map<String, Object> read(Path file) throws IOException {
        try (InputStream raw = new BufferedInputStream(Files.newInputStream(file))) {
            raw.mark(2);
            int a = raw.read(), b = raw.read();
            raw.reset();
            InputStream in = (a == 0x1f && b == 0x8b) ? new GZIPInputStream(raw) : raw;
            DataInputStream data = new DataInputStream(new BufferedInputStream(in));
            int type = data.readUnsignedByte();
            if (type != 10) throw new IOException("Not an NBT compound file");
            data.readUTF(); // root name
            @SuppressWarnings("unchecked")
            Map<String, Object> root = (Map<String, Object>) readPayload(data, 10, 0);
            return root;
        }
    }

    private static Object readPayload(DataInputStream in, int type, int depth) throws IOException {
        if (depth > 512) throw new IOException("NBT too deep");
        switch (type) {
            case 1: return in.readByte();
            case 2: return in.readShort();
            case 3: return in.readInt();
            case 4: return in.readLong();
            case 5: return in.readFloat();
            case 6: return in.readDouble();
            case 7: {
                byte[] arr = new byte[checkLen(in.readInt())];
                in.readFully(arr);
                return arr;
            }
            case 8: return in.readUTF();
            case 9: {
                int inner = in.readUnsignedByte();
                int len = checkLen(in.readInt());
                List<Object> list = new ArrayList<>(Math.min(len, 1 << 16));
                for (int i = 0; i < len; i++) list.add(readPayload(in, inner, depth + 1));
                return list;
            }
            case 10: {
                Map<String, Object> map = new LinkedHashMap<>();
                while (true) {
                    int t = in.readUnsignedByte();
                    if (t == 0) return map;
                    String name = in.readUTF();
                    map.put(name, readPayload(in, t, depth + 1));
                }
            }
            case 11: {
                int[] arr = new int[checkLen(in.readInt())];
                for (int i = 0; i < arr.length; i++) arr[i] = in.readInt();
                return arr;
            }
            case 12: {
                long[] arr = new long[checkLen(in.readInt())];
                for (int i = 0; i < arr.length; i++) arr[i] = in.readLong();
                return arr;
            }
            default: throw new IOException("Unknown NBT tag " + type);
        }
    }

    private static int checkLen(int len) throws IOException {
        if (len < 0 || len > 256 * 1024 * 1024) throw new IOException("Bad NBT length " + len);
        return len;
    }

    // ---- helpers for reading the parsed tree ----

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : null;
    }

    @SuppressWarnings("unchecked")
    static List<Object> list(Object o) {
        return o instanceof List ? (List<Object>) o : null;
    }

    static int num(Object o, int def) {
        return o instanceof Number n ? n.intValue() : def;
    }
}
