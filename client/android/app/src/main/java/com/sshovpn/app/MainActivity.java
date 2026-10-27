package com.sshovpn.app;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity implements SshManager.SshCallback {

    private LinearLayout formSsh, formOvpn, formConfig;
    private TextView tabSsh, tabOvpn, tabConfig, tvStatus, tvLog;
    private Button btnConnect;
    private EditText etHost, etPort, etUser, etPass, etSni, etPayload;
    
    private SshManager sshManager;
    private boolean isConnecting = false;
    private Handler uiHandler = new Handler(Looper.getMainLooper());

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
                startConnection();
            }
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

        etHost = findViewById(R.id.etSshHost);
        etPort = findViewById(R.id.etSshPort);
        etUser = findViewById(R.id.etSshUser);
        etPass = findViewById(R.id.etSshPass);
        etSni = findViewById(R.id.etSni);
        etPayload = findViewById(R.id.etPayload);
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

    private void startConnection() {
        String host = etHost.getText().toString().trim();
        String portStr = etPort.getText().toString().trim();
        String user = etUser.getText().toString().trim();
        String pass = etPass.getText().toString().trim();

        if (host.isEmpty() || portStr.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, "Isi semua data SSH!", Toast.LENGTH_SHORT).show();
            return;
        }

        int port = 22;
        try { port = Integer.parseInt(portStr); } catch (Exception ignored) {}

        isConnecting = true;
        btnConnect.setText("DISCONNECT");
        btnConnect.setBackgroundTintList(getColorStateList(android.R.color.holo_red_dark));
        tvStatus.setText("● CONNECTING");
        tvStatus.setTextColor(0xFFFFC107);
        tvLog.setText("");

        sshManager.connect(host, port, user, pass, 1080, this);
    }

    private void appendLog(String msg) {
        uiHandler.post(() -> {
            tvLog.append("\n> " + msg);
            int scrollAmount = tvLog.getLayout().getLineTop(tvLog.getLineCount()) - tvLog.getHeight();
            if (scrollAmount > 0) tvLog.scrollTo(0, scrollAmount);
        });
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
        uiHandler.post(() -> {
            isConnecting = false;
            tvStatus.setText("● DISCONNECTED");
            tvStatus.setTextColor(0xFFD32F2F);
            btnConnect.setText("CONNECT");
            btnConnect.setBackgroundTintList(getColorStateList(android.R.color.holo_green_dark));
        });
    }

    @Override
    public void onError(String msg) {
        appendLog("ERROR: " + msg);
        Toast.makeText(this, "Gagal: " + msg, Toast.LENGTH_LONG).show();
    }
}
