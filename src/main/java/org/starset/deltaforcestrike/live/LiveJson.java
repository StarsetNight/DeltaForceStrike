package org.starset.deltaforcestrike.live;

import java.util.Collection;
import java.util.Map;

/**
 * 轻量 JSON 字符串构建（无第三方依赖）。
 */
public final class LiveJson {

    private LiveJson() {}

    public static String escape(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    public static String str(String s) {
        return "\"" + escape(s) + "\"";
    }

    public static String num(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return "0";
        }
        if (v == (long) v) {
            return Long.toString((long) v);
        }
        return Double.toString(v);
    }

    public static String bool(boolean b) {
        return b ? "true" : "false";
    }

    public static String nul() {
        return "null";
    }

    public static String obj(Map<String, String> fields) {
        StringBuilder sb = new StringBuilder(64);
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, String> e : fields.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(str(e.getKey())).append(':').append(e.getValue());
        }
        sb.append('}');
        return sb.toString();
    }

    public static String arr(Collection<String> elements) {
        StringBuilder sb = new StringBuilder(64);
        sb.append('[');
        boolean first = true;
        for (String el : elements) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(el);
        }
        sb.append(']');
        return sb.toString();
    }
}
