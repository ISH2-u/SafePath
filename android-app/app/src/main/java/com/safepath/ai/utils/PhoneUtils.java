package com.safepath.ai.utils;

import androidx.annotation.Nullable;
import java.util.regex.Pattern;

/**
 * Utility for phone number normalization, validation, and presentation.
 * Supports international formats with country codes, spaces, hyphens, and parentheses.
 */
public final class PhoneUtils {

    // E.164 standard: optional '+', followed by 7 to 15 digits (first digit typically non-zero)
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9]{7,15}$");

    private PhoneUtils() {
        // Utility class
    }

    /**
     * Normalizes a phone number by stripping whitespace, hyphens, brackets, and dots,
     * while preserving a leading '+' for international numbers.
     *
     * @param raw Raw input string (e.g. "+1 (555) 123-4567" or "98765 43210")
     * @return Normalized phone string (e.g. "+15551234567") or empty string if input is null/empty.
     */
    public static String normalize(@Nullable String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return "";
        }

        boolean hasPlus = trimmed.startsWith("+");
        // Remove all non-digit characters
        String digitsOnly = trimmed.replaceAll("[^0-9]", "");

        if (digitsOnly.isEmpty()) {
            return "";
        }

        return hasPlus ? "+" + digitsOnly : digitsOnly;
    }

    /**
     * Validates whether the given phone string (raw or normalized) is a valid phone number.
     *
     * @param phone The phone string to validate.
     * @return True if normalized length is between 7 and 15 digits.
     */
    public static boolean isValid(@Nullable String phone) {
        String normalized = normalize(phone);
        if (normalized.isEmpty()) {
            return false;
        }
        return PHONE_PATTERN.matcher(normalized).matches();
    }

    /**
     * Formats a normalized phone number for user-friendly display.
     *
     * @param phone The normalized or raw phone string.
     * @return Clean display string.
     */
    public static String formatDisplay(@Nullable String phone) {
        String normalized = normalize(phone);
        if (normalized.isEmpty()) {
            return "";
        }
        return normalized;
    }
}
