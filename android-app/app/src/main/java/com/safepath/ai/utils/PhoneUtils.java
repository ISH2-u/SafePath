package com.safepath.ai.utils;

import androidx.annotation.Nullable;
import java.util.regex.Pattern;

/**
 * Utility for phone number normalization, validation, and presentation.
 * Supports international formats with country codes, spaces, hyphens, and parentheses.
 */
public final class PhoneUtils {

    // E.164 standard: optional '+', followed by 7 to 15 digits
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9]{7,15}$");
    private static final Pattern INDIAN_MOBILE_10 = Pattern.compile("^[6-9][0-9]{9}$");

    private PhoneUtils() {
        // Utility class
    }

    /**
     * Normalizes a phone number by stripping whitespace, hyphens, brackets, and dots.
     * Automatically handles 10-digit Indian mobile numbers (starting with 6-9) by formatting with '+91'.
     * Preserves leading '+' for existing international numbers.
     *
     * @param raw Raw input string (e.g. "+91 98765 43210", "9876543210", or "+1 (555) 123-4567")
     * @return Normalized phone string (e.g. "+919876543210" or "+15551234567") or empty string.
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

        if (hasPlus) {
            return "+" + digitsOnly;
        }

        // If 11 digits starting with 0 (e.g. 09876543210), strip leading 0
        if (digitsOnly.length() == 11 && digitsOnly.startsWith("0")) {
            digitsOnly = digitsOnly.substring(1);
        }

        // If standard 10-digit Indian mobile (starts with 6, 7, 8, 9), prefix with +91
        if (digitsOnly.length() == 10 && INDIAN_MOBILE_10.matcher(digitsOnly).matches()) {
            return "+91" + digitsOnly;
        }

        // If 12 digits starting with 91, format with '+'
        if (digitsOnly.length() == 12 && digitsOnly.startsWith("91")) {
            return "+" + digitsOnly;
        }

        return digitsOnly;
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
