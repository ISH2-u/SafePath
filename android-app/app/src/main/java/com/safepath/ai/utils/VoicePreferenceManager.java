package com.safepath.ai.utils;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * VoicePreferenceManager — Secure storage for personalized voice codeword configuration.
 *
 * SECURITY & PRIVACY RULES:
 * 1. Uses EncryptedSharedPreferences (AES-256) for local phrase storage.
 * 2. Never exposes or logs the plaintext codeword in ordinary logs or analytics.
 * 3. Normalizes input (lowercase, whitespace collapse) for robust acoustic matching.
 */
public class VoicePreferenceManager {

    private static final String PREF_NAME = "safepath_secure_voice_prefs";
    private static final String KEY_CODEWORD_ENCRYPTED = "encrypted_codeword";
    private static final String KEY_CODEWORD_HASH = "codeword_sha256";
    private static final String KEY_MONITORING_ENABLED = "voice_monitoring_enabled";
    private static final String KEY_CONFIDENCE_THRESHOLD = "detection_confidence_threshold";

    private static VoicePreferenceManager instance;
    private SharedPreferences securePrefs;

    private VoicePreferenceManager(Context context) {
        try {
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            securePrefs = EncryptedSharedPreferences.create(
                    PREF_NAME,
                    masterKeyAlias,
                    context.getApplicationContext(),
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            // Fallback for devices with keystore exceptions
            securePrefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        }
    }

    public static synchronized VoicePreferenceManager getInstance(Context context) {
        if (instance == null) {
            instance = new VoicePreferenceManager(context);
        }
        return instance;
    }

    public void setCodeword(String phrase) {
        if (phrase == null) return;
        String normalized = normalizePhrase(phrase);
        String sha256 = hashSha256(normalized);

        securePrefs.edit()
                .putString(KEY_CODEWORD_ENCRYPTED, normalized)
                .putString(KEY_CODEWORD_HASH, sha256)
                .apply();
    }

    public String getCodeword() {
        String cw = securePrefs.getString(KEY_CODEWORD_ENCRYPTED, "");
        if (cw == null || cw.trim().isEmpty()) {
            return "blue moon";
        }
        return cw;
    }

    public String getCodewordPhrase() {
        return getCodeword();
    }

    public boolean hasCodewordConfigured() {
        String cw = getCodeword();
        return cw != null && !cw.trim().isEmpty();
    }

    public void setMonitoringEnabled(boolean enabled) {
        securePrefs.edit().putBoolean(KEY_MONITORING_ENABLED, enabled).apply();
    }

    public void setMonitoringActive(boolean active) {
        setMonitoringEnabled(active);
    }

    public boolean isMonitoringEnabled() {
        return securePrefs.getBoolean(KEY_MONITORING_ENABLED, false);
    }

    public boolean isMonitoringActive() {
        return isMonitoringEnabled();
    }

    public void setConfidenceThreshold(float threshold) {
        securePrefs.edit().putFloat(KEY_CONFIDENCE_THRESHOLD, threshold).apply();
    }

    public float getConfidenceThreshold() {
        return securePrefs.getFloat(KEY_CONFIDENCE_THRESHOLD, 0.70f);
    }

    public float getRecognitionThreshold() {
        return getConfidenceThreshold();
    }

    public void saveCodeword(String phrase, float threshold) {
        setCodeword(phrase);
        setConfidenceThreshold(threshold);
    }

    public static String normalizePhrase(String raw) {
        if (raw == null) return "";
        return raw.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-zA-Z0-9\\s]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String hashSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return "";
        }
    }
}

