package com.sshovpn.app;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private TextView tvLog, tvStatus, tvActiveAccount;
    private Button btnConnect;
    
    private String selectedAccountName = "";
    private String selectedHost = "";
    private String selectedSni = "";
    private String selectedPayload = "";

    // Menerima pesan dari OvpnService / SshManager
    private final BroadcastReceiver vpnStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String status = intent.getStringExtra("STATUS");
            String message = intent.getStringExtra("MESSAGE");

            if (status != null) {
                if (status.equals("CONNECTED")) {
                    tvStatus.setText("● CONNECTED");
                    tvStatus.setTextColor(Color.parseColor("#FF00E676"));
                } else if (status.equals("DISCONNECTED")) {
                    tvStatus.setText("● DISCONNECTED");
                    tvStatus.setTextColor(Color.parseColor("#FFD32F2F"));
                } else if (status.equals("ERROR")) {
                    tvStatus.setText("● ERROR");
                    tvStatus.setTextColor(Color.parseColor("#FFD32F2F"));
                }
                tvLog.append("\n> " + message);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        drawerLayout = findViewById(R.id.drawer_layout);
        tvLog = findViewById(R.id.tvLog);
        tvStatus = findViewById(R.id.tvStatus);
        tvActiveAccount = findViewById(R.id.tvActiveAccount);
        btnConnect = findViewById(R.id.btnConnect);

        findViewById(R.id.menu_akun_utama).setOnClickListener(v -> {
            showAccountListDialog();
            drawerLayout.closeDrawer(GravityCompat.START);
        });

        btnConnect.setOnClickListener(v -> {
            if (selectedAccountName.isEmpty()) {
                Toast.makeText(this, "Pilih akun terlebih dahulu di menu Akun!", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Cek izin VPN
            Intent intent = VpnService.prepare(MainActivity.this);
            if (intent != null) {
                startActivityForResult(intent, 100);
            } else {
                startVpnService();
            }
        });

        // Daftarkan penerima sinyal status (untuk Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(vpnStatusReceiver, new IntentFilter("VPN_STATUS_UPDATE"), Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(vpnStatusReceiver, new IntentFilter("VPN_STATUS_UPDATE"));
        }
    }

    private void startVpnService() {
        tvStatus.setText("● CONNECTING...");
        tvStatus.setTextColor(Color.parseColor("#FFFFA000"));
        tvLog.append("\n> Meminta OvpnService menyambung ke server...");
        
        Intent serviceIntent = new Intent(this, OvpnService.class);
        // Kirim data akun yang dipilih ke Service
        serviceIntent.putExtra("HOST_DATA", selectedHost);
        serviceIntent.putExtra("SNI_DATA", selectedSni);
        serviceIntent.putExtra("PAYLOAD_DATA", selectedPayload);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    // --- SEMUA FUNGSI UI DIALOG TETAP SAMA SEPERTI SEBELUMNYA ---
    
    private void showAccountListDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Daftar Akun");

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(30, 30, 30, 30);

        LinearLayout headerLayout = new LinearLayout(this);
        headerLayout.setOrientation(LinearLayout.HORIZONTAL);
        headerLayout.setGravity(Gravity.END);
        
        TextView btnGlobalMenu = new TextView(this);
        btnGlobalMenu.setText("⋮");
        btnGlobalMenu.setTextSize(28);
        btnGlobalMenu.setTextColor(Color.WHITE);
        btnGlobalMenu.setPadding(20, 0, 20, 10);
        btnGlobalMenu.setOnClickListener(v -> showGlobalMenu());
        headerLayout.addView(btnGlobalMenu);
        mainLayout.addView(headerLayout);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout listLayout = new LinearLayout(this);
        listLayout.setOrientation(LinearLayout.VERTICAL);

        JSONArray accounts = AccountManager.getAccounts(this);
        if (accounts.length() == 0) {
            TextView emptyText = new TextView(this);
            emptyText.setText("Belum ada akun. Klik titik tiga di kanan atas untuk menambah.");
            emptyText.setTextColor(Color.LTGRAY);
            listLayout.addView(emptyText);
        } else {
            try {
                for (int i = 0; i < accounts.length(); i++) {
                    JSONObject acc = accounts.getJSONObject(i);
                    String accName = acc.getString("name");

                    LinearLayout row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(0, 15, 0, 15);

                    TextView tvName = new TextView(this);
                    tvName.setText(accName);
                    tvName.setTextColor(Color.WHITE);
                    tvName.setTextSize(16);
                    tvName.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                    tvName.setOnClickListener(v -> loadAccountToHome(accName));

                    TextView btnRowMenu = new TextView(this);
                    btnRowMenu.setText("⋮");
                    btnRowMenu.setTextSize(24);
                    btnRowMenu.setTextColor(Color.WHITE);
                    btnRowMenu.setPadding(20, 0, 0, 0);
                    btnRowMenu.setOnClickListener(v -> showPerAccountMenu(accName));

                    row.addView(tvName);
                    row.addView(btnRowMenu);
                    
                    View divider = new View(this);
                    divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
                    divider.setBackgroundColor(Color.DKGRAY);

                    listLayout.addView(row);
                    listLayout.addView(divider);
                }
            } catch (Exception e) { e.printStackTrace(); }
        }
        scrollView.addView(listLayout);
        mainLayout.addView(scrollView);
        builder.setView(mainLayout);
        builder.setNegativeButton("Tutup", (dialog, which) -> dialog.dismiss());
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void showGlobalMenu() {
        String[] options = {"Tambah Akun Baru", "Upload Config"};
        new AlertDialog.Builder(this).setTitle("Menu Global").setItems(options, (dialog, which) -> {
            if (which == 0) showEditAccountDialog(null); 
            else loadConfigAction(); 
        }).show();
    }

    private void showPerAccountMenu(String accName) {
        String[] options = {"Edit Akun Ini", "Save Config"};
        new AlertDialog.Builder(this).setTitle(accName).setItems(options, (dialog, which) -> {
            if (which == 0) showEditAccountDialog(accName); 
            else {
                loadAccountToHome(accName);
                saveConfigAction(); 
            }
        }).show();
    }

    private void showEditAccountDialog(String editName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(editName == null ? "Tambah Akun SSH" : "Edit Akun: " + editName);
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 40);

        final EditText etName = new EditText(this);
        etName.setHint("Nama Akun (Cth: Server SG)");
        layout.addView(etName);

        final EditText etHost = new EditText(this);
        etHost.setHint("host:port@user:pass");
        layout.addView(etHost);

        final EditText etSni = new EditText(this);
        etSni.setHint("SNI (Cth: m.facebook.com)");
        layout.addView(etSni);

        final EditText etPayload = new EditText(this);
        etPayload.setHint("HTTP Payload");
        etPayload.setText("GET / HTTP/1.1[crlf]Host: bug.com[crlf][crlf]");
        layout.addView(etPayload);

        if (editName != null) {
            JSONObject acc = AccountManager.getAccount(this, editName);
            if (acc != null) {
                try {
                    etName.setText(acc.getString("name"));
                    etHost.setText(acc.getString("host"));
                    etSni.setText(acc.getString("sni"));
                    etPayload.setText(acc.getString("payload"));
                } catch (Exception e) {}
            }
        }

        builder.setView(layout);
        builder.setPositiveButton("SIMPAN", (dialog, which) -> {
            String name = etName.getText().toString().trim();
            String host = etHost.getText().toString().trim();
            String sni = etSni.getText().toString().trim();
            String payload = etPayload.getText().toString().trim();

            if (name.isEmpty() || host.isEmpty()) {
                Toast.makeText(this, "Nama dan Host tidak boleh kosong!", Toast.LENGTH_SHORT).show();
                return;
            }
            AccountManager.saveAccount(this, name, host, sni, payload);
            Toast.makeText(this, "Akun " + name + " disimpan!", Toast.LENGTH_SHORT).show();
            tvLog.append("\n> Akun " + name + " disimpan.");
            showAccountListDialog(); 
        });
        builder.setNegativeButton("BATAL", null);
        builder.show();
    }

    private void saveConfigAction() {
        if (selectedAccountName.isEmpty()) {
            Toast.makeText(this, "Pilih akun dulu sebelum save config!", Toast.LENGTH_SHORT).show();
            return;
        }
        String data = "NAME:" + selectedAccountName + "|HOST:" + selectedHost + "|SNI:" + selectedSni + "|PAYLOAD:" + selectedPayload;
        ConfigManager.saveConfig(this, data);
        tvLog.append("\n> Config " + selectedAccountName + " disimpan (Aman).");
        Toast.makeText(this, "Config berhasil disimpan", Toast.LENGTH_SHORT).show();
    }

    private void loadConfigAction() {
        String loaded = ConfigManager.loadConfig(this);
        if (loaded != null) {
            try {
                String name = loaded.split("\\|")[0].replace("NAME:", "");
                String host = loaded.split("\\|")[1].replace("HOST:", "");
                String sni = loaded.split("\\|")[2].replace("SNI:", "");
                String payload = loaded.split("\\|")[3].replace("PAYLOAD:", "");
                AccountManager.saveAccount(this, name + " (Loaded)", host, sni, payload);
                Toast.makeText(this, "Config berhasil di-upload!", Toast.LENGTH_SHORT).show();
                showAccountListDialog(); 
            } catch (Exception e) {
                Toast.makeText(this, "Format config tidak valid.", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Tidak ada config tersimpan.", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadAccountToHome(String accountName) {
        try {
            JSONObject acc = AccountManager.getAccount(this, accountName);
            if (acc != null) {
                selectedAccountName = acc.getString("name");
                selectedHost = acc.getString("host");
                selectedSni = acc.getString("sni");
                selectedPayload = acc.getString("payload");

                tvActiveAccount.setText("Akun Aktif: " + selectedAccountName);
                tvLog.append("\n> Akun " + selectedAccountName + " dipilih.");
                Toast.makeText(this, "Akun " + selectedAccountName + " siap!", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100) {
            if (resultCode == RESULT_OK) startVpnService();
            else tvLog.append("\n> Izin VPN ditolak.");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(vpnStatusReceiver);
        } catch (Exception e) {}
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
}
