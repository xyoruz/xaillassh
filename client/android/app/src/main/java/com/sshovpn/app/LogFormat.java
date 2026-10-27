package com.sshovpn.app;

final class LogFormat {
    private LogFormat() { }

    static String banner(String raw) {
        if (raw == null) return "";
        String t = raw.replace("\r", "")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</?(p|div|li|tr)[^>]*>", "\n")
                .replaceAll("<[^>]*>", "")
                .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'");
        StringBuilder sb = new StringBuilder("┌─ BANNER SERVER");
        int n = 0;
        for (String line : t.split("\n")) {
            line = line.trim().replaceAll("\\s{2,}", " ");
            if (line.isEmpty() || line.matches("[-=_*~.\\s]+")) continue;
            if (++n > 8) { sb.append("\n│ …"); break; }
            if (line.length() > 44) line = line.substring(0, 43) + "…";
            sb.append("\n│ ").append(line);
        }
        if (n == 0) return "";
        return sb.append("\n└─").toString();
    }
}
