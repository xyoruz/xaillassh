package com.sshovpn.app;

import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity implements SshManager.SshCallback {

    private LinearLayout formSsh, formOvpn, formConfig;
    private TextView tabSsh, tabOvpn, tabConfig, tvStatus, tvLog;
    private Button btnConnect, btnParse;
    private EditText etQuickConnect, etSni, etPayload;
    
    private SshManager sshManager;
    private boolean isConnecting = false;
    private Handler uiHandler = new Handler(Looper.getMainLooper());

    // Variabel background untuk menyimpan hasil parse
    private String sshHost = "";
    private int sshPort = 22;
    private String sshUser = "";
    private String sshPass = "";

    private final ActivityResultLauncher<Intent> vpnPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    appendLog("Izin VPN diberikan. Memulai koneksi...");
                    proceedConnection();
                } else {
                    appendLog("Izin VPN DITOLAK. Koneksi dibatalkan.");
                    Toast.makeText(this, "Aplikasi butuh izin VPN untuk terhubung!", Toast.LENGTH_LONG).show();
                    resetUiOnDisconnect();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sshManager = new SshManager();
        initViews();
        setupTabs();
        
        btnConnect.setOnClickListener(v -> {
            if (isConnecting || sshManager.isConnected()) {
                appendLog("Disconnecting...");
                sshManager.disconnect(this);
            } else {
                startConnectionProcess();
            }
        });

        btnParse.setOnClickListener(v -> {
            String quickText = etQuickConnect.getText().toString().trim();
            if (quickText.isEmpty()) {
                Toast.makeText(this, "Isi format quick connect dulu!", Toast.LENGTH_SHORT).show();
                return;
            }
            parseQuickConnect(quickText);
        });
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
        btnConnect = findViewById(R.id.btnConnect);
        btnParse = findViewById(R.id.btnParse);

        etQuickConnect = findViewById(R.id.etQuickConnect);
        etSni = findViewById(R.id.etSni);
        etPayload = findViewById(R.id.etPayload);
    }

    private void parseQuickConnect(String input) {
        try {
            // Format: id3.aryus09.com:80@xailla:sihab
            String[] parts = input.split("@");
            String hostPort = parts[0];
            String userPass = parts[1];

            String[] hp = hostPort.split(":");
            sshHost = hp[0];
            sshPort = Integer.parseInt(hp[1]);

            String[] up = userPass.split(":");
            sshUser = up[0];
            sshPass = up[1];
            
            Toast.makeText(this, "Format berhasil diurai!", Toast.LENGTH_SHORT).show();
            appendLog("Parsed: " + sshHost + " | Port: " + sshPort + " | User: " + sshUser);

        } catch (Exception e) {
            Toast.makeText(this, "Format salah! Gunakan: host:port@user:pass", Toast.LENGTH_LONG).show();
            sshHost = ""; sshUser = ""; sshPass = "";
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

        int activeColor = 0xFF1A73E8;
        int inactiveColor = 0xFFB0B0B0;
        int activeBg = 0xFF2A2A2A;
        int noBg = 0x00000000;

        tabSsh.setTextColor(index == 0 ? activeColor : inactiveColor);
        tabOvpn.setTextColor(index == 1 ? activeColor : inactiveColor);
        tabConfig.setTextColor(index == 2 ? activeColor : inactiveColor);

        tabSsh.setBackgroundColor(index == 0 ? activeBg : noBg);
        tabOvpn.setBackgroundColor(index == 1 ? activeBg : noBg);
        tabConfig.setBackgroundColor(index == 2 ? activeBg : noBg);
    }

    private void startConnectionProcess() {
        // Jika user belum klik "ISI OTOMATIS", coba parse langsung dari kolom input
        if (sshHost.isEmpty()) {
            parseQuickConnect(etQuickConnect.getText().toString().trim());
        }

        if (sshHost.isEmpty() || sshUser.isEmpty() || sshPass.isEmpty()) {
            Toast.makeText(this, "Data SSH belum lengkap! Cek format Quick Connect.", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = VpnService.prepare(this);
        if (intent != null) {
            appendLog("Meminta izin VPN...");
            vpnPermissionLauncher.launch(intent);
        } else {
            appendLog("Izin VPN sudah ada.");
            proceedConnection();
        }
    }

    private void proceedConnection() {
        Intent vpnIntent = new Intent(this, OvpnService.class);
        startService(vpnIntent);

        isConnecting = true;
        btnConnect.setText("DISCONNECT");
        btnConnect.setBackgroundTintList(getColorStateList(android.R.color.holo_red_dark));
        tvStatus.setText("● CONNECTING");
        tvStatus.setTextColor(0xFFFFC107);
        tvLog.setText("");

        sshManager.connect(sshHost, sshPort, sshUser, sshPass, 1080, this);
    }

    private void appendLog(String msg) {
        uiHandler.post(() -> {
            tvLog.append("\n> " + msg);
            int scrollAmount = tvLog.getLayout().getLineTop(tvLog.getLineCount()) - tvLog.getHeight();
            if (scrollAmount > 0) tvLog.scrollTo(0, scrollAmount);
        });
    }

    private void resetUiOnDisconnect() {
        isConnecting = false;
        tvStatus.setText("● DISCONNECTED");
        tvStatus.setTextColor(0xFFD32F2F);
        btnConnect.setText("CONNECT");
        btnConnect.setBackgroundTintList(getColorStateList(android.R.color.holo_green_dark));
    }

    @Override
    public void onLog(String msg) { appendLog(msg); }

    @Override
    public void onConnected() {
        uiHandler.post(() -> {
            isConnecting = false;
            tvStatus.setText("● CONNECTED");
            tvStatus.setTextColor(0xFF00C853);
            btnConnect.setText("DISCONNECT");
        });
    }

    @Override
    public void onDisconnected() {
        uiHandler.post(this::resetUiOnDisconnect);
        stopService(new Intent(this, OvpnService.class));
    }

    @Override
    public void onError(String msg) {
        appendLog("ERROR: " + msg);
        Toast.makeText(this, "Gagal: " + msg, Toast.LENGTH_LONG).show();
    }
}
