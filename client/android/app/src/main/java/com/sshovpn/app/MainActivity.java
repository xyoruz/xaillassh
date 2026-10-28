package com.sshovpn.app;

import android.app.AlertDialog;
import android.os.Bundle;
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
        
        // 1. Menu SSH -> Buka Pop-up
        findViewById(R.id.menu_ssh).setOnClickListener(v -> {
            showInputDialog("SSH");
            drawerLayout.closeDrawer(GravityCompat.START);
        });

        // 2. Menu OVPN
        findViewById(R.id.menu_ovpn).setOnClickListener(v -> {
            showInputDialog("OVPN");
            drawerLayout.closeDrawer(GravityCompat.START);
        });

        // 3. Upload Config (Load)
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

        // Tombol Connect
        btnConnect.setOnClickListener(v -> {
            if (activeConfigData.isEmpty()) {
                Toast.makeText(this, "Silakan isi konfigurasi SSH dulu!", Toast.LENGTH_SHORT).show();
            } else {
                tvStatus.setText("● CONNECTING...");
                tvStatus.setTextColor(0xFFFFA000);
                tvLog.append("\n> Menghubungkan dengan config: " + activeConfigData);
                // TODO: Panggil fungsi SshManager.connect() di sini
            }
        });
    }

    // Fungsi untuk menampilkan Pop-up Input
    private void showInputDialog(String type) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Input " + type);
        
        // Gunakan layout dialog_ssh.xml yang sudah kita buat
        android.view.View view = getLayoutInflater().inflate(R.layout.dialog_ssh, null);
        builder.setView(view);

        EditText etQuick = view.findViewById(R.id.etQuickConnect);
        EditText etSni = view.findViewById(R.id.etSni);
        EditText etPayload = view.findViewById(R.id.etPayload);
        Button btnParse = view.findViewById(R.id.btnParse);

        // Tombol Isi Otomatis
        btnParse.setOnClickListener(v -> {
            etQuick.setText("id3.aryus09.com:80@xailla:sihab");
            etSni.setText("m.facebook.com");
            etPayload.setText("GET / HTTP/1.1[crlf]Host: m.facebook.com[crlf]");
        });

        builder.setPositiveButton("SIMPAN KE MEMORI", (dialog, which) -> {
            // Simpan data ke variabel sementara (activeConfigData)
            activeConfigData = "HOST:" + etQuick.getText().toString() + 
                               "|SNI:" + etSni.getText().toString() + 
                               "|PAYLOAD:" + etPayload.getText().toString();
            tvLog.append("\n> Config " + type + " dimuat ke memori.");
            Toast.makeText(this, "Config siap digunakan", Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton("BATAL", (dialog, which) -> dialog.dismiss());
        builder.show();
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
