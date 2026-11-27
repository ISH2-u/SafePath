package com.safepath.ai.controllers;

import android.view.KeyEvent;

/**
 * VolumeKeyDetector — Robust hardware key event sequence detector.
 *
 * SPECIFICATION:
 * - Trigger button: Volume Down (KeyEvent.KEYCODE_VOLUME_DOWN = 25).
 * - Required presses: 3 presses.
 * - Maximum interval between 1st and 3rd press: 2000 milliseconds.
 * - Action filter: Key down (KeyEvent.ACTION_DOWN = 0) with repeat count == 0.
 * - Unrelated Volume Up presses are ignored, reset any active sequence, and do NOT trigger SOS.
 * - Accidental single or double presses reset after timeout and do not trigger.
 */
public class VolumeKeyDetector {

    public interface OnTriplePressListener {
        void onTriplePressDetected();
    }

    public static final int KEYCODE_VOLUME_UP = 24;
    public static final int KEYCODE_VOLUME_DOWN = 25;
    public static final int ACTION_DOWN = 0;
    public static final int ACTION_UP = 1;

    public static final int REQUIRED_PRESSES = 3;
    public static final long MAX_INTERVAL_MS = 2000L;

    private final OnTriplePressListener listener;
    private int pressCount = 0;
    private long firstPressTimeMs = 0L;

    public VolumeKeyDetector(OnTriplePressListener listener) {
        this.listener = listener;
    }

    /**
     * Process an incoming KeyEvent from Android framework.
     */
    public boolean onKeyEvent(KeyEvent event) {
        if (event == null) return false;
        return onKeyEvent(event.getKeyCode(), event.getAction(), event.getRepeatCount(), System.currentTimeMillis());
    }

    /**
     * Core deterministic key event processing logic.
     *
     * @param keyCode Hardware key code.
     * @param action Key action (ACTION_DOWN or ACTION_UP).
     * @param repeatCount Repeat count (0 for first down, >0 for held-down repeat).
     * @param timestampMs Timestamp in milliseconds.
     * @return true if the event was consumed (3rd press triggered SOS), false otherwise.
     */
    public synchronized boolean onKeyEvent(int keyCode, int action, int repeatCount, long timestampMs) {
        // Reset sequence if Volume Up is pressed so mixed sequences do not trigger
        if (keyCode == KEYCODE_VOLUME_UP) {
            if (action == ACTION_DOWN && repeatCount == 0) {
                reset();
            }
            return false;
        }

        // Ignore other unrelated keys
        if (keyCode != KEYCODE_VOLUME_DOWN) {
            return false;
        }

        // Only register distinct KEY_DOWN events, ignore held-down repeat events or key-up events
        if (action != ACTION_DOWN || repeatCount > 0) {
            return false;
        }

        if (pressCount == 0) {
            // First press in sequence
            firstPressTimeMs = timestampMs;
            pressCount = 1;
            return false;
        }

        // Subsequent presses: verify within time window
        long elapsed = timestampMs - firstPressTimeMs;
        if (elapsed <= MAX_INTERVAL_MS) {
            pressCount++;
            if (pressCount >= REQUIRED_PRESSES) {
                // Triple press sequence completed!
                reset();
                if (listener != null) {
                    listener.onTriplePressDetected();
                }
                return true; // Consume the 3rd press
            }
            return false;
        } else {
            // Window expired! Reset sequence and count this press as the first press
            firstPressTimeMs = timestampMs;
            pressCount = 1;
            return false;
        }
    }

    public synchronized void reset() {
        pressCount = 0;
        firstPressTimeMs = 0L;
    }

    public synchronized int getPressCount() {
        return pressCount;
    }

    public synchronized long getFirstPressTimeMs() {
        return firstPressTimeMs;
    }
}
