package com.sshovpn.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.core.view.GravityCompat;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private TextView tvLog, tvStatus, tvActiveAccount;
    private Button btnConnect;
    
    // Variabel penampung akun yang sedang dipilih
    private String selectedAccountName = "";
    private String selectedHost = "";
    private String selectedSni = "";
    private String selectedPayload = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        drawerLayout = findViewById(R.id.drawer_layout);
        tvLog = findViewById(R.id.tvLog);
        tvStatus = findViewById(R.id.tvStatus);
        tvActiveAccount = findViewById(R.id.tvActiveAccount);
        btnConnect = findViewById(R.id.btnConnect);

        // --- LOGIKA MENU SAMPING ---
        
        // 1. Tambah Akun
        findViewById(R.id.menu_tambah_akun).setOnClickListener(v -> {
            showAddAccountDialog();
            drawerLayout.closeDrawer(GravityCompat.START);
        });

        // 2. Manajemen Akun (Pilih Akun)
        findViewById(R.id.menu_akun).setOnClickListener(v -> {
            showAccountListDialog();
            drawerLayout.closeDrawer(GravityCompat.START);
        });

        // --- TOMBOL CONNECT & IZIN VPN ---
        btnConnect.setOnClickListener(v -> {
            if (selectedAccountName.isEmpty()) {
                Toast.makeText(this, "Pilih akun SSH terlebih dahulu di menu Akun!", Toast.LENGTH_SHORT).show();
                return;
            }

            // Cek Izin VPN
            Intent intent = VpnService.prepare(MainActivity.this);
            if (intent != null) {
                startActivityForResult(intent, 100);
            } else {
                startVpnService();
            }
        });
    }

    // --- FUNGSI UNTUK MENAMBAH AKUN BARU ---
    private void showAddAccountDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Tambah Akun SSH Baru");
        
        // Kita buat layout input secara manual lewat kode agar cepat
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
        etPayload.setText("GET / HTTP/1.1[crlf]Host: m.facebook.com[crlf][crlf]");
        layout.addView(etPayload);

        builder.setView(layout);

        builder.setPositiveButton("SIMPAN AKUN", (dialog, which) -> {
            String name = etName.getText().toString().trim();
            String host = etHost.getText().toString().trim();
            String sni = etSni.getText().toString().trim();
            String payload = etPayload.getText().toString().trim();

            if (name.isEmpty() || host.isEmpty()) {
                Toast.makeText(this, "Nama dan Host tidak boleh kosong!", Toast.LENGTH_SHORT).show();
                return;
            }

            // Simpan ke AccountManager
            AccountManager.saveAccount(this, name, host, sni, payload);
            Toast.makeText(this, "Akun " + name + " berhasil disimpan!", Toast.LENGTH_SHORT).show();
            tvLog.append("\n> Akun " + name + " ditambahkan.");
        });

        builder.setNegativeButton("BATAL", null);
        builder.show();
    }

    // --- FUNGSI UNTUK MENAMPILKAN DAFTAR AKUN ---
    private void showAccountListDialog() {
        JSONArray accounts = AccountManager.getAccounts(this);
        if (accounts.length() == 0) {
            Toast.makeText(this, "Belum ada akun tersimpan.", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] accountNames = new String[accounts.length()];
        try {
            for (int i = 0; i < accounts.length(); i++) {
                accountNames[i] = accounts.getJSONObject(i).getString("name");
            }
        } catch (Exception e) { return; }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Pilih Akun SSH");
        builder.setItems(accountNames, (dialog, which) -> {
            try {
                String chosenName = accountNames[which];
                JSONObject acc = AccountManager.getAccount(this, chosenName);
                if (acc != null) {
                    // Set variabel global
                    selectedAccountName = acc.getString("name");
                    selectedHost = acc.getString("host");
                    selectedSni = acc.getString("sni");
                    selectedPayload = acc.getString("payload");

                    // Update Tampilan Home
                    tvActiveAccount.setText("Akun Aktif: " + selectedAccountName);
                    tvLog.append("\n> Akun " + selectedAccountName + " dipilih.");
                    Toast.makeText(this, "Akun " + selectedAccountName + " siap!", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        builder.show();
    }

    // --- FUNGSI JALANKAN VPN ---
    private void startVpnService() {
        tvStatus.setText("● CONNECTING...");
        tvStatus.setTextColor(0xFFFFA000);
        tvLog.append("\n> Memulai SSH: " + selectedAccountName);
        
        Intent serviceIntent = new Intent(this, OvpnService.class);
        // Kirim data akun yang dipilih ke OvpnService
        serviceIntent.putExtra("HOST_DATA", selectedHost);
        serviceIntent.putExtra("SNI_DATA", selectedSni);
        serviceIntent.putExtra("PAYLOAD_DATA", selectedPayload);
        
        startService(serviceIntent); 
        
        new Handler().postDelayed(() -> {
            tvStatus.setText("● CONNECTED");
            tvStatus.setTextColor(0xFF00E676);
            tvLog.append("\n> Koneksi SSH Aktif!");
        }, 2000);
    }

    // --- IZIN VPN ---
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100) {
            if (resultCode == RESULT_OK) {
                startVpnService();
            } else {
                tvLog.append("\n> Izin VPN ditolak.");
            }
        }
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
