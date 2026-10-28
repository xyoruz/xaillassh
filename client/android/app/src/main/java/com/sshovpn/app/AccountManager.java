package com.sshovpn.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

public class AccountManager {
    private static final String PREF_NAME = "XaillaAccounts";
    private static final String KEY_ACCOUNTS = "accounts_list";

    // Simpan akun baru (Menimpa jika nama sama)
    public static void saveAccount(Context context, String name, String host, String sni, String payload) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String existing = prefs.getString(KEY_ACCOUNTS, "[]");
            JSONArray array = new JSONArray(existing);

            JSONObject newAccount = new JSONObject();
            newAccount.put("name", name);
            newAccount.put("host", host);
            newAccount.put("sni", sni);
            newAccount.put("payload", payload);

            // Cek jika nama akun sudah ada, hapus yang lama
            for (int i = 0; i < array.length(); i++) {
                if (array.getJSONObject(i).getString("name").equals(name)) {
                    array.remove(i);
                    break;
                }
            }

            array.put(newAccount);
            prefs.edit().putString(KEY_ACCOUNTS, array.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Ambil semua daftar akun (untuk ditampilkan di menu)
    public static JSONArray getAccounts(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String existing = prefs.getString(KEY_ACCOUNTS, "[]");
        try {
            return new JSONArray(existing);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    // Ambil data 1 akun spesifik
    public static JSONObject getAccount(Context context, String name) {
        try {
            JSONArray array = getAccounts(context);
            for (int i = 0; i < array.length(); i++) {
                if (array.getJSONObject(i).getString("name").equals(name)) {
                    return array.getJSONObject(i);
                }
            }
        } catch (Exception e) {}
        return null;
    }
}
