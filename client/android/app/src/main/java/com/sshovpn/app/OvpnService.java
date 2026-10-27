package com.sshovpn.app;

import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;

public class OvpnService extends VpnService {
    private ParcelFileDescriptor vpnInterface = null;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (vpnInterface == null) {
            Builder builder = new Builder();
            builder.setSession("SSH OVPN");
            builder.addAddress("10.0.0.2", 24);
            builder.addDnsServer("1.1.1.1");
            builder.addRoute("0.0.0.0", 0); // Route all traffic
            vpnInterface = builder.establish();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (vpnInterface != null) {
            try { vpnInterface.close(); } catch (Exception ignored) {}
            vpnInterface = null;
        }
    }
}
