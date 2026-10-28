package com.sshovpn.app;

import android.content.Context;
import android.util.Base64;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public class ConfigManager {
    private static final String FILE_NAME = "xaillassh_secure.cfg";
    private static final String SECRET_KEY = "XaillaXshSecretKey2026"; // Kunci Enkripsi

    public static void saveConfig(Context context, String data) {
        try {
            // Enkripsi sederhana (XOR + Base64) agar tidak bisa dibaca mata manusia
            byte[] key = SECRET_KEY.getBytes(StandardCharsets.UTF_8);
            byte[] input = data.getBytes(StandardCharsets.UTF_8);
            byte[] output = new byte[input.length];
            for (int i = 0; i < input.length; i++) {
                output[i] = (byte) (input[i] ^ key[i % key.length]);
            }
            String encrypted = Base64.encodeToString(output, Base64.DEFAULT);
            
            File file = new File(context.getFilesDir(), FILE_NAME);
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(encrypted.getBytes());
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String loadConfig(Context context) {
        try {
            File file = new File(context.getFilesDir(), FILE_NAME);
            if (!file.exists()) return null;
            
            FileInputStream fis = new FileInputStream(file);
            byte[] buffer = new byte[(int) file.length()];
            fis.read(buffer);
            fis.close();
            
            String encrypted = new String(buffer, StandardCharsets.UTF_8);
            byte[] input = Base64.decode(encrypted, Base64.DEFAULT);
            byte[] key = SECRET_KEY.getBytes(StandardCharsets.UTF_8);
            byte[] output = new byte[input.length];
            
            for (int i = 0; i < input.length; i++) {
                output[i] = (byte) (input[i] ^ key[i % key.length]);
            }
            return new String(output, StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
