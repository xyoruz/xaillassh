package com.sshovpn.app;

import com.jcraft.jsch.ChannelDirectTCPIP;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UIKeyboardInteractive;
import com.jcraft.jsch.UserInfo;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.io.SequenceInputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class SshManager {

    public static final int LOCAL_PORT = 1080;

    public enum State { DISCONNECTED, CONNECTING, CONNECTED }

    public static class Config {
        public String host = "", user = "", pass = "", sni = "", payload = "", proxyHost = "";
        public int port = 22, proxyPort = 0;
        public boolean ssl, autoReconnect = true, split;
    }

    public interface Listener {
        void onLog(String msg);
        void onState(State s);
        void onError(String msg);
    }

    public static volatile Config pending;

    private static final SshManager INSTANCE = new SshManager();
    public static SshManager get() { return INSTANCE; }

    static {
        JSch.setLogger(new com.jcraft.jsch.Logger() {
            @Override public boolean isEnabled(int level) { return level >= com.jcraft.jsch.Logger.INFO; }
            @Override public void log(int level, String message) { INSTANCE.log("[ssh] " + message); }
        });
    }

    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final StringBuilder logBuf = new StringBuilder();
    private volatile State state = State.DISCONNECTED;
    private volatile Session session;
    private volatile boolean stopRequested, fatal;
    private Thread worker;
    private ServerSocket server;
    private ExecutorService pool;

    public void addListener(Listener l) { if (!listeners.contains(l)) listeners.add(l); }
    public void removeListener(Listener l) { listeners.remove(l); }
    public State getState() { return state; }

    public String getLogText() {
        synchronized (logBuf) { return logBuf.toString().trim(); }
    }

    public synchronized void start(final Config cfg) {
        if (worker != null && worker.isAlive()) return;
        stopRequested = false;
        worker = new Thread(() -> runLoop(cfg), "ssh-worker");
        worker.start();
    }

    public synchronized void stop() {
        stopRequested = true;
        closeSession();
        stopProxy();
        if (worker != null) worker.interrupt();
        setState(State.DISCONNECTED);
    }

    void log(String msg) {
        synchronized (logBuf) {
            logBuf.append("\n> ").append(msg);
            if (logBuf.length() > 8000) logBuf.delete(0, 4000);
        }
        for (Listener l : listeners) l.onLog(msg);
    }

    private void error(String msg) {
        log("ERROR: " + msg);
        for (Listener l : listeners) l.onError(msg);
    }

    private void setState(State s) {
        state = s;
        for (Listener l : listeners) l.onState(s);
    }

    private void runLoop(Config cfg) {
        int attempt = 0;
        while (!stopRequested) {
            fatal = false;
            setState(State.CONNECTING);
            try {
                openSession(cfg);
                startProxy();
                attempt = 0;
                setState(State.CONNECTED);
                log("Terhubung. Proxy lokal SOCKS5/HTTP di 127.0.0.1:" + LOCAL_PORT);
                while (!stopRequested && session != null && session.isConnected()) {
                    Thread.sleep(1000);
                }
                if (!stopRequested) log("Koneksi SSH terputus.");
            } catch (InterruptedException ie) {
                break;
            } catch (Throwable e) {
                if (!stopRequested) error(friendly(e));
            } finally {
                closeSession();
            }
            if (stopRequested || fatal || !cfg.autoReconnect) break;
            if (++attempt > 20) { log("Auto reconnect berhenti setelah 20 percobaan."); break; }
            setState(State.CONNECTING);
            log("Reconnect dalam 3 detik (#" + attempt + ")...");
            try { Thread.sleep(3000); } catch (InterruptedException ie) { break; }
        }
        stopProxy();
        setState(State.DISCONNECTED);
    }

    private void openSession(Config c) throws Exception {
        log("Memulai SSH ke " + c.host + ":" + c.port + " (user " + c.user + ")");
        JSch js = new JSch();
        Session s = js.getSession(c.user, c.host, c.port);
        s.setPassword(c.pass);
        s.setUserInfo(new PassInfo(c.pass));
        s.setSocketFactory(new TunnelFactory(c));

        Properties p = new Properties();
        p.put("StrictHostKeyChecking", "no");
        p.put("PreferredAuthentications", "password,keyboard-interactive");
        p.put("server_host_key", "ssh-ed25519,ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521,rsa-sha2-512,rsa-sha2-256,ssh-rsa");
        p.put("kex", "curve25519-sha256,curve25519-sha256@libssh.org,ecdh-sha2-nistp256,ecdh-sha2-nistp384,ecdh-sha2-nistp521,diffie-hellman-group-exchange-sha256,diffie-hellman-group16-sha512,diffie-hellman-group14-sha256,diffie-hellman-group14-sha1,diffie-hellman-group-exchange-sha1");
        String ciphers = "aes128-ctr,aes192-ctr,aes256-ctr,aes128-gcm@openssh.com,aes256-gcm@openssh.com,aes128-cbc,aes192-cbc,aes256-cbc";
        p.put("cipher.s2c", ciphers);
        p.put("cipher.c2s", ciphers);
        String macs = "hmac-sha2-256,hmac-sha2-512,hmac-sha1";
        p.put("mac.s2c", macs);
        p.put("mac.c2s", macs);
        s.setConfig(p);

        s.setServerAliveInterval(15000);
        s.setServerAliveCountMax(4);
        s.connect(30000);
        session = s;
        log("Login SSH berhasil.");
    }

    private void closeSession() {
        Session s = session;
        session = null;
        if (s != null) {
            try { s.disconnect(); } catch (Throwable ignored) { }
        }
    }

    private String friendly(Throwable e) {
        String m = String.valueOf(e);
        Throwable c = e.getCause();
        if (c != null) m += " / " + c;
        if (m.contains("Auth fail") || m.contains("Auth cancel")) {
            fatal = true;
            return "Autentikasi gagal. Cek username/password, atau akun expired / limit login penuh.";
        }
        if (m.contains("Algorithm negotiation fail")) return "Negosiasi algoritma SSH gagal: " + m;
        if (e instanceof UnknownHostException || m.contains("UnknownHost")) return "Host tidak ditemukan (DNS gagal / tidak ada internet).";
        if (m.contains("Connection refused")) return "Koneksi ditolak server (port salah / server mati).";
        if (m.contains("timeout") || m.contains("timed out")) return "Timeout. Server/proxy tidak merespons.";
        if (m.contains("End of IO Stream Read") || m.contains("connection is closed by foreign host")
                || m.contains("Connection reset") || m.contains("Broken pipe")) {
            return "Server menutup koneksi sebelum login. Biasanya payload/SNI/proxy salah, atau port itu bukan SSH.";
        }
        return e.getMessage() != null ? e.getMessage() : m;
    }

    private static void closeQuiet(Closeable c) {
        if (c != null) try { c.close(); } catch (Throwable ignored) { }
    }

    private static class PassInfo implements UserInfo, UIKeyboardInteractive {
        private final String pass;
        PassInfo(String pass) { this.pass = pass; }
        @Override public String getPassphrase() { return null; }
        @Override public String getPassword() { return pass; }
        @Override public boolean promptPassword(String message) { return true; }
        @Override public boolean promptPassphrase(String message) { return false; }
        @Override public boolean promptYesNo(String message) { return true; }
        @Override public void showMessage(String message) { INSTANCE.log("[server] " + message); }
        @Override public String[] promptKeyboardInteractive(String dest, String name, String instruction,
                                                            String[] prompt, boolean[] echo) {
            String[] r = new String[prompt == null ? 0 : prompt.length];
            for (int i = 0; i < r.length; i++) r[i] = pass;
            return r;
        }
    }

    private class TunnelFactory implements com.jcraft.jsch.SocketFactory {
        private final Config c;
        private InputStream in;
        private OutputStream out;

        TunnelFactory(Config c) { this.c = c; }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            boolean viaProxy = !c.proxyHost.isEmpty() && c.proxyPort > 0;
            String th = viaProxy ? c.proxyHost : host;
            int tp = viaProxy ? c.proxyPort : port;
            log("TCP connect " + th + ":" + tp + (viaProxy ? " (remote proxy)" : ""));
            Socket sock = new Socket();
            sock.connect(new InetSocketAddress(th, tp), 15000);
            sock.setTcpNoDelay(true);
            sock.setKeepAlive(true);

            if (c.ssl) {
                String sni = c.sni.trim().isEmpty() ? host : c.sni.trim();
                log("TLS handshake, SNI = " + sni);
                try {
                    SSLContext ctx = SSLContext.getInstance("TLS");
                    ctx.init(null, new TrustManager[]{ new X509TrustManager() {
                        @Override public void checkClientTrusted(X509Certificate[] chain, String t) { }
                        @Override public void checkServerTrusted(X509Certificate[] chain, String t) { }
                        @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    }}, new SecureRandom());
                    SSLSocket ss = (SSLSocket) ctx.getSocketFactory().createSocket(sock, sni, tp, true);
                    try {
                        SSLParameters sp = ss.getSSLParameters();
                        sp.setServerNames(Collections.singletonList(new SNIHostName(sni)));
                        ss.setSSLParameters(sp);
                    } catch (IllegalArgumentException ignored) { }
                    ss.setSoTimeout(15000);
                    ss.startHandshake();
                    ss.setSoTimeout(0);
                    sock = ss;
                } catch (Exception e) {
                    closeQuiet(sock);
                    throw new IOException("TLS gagal: " + e.getMessage(), e);
                }
            }

            InputStream rawIn = sock.getInputStream();
            OutputStream rawOut = sock.getOutputStream();
            PushbackInputStream pin = new PushbackInputStream(rawIn, 16384);

            String payload = c.payload.replace("\r", "").replace("\n", "").trim();
            if (payload.isEmpty() && viaProxy) {
                payload = "CONNECT [host_port] HTTP/1.1[crlf]Host: [host_port][crlf][crlf]";
            }
            if (!payload.isEmpty()) {
                try {
                    sendPayload(rawOut, payload, host, port, c.split);
                    readReply(sock, pin);
                } catch (IOException e) {
                    closeQuiet(sock);
                    throw e;
                }
            }
            this.in = pin;
            this.out = rawOut;
            return sock;
        }

        @Override public InputStream getInputStream(Socket socket) { return in; }
        @Override public OutputStream getOutputStream(Socket socket) { return out; }
    }

    private static String rep(String s, String token, String val) {
        return s.replaceAll("(?i)" + Pattern.quote(token), Matcher.quoteReplacement(val));
    }

    private void sendPayload(OutputStream os, String raw, String host, int port, boolean split) throws IOException {
        String hp = host + ":" + port;
        String p = raw;
        p = rep(p, "[netData]", "CONNECT " + hp + " HTTP/1.1");
        p = rep(p, "[host_port]", hp);
        p = rep(p, "[host]", host);
        p = rep(p, "[port]", String.valueOf(port));
        p = rep(p, "[protocol]", "HTTP/1.1");
        p = rep(p, "[crlf]", "\r\n");
        p = rep(p, "[cr]", "\r");
        p = rep(p, "[lf]", "\n");

        String[] parts = p.split("(?i)\\[split\\]");
        if (parts.length == 1 && split) {
            int i = p.indexOf(' ');
            if (i > 0 && i < p.length() - 1) parts = new String[]{ p.substring(0, i + 1), p.substring(i + 1) };
        }
        int sent = 0;
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) continue;
            byte[] b = parts[i].getBytes(StandardCharsets.ISO_8859_1);
            os.write(b);
            os.flush();
            sent += b.length;
            if (i < parts.length - 1) {
                try { Thread.sleep(250); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); throw new IOException("Dibatalkan"); }
            }
        }
        log("Payload terkirim (" + sent + " byte" + (parts.length > 1 ? ", " + parts.length + " bagian" : "") + ")");
    }

    private void readReply(Socket sock, PushbackInputStream pin) throws IOException {
        byte[] first = new byte[4];
        int n = 0;
        try {
            sock.setSoTimeout(8000);
            while (n < 4) {
                int r = pin.read(first, n, 4 - n);
                if (r < 0) throw new IOException("Server menutup koneksi setelah payload dikirim.");
                n += r;
            }
            if (first[0] == 'H' && first[1] == 'T' && first[2] == 'T' && first[3] == 'P') {
                StringBuilder sb = new StringBuilder("HTTP");
                int last4 = 0;
                int x;
                while ((x = pin.read()) >= 0) {
                    sb.append((char) x);
                    last4 = (last4 << 8) | (x & 0xFF);
                    if (last4 == 0x0D0A0D0A || sb.length() > 16384) break;
                }
                String line = sb.toString().split("\r\n")[0];
                log("Balasan: " + line);
                String[] sp = line.split(" ");
                int code = 0;
                try { code = Integer.parseInt(sp[1]); } catch (Exception ignored) { }
                if (code >= 300) throw new IOException("Payload ditolak server/proxy: " + line);
            } else {
                pin.unread(first, 0, n);
                n = 0;
            }
        } catch (SocketTimeoutException e) {
            if (n > 0) pin.unread(first, 0, n);
            log("Tidak ada balasan HTTP, lanjut handshake SSH.");
        } finally {
            sock.setSoTimeout(0);
        }
    }

    private synchronized void startProxy() throws IOException {
        if (server != null && !server.isClosed()) return;
        final ServerSocket ss = new ServerSocket();
        ss.setReuseAddress(true);
        ss.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), LOCAL_PORT));
        server = ss;
        pool = Executors.newCachedThreadPool();
        final ExecutorService ex = pool;
        Thread t = new Thread(() -> {
            while (!ss.isClosed()) {
                try {
                    final Socket cl = ss.accept();
                    ex.execute(() -> handle(cl));
                } catch (Throwable e) {
                    break;
                }
            }
        }, "proxy-accept");
        t.setDaemon(true);
        t.start();
    }

    private synchronized void stopProxy() {
        closeQuiet(server);
        server = null;
        if (pool != null) { pool.shutdownNow(); pool = null; }
    }

    private void handle(Socket c) {
        try {
            c.setTcpNoDelay(true);
            InputStream in = new BufferedInputStream(c.getInputStream());
            OutputStream out = c.getOutputStream();
            int b = in.read();
            if (b < 0) return;
            if (b == 5) socks5(c, in, out);
            else http(c, b, in, out);
        } catch (Throwable ignored) {
        } finally {
            closeQuiet(c);
        }
    }

    private static void readFully(InputStream in, byte[] buf) throws IOException {
        int n = 0;
        while (n < buf.length) {
            int r = in.read(buf, n, buf.length - n);
            if (r < 0) throw new IOException("EOF");
            n += r;
        }
    }

    private void socks5(Socket c, InputStream in, OutputStream out) throws Exception {
        int nm = in.read();
        readFully(in, new byte[nm]);
        out.write(new byte[]{5, 0});
        out.flush();
        int ver = in.read();
        int cmd = in.read();
        in.read();
        int atyp = in.read();
        String host;
        if (atyp == 1) {
            byte[] a = new byte[4]; readFully(in, a);
            host = InetAddress.getByAddress(a).getHostAddress();
        } else if (atyp == 3) {
            byte[] a = new byte[in.read()]; readFully(in, a);
            host = new String(a, StandardCharsets.UTF_8);
        } else if (atyp == 4) {
            byte[] a = new byte[16]; readFully(in, a);
            host = InetAddress.getByAddress(a).getHostAddress();
        } else {
            out.write(new byte[]{5, 8, 0, 1, 0, 0, 0, 0, 0, 0});
            return;
        }
        int port = (in.read() << 8) | in.read();
        if (ver != 5 || cmd != 1) {
            out.write(new byte[]{5, 7, 0, 1, 0, 0, 0, 0, 0, 0});
            return;
        }
        tunnel(c, in, out, host, port,
                new byte[]{5, 0, 0, 1, 0, 0, 0, 0, 0, 0},
                new byte[]{5, 5, 0, 1, 0, 0, 0, 0, 0, 0}, null);
    }

    private void http(Socket c, int firstByte, InputStream in, OutputStream out) throws Exception {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        head.write(firstByte);
        int last4 = firstByte, x;
        while ((x = in.read()) >= 0) {
            head.write(x);
            last4 = (last4 << 8) | x;
            if (last4 == 0x0D0A0D0A) break;
            if (head.size() > 32768) return;
        }
        String h = new String(head.toByteArray(), StandardCharsets.ISO_8859_1);
        String[] lines = h.split("\r\n");
        String[] rl = lines[0].split(" ");
        if (rl.length < 3) return;
        String method = rl[0], target = rl[1], ver = rl[2];
        byte[] fail = "HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);

        if (method.equalsIgnoreCase("CONNECT")) {
            int i = target.lastIndexOf(':');
            String host = i > 0 ? target.substring(0, i) : target;
            int port = i > 0 ? Integer.parseInt(target.substring(i + 1)) : 443;
            tunnel(c, in, out, host, port,
                    "HTTP/1.1 200 Connection Established\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1), fail, null);
            return;
        }
        if (!target.toLowerCase().startsWith("http://")) {
            out.write("HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
            return;
        }
        URI u = new URI(target);
        String host = u.getHost();
        int port = u.getPort() < 0 ? 80 : u.getPort();
        String path = u.getRawPath() == null || u.getRawPath().isEmpty() ? "/" : u.getRawPath();
        if (u.getRawQuery() != null) path += "?" + u.getRawQuery();
        StringBuilder sb = new StringBuilder(method).append(' ').append(path).append(' ').append(ver).append("\r\n");
        for (int i = 1; i < lines.length; i++) {
            String l = lines[i];
            String lo = l.toLowerCase();
            if (l.isEmpty() || lo.startsWith("proxy-connection:") || lo.startsWith("connection:") || lo.startsWith("proxy-authorization:")) continue;
            sb.append(l).append("\r\n");
        }
        sb.append("Connection: close\r\n\r\n");
        tunnel(c, in, out, host, port, null, fail, sb.toString().getBytes(StandardCharsets.ISO_8859_1));
    }

    private static class Gate extends OutputStream {
        private final OutputStream o;
        private final CountDownLatch latch = new CountDownLatch(1);
        Gate(OutputStream o) { this.o = o; }
        void open() { latch.countDown(); }
        private void waitOpen() throws IOException {
            try {
                if (!latch.await(30, TimeUnit.SECONDS)) throw new IOException("gate timeout");
            } catch (InterruptedException e) { throw new IOException("interrupted"); }
        }
        @Override public void write(int b) throws IOException { waitOpen(); o.write(b); }
        @Override public void write(byte[] b, int off, int len) throws IOException { waitOpen(); o.write(b, off, len); }
        @Override public void flush() throws IOException { o.flush(); }
    }

    private void tunnel(Socket c, InputStream in, OutputStream out, String host, int port,
                        byte[] okReply, byte[] failReply, byte[] prefix) throws Exception {
        Session s = session;
        if (s == null || !s.isConnected()) { out.write(failReply); out.flush(); return; }
        ChannelDirectTCPIP ch = null;
        try {
            ch = (ChannelDirectTCPIP) s.getStreamForwarder(host, port);
            Gate gate = new Gate(out);
            InputStream src = prefix == null ? in : new SequenceInputStream(new ByteArrayInputStream(prefix), in);
            ch.setInputStream(src);
            ch.setOutputStream(gate);
            try {
                ch.connect(15000);
            } catch (JSchException e) {
                out.write(failReply);
                out.flush();
                return;
            }
            if (okReply != null) { out.write(okReply); out.flush(); }
            gate.open();
            while (!ch.isClosed() && !c.isClosed() && !Thread.currentThread().isInterrupted()) {
                Thread.sleep(150);
            }
        } finally {
            if (ch != null) try { ch.disconnect(); } catch (Throwable ignored) { }
        }
    }
}
