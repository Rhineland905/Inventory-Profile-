package dev.schematicprogress;

/** Tiny JSON writer that inserts commas by itself. */
final class Json {
    private final StringBuilder sb = new StringBuilder(4096);
    private boolean needComma;

    private void sep() {
        if (needComma) sb.append(',');
        needComma = false;
    }

    Json open() { sep(); sb.append('{'); return this; }
    Json close() { sb.append('}'); needComma = true; return this; }
    Json arr() { sep(); sb.append('['); return this; }
    Json end() { sb.append(']'); needComma = true; return this; }

    Json key(String k) {
        sep();
        str(k);
        sb.append(':');
        return this;
    }

    Json v(long n) { sep(); sb.append(n); needComma = true; return this; }
    Json v(boolean b) { sep(); sb.append(b); needComma = true; return this; }
    Json v(String s) { sep(); str(s); needComma = true; return this; }

    Json kv(String k, long n) { return key(k).v(n); }
    Json kv(String k, boolean b) { return key(k).v(b); }
    Json kv(String k, String s) { return key(k).v(s); }

    private void str(String s) {
        sb.append('"');
        if (s != null) for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20 || c == '<' || c == '>') sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }

    @Override
    public String toString() {
        return sb.toString();
    }
}
