package com.safepath.ai.utils;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import com.safepath.ai.SafePathApplication;
import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * TokenManager — stores the JWT access token in EncryptedSharedPreferences.
 * Never holds tokens in plain SharedPreferences or memory beyond necessity.
 */
public class TokenManager {

    private static final String PREFS_FILE = "safepath_secure_prefs";
    private static final String KEY_TOKEN = "auth_token";
    private static volatile TokenManager instance;
    private SharedPreferences prefs;

    private TokenManager() {
        try {
            Context ctx = SafePathApplication.getInstance();
            MasterKey masterKey = new MasterKey.Builder(ctx)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            prefs = EncryptedSharedPreferences.create(
                    ctx,
                    PREFS_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException("Failed to initialise EncryptedSharedPreferences", e);
        }
    }

    public static TokenManager getInstance() {
        if (instance == null) {
            synchronized (TokenManager.class) {
                if (instance == null) instance = new TokenManager();
            }
        }
        return instance;
    }

    public void saveToken(String token) {
        prefs.edit().putString(KEY_TOKEN, token).apply();
    }

    public String getToken() {
        return prefs.getString(KEY_TOKEN, null);
    }

    public void clearToken() {
        prefs.edit().remove(KEY_TOKEN).apply();
    }
}

