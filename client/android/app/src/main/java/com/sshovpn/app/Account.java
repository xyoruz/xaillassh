package com.sshovpn.app;

import org.json.JSONException;
import org.json.JSONObject;

/** Satu profil akun SSH. Format kunci JSON "host" dipertahankan agar akun lama tetap terbaca. */
public class Account {

    public String name = "";
    /** Format: host:port@user:pass */
    public String connect = "";
    public String sni = "";
    public String payload = "";
    /** Format: host:port (kosong = tanpa proxy) */
    public String proxy = "";
    public boolean tls;
    public boolean split;
    public boolean autoReconnect = true;

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("name", name);
        o.put("host", connect);
        o.put("sni", sni);
        o.put("payload", payload);
        o.put("proxy", proxy);
        o.put("tls", tls);
        o.put("split", split);
        o.put("reconnect", autoReconnect);
        return o;
    }

    public static Account fromJson(JSONObject o) {
        Account a = new Account();
        a.name = o.optString("name", "").trim();
        a.connect = o.optString("host", "").trim();
        a.sni = o.optString("sni", "").trim();
        a.payload = o.optString("payload", "");
        a.proxy = o.optString("proxy", "").trim();
        // Akun versi lama belum punya flag tls: anggap TLS bila SNI diisi.
        a.tls = o.has("tls") ? o.optBoolean("tls") : !a.sni.isEmpty();
        a.split = o.optBoolean("split", false);
        a.autoReconnect = o.optBoolean("reconnect", true);
        return a;
    }

    public Account copy() {
        Account a = new Account();
        a.name = name; a.connect = connect; a.sni = sni; a.payload = payload;
        a.proxy = proxy; a.tls = tls; a.split = split; a.autoReconnect = autoReconnect;
        return a;
    }

    /** "host:port • user" tanpa password, untuk ditampilkan di UI. */
    public String summary() {
        int at = connect.indexOf('@');
        if (at <= 0) return connect;
        String hp = connect.substring(0, at);
        String up = connect.substring(at + 1);
        int c = up.indexOf(':');
        String user = c > 0 ? up.substring(0, c) : up;
        return hp + " • " + user;
    }

    /** Ubah ke konfigurasi tunnel. Melempar IllegalArgumentException dengan pesan yang ramah. */
    public SshManager.Config toConfig() {
        SshManager.Config cfg = new SshManager.Config();

        String s = connect.trim();
        int at = s.indexOf('@');
        if (at <= 0 || at == s.length() - 1) {
            throw new IllegalArgumentException("Format harus host:port@user:pass");
        }
        String hp = s.substring(0, at);
        String up = s.substring(at + 1);

        int c = up.indexOf(':');
        if (c <= 0) throw new IllegalArgumentException("User dan password harus dipisah ':'");
        cfg.user = up.substring(0, c);
        cfg.pass = up.substring(c + 1);

        int hc = hp.lastIndexOf(':');
        if (hc > 0) {
            cfg.host = hp.substring(0, hc).trim();
            cfg.port = parsePort(hp.substring(hc + 1));
        } else {
            cfg.host = hp.trim();
            cfg.port = 22;
        }
        if (cfg.host.isEmpty()) throw new IllegalArgumentException("Host kosong");

        String px = proxy.trim();
        if (!px.isEmpty()) {
            int pc = px.lastIndexOf(':');
            if (pc <= 0) throw new IllegalArgumentException("Proxy harus berformat host:port");
            cfg.proxyHost = px.substring(0, pc).trim();
            cfg.proxyPort = parsePort(px.substring(pc + 1));
        }

        cfg.sni = sni.trim();
        cfg.payload = payload;
        cfg.ssl = tls;
        cfg.split = split;
        cfg.autoReconnect = autoReconnect;
        return cfg;
    }

    private static int parsePort(String raw) {
        try {
            int p = Integer.parseInt(raw.trim());
            if (p < 1 || p > 65535) throw new NumberFormatException();
            return p;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Port tidak valid: " + raw.trim());
        }
    }
}
