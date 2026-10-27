package com.sshovpn.app;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import java.util.Properties;

public class SshManager {
    private Session session;
    private Thread sshThread;

    public interface SshCallback {
        void onLog(String msg);
        void onConnected();
        void onDisconnected();
        void onError(String msg);
    }

    public void connect(final String host, final int port, final String user, final String pass, final int localPort, final SshCallback cb) {
        sshThread = new Thread(() -> {
            try {
                cb.onLog("Connecting to " + host + ":" + port);
                JSch jsch = new JSch();
                session = jsch.getSession(user, host, port);
                session.setPassword(pass);

                Properties config = new Properties();
                config.put("StrictHostKeyChecking", "no");
                session.setConfig(config);
                session.setTimeout(15000);

                cb.onLog("Authenticating...");
                session.connect();

                cb.onLog("Setting up SOCKS proxy on port " + localPort);
                session.setPortForwardingD(localPort, "127.0.0.1", localPort);

                cb.onLog("Connection Established!");
                cb.onConnected();

            } catch (Exception e) {
                cb.onError(e.getMessage());
                disconnect(cb);
            }
        });
        sshThread.start();
    }

    public void disconnect(SshCallback cb) {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
        if (cb != null) cb.onDisconnected();
    }

    public boolean isConnected() {
        return session != null && session.isConnected();
    }
}
