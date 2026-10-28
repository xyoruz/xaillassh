package com.sshovpn.app;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ProxyInfo;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;

public class OvpnService extends VpnService implements SshManager.Listener {

    public static final String ACTION_START = "com.sshovpn.app.START";
    public static final String ACTION_STOP = "com.sshovpn.app.STOP";

    private ParcelFileDescriptor tun;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        
        SshManager mgr = SshManager.get();
        
        if (ACTION_STOP.equals(intent.getAction())) {
            mgr.stop();
            closeTun();
            stopSelf();
            return START_NOT_STICKY;
        }

        // MENGAMBIL DATA DARI MAINACTIVITY
        String hostData = intent.getStringExtra("HOST_DATA");
        String sniData = intent.getStringExtra("SNI_DATA");
        String payloadData = intent.getStringExtra("PAYLOAD_DATA");

        if (hostData == null || hostData.isEmpty()) {
            sendStatus("ERROR", "Data Host kosong!");
            stopSelf();
            return START_NOT_STICKY;
        }

        // Memecah data Host (format: host:port@user:pass)
        String[] parts = hostData.split("@");
        if (parts.length != 2) {
            sendStatus("ERROR", "Format Host salah! Gunakan host:port@user:pass");
            stopSelf();
            return START_NOT_STICKY;
        }

        String hostPort = parts[0];
        String userPass = parts[1];

        String[] hp = hostPort.split(":");
        String host = hp[0];
        int port = hp.length > 1 ? Integer.parseInt(hp[1]) : 22;

        String[] up = userPass.split(":");
        String user = up[0];
        String pass = up.length > 1 ? up[1] : "";

        sendStatus("CONNECTING", "Menghubungkan ke " + host + ":" + port);

        try {
            // MEMBUAT KONFIGURASI SESUAI SSHMANAGER ASLI
            SshManager.Config cfg = new SshManager.Config();
            cfg.host = host;
            cfg.port = port;
            cfg.user = user;
            cfg.pass = pass;
            cfg.sni = (sniData != null) ? sniData : "";
            cfg.payload = (payloadData != null) ? payloadData : "";
            
            // Daftarkan listener agar bisa menerima status
            mgr.addListener(this);
            
            // JALANKAN SSH MANAGER
            mgr.start(cfg);

        } catch (Exception e) {
            sendStatus("ERROR", "Gagal memulai: " + e.getMessage());
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    private synchronized void establish() {
        if (tun != null) return;
        try {
            Builder b = new Builder();
            b.setSession("SSH OVPN");
            b.addAddress("10.8.0.2", 32);
            b.addRoute("10.8.0.1", 32);
            b.setMtu(1500);
            try { b.addDisallowedApplication(getPackageName()); } catch (PackageManager.NameNotFoundException ignored) { }
            if (Build.VERSION.SDK_INT >= 29) {
                b.setHttpProxy(ProxyInfo.buildDirectProxy("127.0.0.1", SshManager.LOCAL_PORT));
            }
            tun = b.establish();
            if (tun == null) {
                SshManager.get().stop();
                sendStatus("ERROR", "Gagal membuat antarmuka VPN.");
            }
        } catch (Throwable e) {
            SshManager.get().stop();
            sendStatus("ERROR", "Error VPN: " + e.getMessage());
        }
    }

    private synchronized void closeTun() {
        if (tun != null) {
            try { tun.close(); } catch (Exception ignored) { }
            tun = null;
        }
    }

    // MENGIRIM SINYAL KE MAINACTIVITY
    private void sendStatus(String status, String msg) {
        Intent i = new Intent("VPN_STATUS_UPDATE");
        i.putExtra("STATUS", status);
        i.putExtra("MESSAGE", msg);
        sendBroadcast(i);
    }

    @Override 
    public void onLog(String msg) { 
        sendStatus("LOG", msg);
    }
    
    @Override 
    public void onError(String msg) { 
        sendStatus("ERROR", msg);
    }

    @Override
    public void onState(SshManager.State s) {
        if (s == SshManager.State.CONNECTED) {
            establish();
            sendStatus("CONNECTED", "SSH Terhubung & VPN Aktif!");
        } else if (s == SshManager.State.DISCONNECTED) {
            closeTun();
            sendStatus("DISCONNECTED", "Koneksi terputus.");
            stopSelf();
        }
    }

    @Override
    public void onRevoke() {
        SshManager.get().stop();
        closeTun();
        super.onRevoke();
    }

    @Override
    public void onDestroy() {
        SshManager.get().removeListener(this);
        SshManager.get().stop();
        closeTun();
        super.onDestroy();
    }
}
