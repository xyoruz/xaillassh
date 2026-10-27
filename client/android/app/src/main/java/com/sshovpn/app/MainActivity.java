package com.sshovpn.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity implements SshManager.Listener {

    private LinearLayout formSsh, formOvpn, formConfig;
    private TextView tabSsh, tabOvpn, tabConfig, tvStatus, tvLog;
    private Button btnConnect, btnParse;
    private EditText etQuickConnect, etSni, etPayload, etProxy, etDns;
    private CheckBox cbSsl, cbAutoReconnect, cbBypassDpi;

    private SharedPreferences prefs;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<Intent> vpnPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    appendLog("Izin VPN diberikan.");
                    startTunnel();
                } else {
                    appendLog("Izin VPN ditolak. Koneksi dibatalkan.");
                    Toast.makeText(this, "Aplikasi butuh izin VPN untuk terhubung!", Toast.LENGTH_LONG).show();
                    render(SshManager.State.DISCONNECTED);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences("cfg", MODE_PRIVATE);

        initViews();
        loadPrefs();
        setupTabs();

        btnConnect.setOnClickListener(v -> {
            if (SshManager.get().getState() != SshManager.State.DISCONNECTED) {
                appendLog("Disconnecting...");
                SshManager.get().stop();
                stopService(new Intent(this, OvpnService.class));
            } else {
                startConnectionProcess();
            }
        });

        btnParse.setOnClickListener(v -> {
            String q = etQuickConnect.getText().toString().trim();
            if (q.isEmpty()) {
                Toast.makeText(this, "Isi format quick connect dulu!", Toast.LENGTH_SHORT).show();
                return;
            }
            SshManager.Config c = parseQuick(q);
            if (c == null) {
                Toast.makeText(this, "Format salah! Gunakan: host:port@user:pass", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Format berhasil diurai!", Toast.LENGTH_SHORT).show();
                appendLog("Parsed: " + c.host + " | Port: " + c.port + " | User: " + c.user);
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        SshManager m = SshManager.get();
        m.addListener(this);
        String t = m.getLogText();
        if (!t.isEmpty()) tvLog.setText(t);
        render(m.getState());
    }

    @Override
    protected void onStop() {
        SshManager.get().removeListener(this);
        super.onStop();
    }

    private void initViews() {
        formSsh = findViewById(R.id.formSsh);
        formOvpn = findViewById(R.id.formOvpn);
        formConfig = findViewById(R.id.formConfig);
        tabSsh = findViewById(R.id.tabSsh);
        tabOvpn = findViewById(R.id.tabOvpn);
        tabConfig = findViewById(R.id.tabConfig);
        tvStatus = findViewById(R.id.tvStatus);
        tvLog = findViewById(R.id.tvLog);
        tvLog.setMovementMethod(new ScrollingMovementMethod());
        btnConnect = findViewById(R.id.btnConnect);
        btnParse = findViewById(R.id.btnParse);
        etQuickConnect = findViewById(R.id.etQuickConnect);
        etSni = findViewById(R.id.etSni);
        etPayload = findViewById(R.id.etPayload);
        etProxy = findViewById(R.id.etProxy);
        etDns = findViewById(R.id.etDns);
        cbSsl = findViewById(R.id.cbSsl);
        cbAutoReconnect = findViewById(R.id.cbAutoReconnect);
        cbBypassDpi = findViewById(R.id.cbBypassDpi);
    }

    private void loadPrefs() {
        etQuickConnect.setText(prefs.getString("quick", ""));
        etSni.setText(prefs.getString("sni", ""));
        etPayload.setText(prefs.getString("payload", ""));
        etProxy.setText(prefs.getString("proxy", ""));
        etDns.setText(prefs.getString("dns", "1.1.1.1"));
        cbSsl.setChecked(prefs.getBoolean("ssl", false));
        cbAutoReconnect.setChecked(prefs.getBoolean("auto", true));
        cbBypassDpi.setChecked(prefs.getBoolean("split", false));
    }

    private void savePrefs() {
        prefs.edit()
                .putString("quick", etQuickConnect.getText().toString().trim())
                .putString("sni", etSni.getText().toString().trim())
                .putString("payload", etPayload.getText().toString())
                .putString("proxy", etProxy.getText().toString().trim())
                .putString("dns", etDns.getText().toString().trim())
                .putBoolean("ssl", cbSsl.isChecked())
                .putBoolean("auto", cbAutoReconnect.isChecked())
                .putBoolean("split", cbBypassDpi.isChecked())
                .apply();
    }

    private SshManager.Config parseQuick(String input) {
        try {
            String s = input.trim();
            int at = s.indexOf('@');
            if (at <= 0) return null;
            String hostPort = s.substring(0, at);
            String userPass = s.substring(at + 1);
            int c1 = userPass.indexOf(':');
            if (c1 <= 0) return null;

            SshManager.Config c = new SshManager.Config();
            int c0 = hostPort.lastIndexOf(':');
            if (c0 > 0) {
                c.host = hostPort.substring(0, c0).trim();
                c.port = Integer.parseInt(hostPort.substring(c0 + 1).trim());
            } else {
                c.host = hostPort.trim();
                c.port = 22;
            }
            c.user = userPass.substring(0, c1);
            c.pass = userPass.substring(c1 + 1);
            if (c.host.isEmpty() || c.user.isEmpty() || c.pass.isEmpty() || c.port < 1 || c.port > 65535) return null;
            return c;
        } catch (Exception e) {
            return null;
        }
    }

    private void setupTabs() {
        tabSsh.setOnClickListener(v -> showTab(0));
        tabOvpn.setOnClickListener(v -> showTab(1));
        tabConfig.setOnClickListener(v -> showTab(2));
        showTab(0);
    }

    private void showTab(int index) {
        formSsh.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        formOvpn.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        formConfig.setVisibility(index == 2 ? View.VISIBLE : View.GONE);

        int active = 0xFF1A73E8, inactive = 0xFFB0B0B0, activeBg = 0xFF2A2A2A, noBg = 0x00000000;
        tabSsh.setTextColor(index == 0 ? active : inactive);
        tabOvpn.setTextColor(index == 1 ? active : inactive);
        tabConfig.setTextColor(index == 2 ? active : inactive);
        tabSsh.setBackgroundColor(index == 0 ? activeBg : noBg);
        tabOvpn.setBackgroundColor(index == 1 ? activeBg : noBg);
        tabConfig.setBackgroundColor(index == 2 ? activeBg : noBg);
    }

    private SshManager.Config buildConfig() {
        SshManager.Config c = parseQuick(etQuickConnect.getText().toString());
        if (c == null) {
            Toast.makeText(this, "Format salah! Gunakan: host:port@user:pass", Toast.LENGTH_LONG).show();
            return null;
        }
        c.sni = etSni.getText().toString().trim();
        c.payload = etPayload.getText().toString();
        c.ssl = cbSsl.isChecked();
        c.autoReconnect = cbAutoReconnect.isChecked();
        c.split = cbBypassDpi.isChecked();

        String px = etProxy.getText().toString().trim();
        if (!px.isEmpty()) {
            int i = px.lastIndexOf(':');
            try {
                if (i <= 0) throw new NumberFormatException();
                c.proxyHost = px.substring(0, i).trim();
                c.proxyPort = Integer.parseInt(px.substring(i + 1).trim());
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Remote proxy harus host:port", Toast.LENGTH_LONG).show();
                return null;
            }
        }
        return c;
    }

    private void startConnectionProcess() {
        SshManager.Config c = buildConfig();
        if (c == null) return;
        savePrefs();
        SshManager.pending = c;
        tvLog.setText("");

        Intent intent = VpnService.prepare(this);
        if (intent != null) {
            appendLog("Meminta izin VPN...");
            vpnPermissionLauncher.launch(intent);
        } else {
            startTunnel();
        }
    }

    private void startTunnel() {
        render(SshManager.State.CONNECTING);
        Intent i = new Intent(this, OvpnService.class);
        i.setAction(OvpnService.ACTION_START);
        startService(i);
    }

    private void appendLog(String msg) {
        ui.post(() -> {
            tvLog.append("\n> " + msg);
            Layout l = tvLog.getLayout();
            if (l != null) {
                int y = l.getLineTop(tvLog.getLineCount()) - tvLog.getHeight();
                tvLog.scrollTo(0, Math.max(y, 0));
            }
        });
    }

    private void render(SshManager.State s) {
        switch (s) {
            case CONNECTED:
                tvStatus.setText("● CONNECTED");
                tvStatus.setTextColor(0xFF00C853);
                btnConnect.setText("DISCONNECT");
                btnConnect.setBackgroundTintList(getColorStateList(android.R.color.holo_red_dark));
                break;
            case CONNECTING:
                tvStatus.setText("● CONNECTING");
                tvStatus.setTextColor(0xFFFFC107);
                btnConnect.setText("DISCONNECT");
                btnConnect.setBackgroundTintList(getColorStateList(android.R.color.holo_red_dark));
                break;
            default:
                tvStatus.setText("● DISCONNECTED");
                tvStatus.setTextColor(0xFFD32F2F);
                btnConnect.setText("CONNECT");
                btnConnect.setBackgroundTintList(getColorStateList(android.R.color.holo_green_dark));
        }
    }

    @Override public void onLog(String msg) { appendLog(msg); }

    @Override public void onState(SshManager.State s) { ui.post(() -> render(s)); }

    @Override
    public void onError(String msg) {
        ui.post(() -> Toast.makeText(this, "Gagal: " + msg, Toast.LENGTH_LONG).show());
    }
}
