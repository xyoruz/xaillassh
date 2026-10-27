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
        SshManager.Config cfg = SshManager.pending;
        if (cfg == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        mgr.addListener(this);
        mgr.start(cfg);
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
            }
        } catch (Throwable e) {
            SshManager.get().stop();
        }
    }

    private synchronized void closeTun() {
        if (tun != null) {
            try { tun.close(); } catch (Exception ignored) { }
            tun = null;
        }
    }

    @Override public void onLog(String msg) { }
    @Override public void onError(String msg) { }

    @Override
    public void onState(SshManager.State s) {
        if (s == SshManager.State.CONNECTED) {
            establish();
        } else if (s == SshManager.State.DISCONNECTED) {
            closeTun();
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
