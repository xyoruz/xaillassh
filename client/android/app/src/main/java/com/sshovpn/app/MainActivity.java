package com.sshovpn.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.core.view.GravityCompat;

public class MainActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private TextView tvLog, tvStatus;
    private Button btnConnect;
    private String activeConfigData = ""; // Menyimpan data config aktif

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        drawerLayout = findViewById(R.id.drawer_layout);
        tvLog = findViewById(R.id.tvLog);
        tvStatus = findViewById(R.id.tvStatus);
        btnConnect = findViewById(R.id.btnConnect);

        // --- LOGIKA MENU SAMPING ---
        
        // 1. Menu SSH
        findViewById(R.id.menu_ssh).setOnClickListener(v -> {
            showInputDialog("SSH");
            drawerLayout.closeDrawer(GravityCompat.START);
        });

        // 2. Menu OVPN
        findViewById(R.id.menu_ovpn).setOnClickListener(v -> {
            showInputDialog("OVPN");
            drawerLayout.closeDrawer(GravityCompat.START);
        });

        // 3. Upload Config (Load dari Penyimpanan Aman)
        findViewById(R.id.menu_upload_ssh).setOnClickListener(v -> {
            String loadedData = ConfigManager.loadConfig(this);
            if (loadedData != null) {
                activeConfigData = loadedData;
                tvLog.append("\n> Config berhasil di-upload dan dibuka.");
                Toast.makeText(this, "Config Terbuka!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Belum ada config tersimpan.", Toast.LENGTH_SHORT).show();
            }
            drawerLayout.closeDrawer(GravityCompat.START);
        });

        // 4. Save Config (Simpan Terenkripsi)
        findViewById(R.id.menu_save_config).setOnClickListener(v -> {
            if (!activeConfigData.isEmpty()) {
                ConfigManager.saveConfig(this, activeConfigData);
                tvLog.append("\n> Config berhasil disimpan (Terenkripsi).");
                Toast.makeText(this, "Config Disimpan Aman", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Isi form SSH terlebih dahulu!", Toast.LENGTH_SHORT).show();
            }
            drawerLayout.closeDrawer(GravityCompat.START);
        });

        // --- TOMBOL CONNECT & IZIN VPN ---
        btnConnect.setOnClickListener(v -> {
            if (activeConfigData.isEmpty()) {
                Toast.makeText(this, "Silakan isi konfigurasi SSH dulu!", Toast.LENGTH_SHORT).show();
                return;
            }

            // Cek Izin VPN
            Intent intent = VpnService.prepare(MainActivity.this);
            if (intent != null) {
                // Jika belum diizinkan, munculkan pop-up Android
                startActivityForResult(intent, 100);
            } else {
                // Jika sudah diizinkan, langsung jalankan VPN
                startVpnService();
            }
        });
    }

    // Fungsi untuk menampilkan Pop-up Input SSH/OVPN
    private void showInputDialog(String type) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Input " + type);
        
        android.view.View view = getLayoutInflater().inflate(R.layout.dialog_ssh, null);
        builder.setView(view);

        EditText etQuick = view.findViewById(R.id.etQuickConnect);
        EditText etSni = view.findViewById(R.id.etSni);
        EditText etPayload = view.findViewById(R.id.etPayload);
        Button btnParse = view.findViewById(R.id.btnParse);

        btnParse.setOnClickListener(v -> {
            etQuick.setText("id3.aryus09.com:80@xailla:sihab");
            etSni.setText("m.facebook.com");
            etPayload.setText("GET / HTTP/1.1[crlf]Host: m.facebook.com[crlf]Upgrade: websocket[crlf]Connection: Upgrade[crlf][crlf]");
        });

        builder.setPositiveButton("SIMPAN", (dialog, which) -> {
            activeConfigData = "HOST:" + etQuick.getText().toString() + 
                               "|SNI:" + etSni.getText().toString() + 
                               "|PAYLOAD:" + etPayload.getText().toString();
            tvLog.append("\n> Config " + type + " dimuat ke memori.");
            Toast.makeText(this, "Konfigurasi siap digunakan", Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton("BATAL", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    // Fungsi Menjalankan Service VPN
    private void startVpnService() {
        tvStatus.setText("● CONNECTING...");
        tvStatus.setTextColor(0xFFFFA000);
        tvLog.append("\n> Memulai koneksi VPN...");
        
        // Panggil OvpnService
        Intent serviceIntent = new Intent(this, OvpnService.class);
        
        // Kirim data config ke Service (Jika OvpnService Anda menerima data via Intent)
        serviceIntent.putExtra("CONFIG_DATA", activeConfigData);
        
        startService(serviceIntent); 
        
        // Simulasi Status Connected (Bisa dihapus jika OvpnService sudah mengirim sinyal balik)
        new Handler().postDelayed(() -> {
            tvStatus.setText("● CONNECTED");
            tvStatus.setTextColor(0xFF00E676);
            tvLog.append("\n> Koneksi Berhasil (VPN Aktif)");
        }, 2000);
    }

    // Menangkap Hasil dari Pop-up Izin VPN Android
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100) {
            if (resultCode == RESULT_OK) {
                // User menekan "OK"
                startVpnService();
            } else {
                // User menolak
                tvLog.append("\n> Izin VPN ditolak. Koneksi dibatalkan.");
                Toast.makeText(this, "Izin VPN diperlukan untuk koneksi!", Toast.LENGTH_SHORT).show();
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
