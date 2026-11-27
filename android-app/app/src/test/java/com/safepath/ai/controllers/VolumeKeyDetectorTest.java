package com.safepath.ai.controllers;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Unit tests for VolumeKeyDetector verifying Volume Down triple-press trigger.
 */
public class VolumeKeyDetectorTest {

    private AtomicInteger triggerCount;
    private VolumeKeyDetector detector;

    @Before
    public void setUp() {
        triggerCount = new AtomicInteger(0);
        detector = new VolumeKeyDetector(triggerCount::incrementAndGet);
    }

    @Test
    public void testThreeVolumeDownPressesWithinTwoSecondsTriggersSos() {
        long baseTime = 1000000L;

        // Press 1
        boolean c1 = detector.onKeyEvent(VolumeKeyDetector.KEYCODE_VOLUME_DOWN, VolumeKeyDetector.ACTION_DOWN, 0, baseTime);
        assertFalse("Press 1 should not consume event", c1);
        assertEquals(1, detector.getPressCount());
        assertEquals(0, triggerCount.get());

        // Press 2 at 600ms
        boolean c2 = detector.onKeyEvent(VolumeKeyDetector.KEYCODE_VOLUME_DOWN, VolumeKeyDetector.ACTION_DOWN, 0, baseTime + 600);
        assertFalse("Press 2 should not consume event", c2);
        assertEquals(2, detector.getPressCount());
        assertEquals(0, triggerCount.get());

        // Press 3 at 1200ms (within 2000ms window)
        boolean c3 = detector.onKeyEvent(VolumeKeyDetector.KEYCODE_VOLUME_DOWN, VolumeKeyDetector.ACTION_DOWN, 0, baseTime + 1200);
        assertTrue("Press 3 should consume event and trigger SOS", c3);
        assertEquals("Should trigger exactly once", 1, triggerCount.get());
        assertEquals("Press count should reset to 0 after trigger", 0, detector.getPressCount());
    }

    @Test
    public void testTwoVolumeDownPressesDoNotTriggerSos() {
        long baseTime = 1000000L;

        detector.onKeyEvent(VolumeKeyDetector.KEYCODE_VOLUME_DOWN, VolumeKeyDetector.ACTION_DOWN, 0, baseTime);
        detector.onKeyEvent(VolumeKeyDetector.KEYCODE_VOLUME_DOWN, VolumeKeyDetector.ACTION_DOWN, 0, baseTime + 500);

        assertEquals("Two Volume Down presses should not trigger SOS", 0, triggerCount.get());
        assertEquals(2, detector.getPressCount());
    }

    @Test
    public void testThreeVolumeDownPressesOutsideTimeWindowDoNotTriggerSos() {
        long baseTime = 1000000L;

        // Press 1 at t=0
        detector.onKeyEvent(VolumeKeyDetector.KEYCODE_VOLUME_DOWN, VolumeKeyDetector.ACTION_DOWN, 0, baseTime);
        assertEquals(1, detector.getPressCount());

        // Press 2 at t=1000ms
        detector.onKeyEvent(VolumeKeyDetector.KEYCODE_VOLUME_DOWN, VolumeKeyDetector.ACTION_DOWN, 0, baseTime + 1000);
        assertEquals(2, detector.getPressCount());

        // Press 3 at t=2500ms (elapsed = 2500ms > 2000ms max interval)
        boolean c3 = detector.onKeyEvent(VolumeKeyDetector.KEYCODE_VOLUME_DOWN, VolumeKeyDetector.ACTION_DOWN, 0, baseTime + 2500);
        assertFalse("Press 3 outside window should not consume event", c3);
        assertEquals("Should not trigger SOS because interval > 2000ms", 0, triggerCount.get());
        assertEquals("Window expired: press 3 resets counter and becomes press 1", 1, detector.getPressCount());
        assertEquals(baseTime + 2500, detector.getFirstPressTimeMs());
    }

    @Test
    public void testVolumeUpNeverTriggersSos() {
        long baseTime = 1000000L;

        boolean u1 = detector.onKeyEvent(VolumeKeyDetector.KEYCODE_VOLUME_UP, VolumeKeyDetector.ACTION_DOWN, 0, baseTime);
        boolean u2 = detector.onKeyEvent(VolumeKeyDetector.KEYCODE_VOLUME_UP, VolumeKeyDetector.ACTION_DOWN, 0, baseTime + 300);
        boolean u3 = detector.onKeyEvent(VolumeKeyDetector.KEYCODE_VOLUME_UP, VolumeKeyDetector.ACTION_DOWN, 0, baseTime + 600);
        boolean u4 = detector.onKeyEvent(VolumeKeyDetector.KEYCODE_VOLUME_UP, VolumeKeyDetector.ACTION_DOWN, 0, baseTime + 900);

        assertFalse(u1);
        assertFalse(u2);
        assertFalse(u3);
        assertFalse(u4);
        assertEquals("Volume Up presses must never trigger SOS", 0, triggerCount.get());
        assertEquals(0, detector.getPressCount());
    }

    @Test
    public void testMixedVolumeUpAndVolumeDownDoNotCountAsThreeValidPresses() {
        long baseTime = 1000000L;

        // Sequence: Down -> Down -> Up -> Down (within 2s)
        detector.onKeyEvent(VolumeKeyDetector.KEYCODE_VOLUME_DOWN, VolumeKeyDetector.ACTION_DOWN, 0, baseTime);
        assertEquals(1, detector.getPressCount());

        detector.onKeyEvent(VolumeKeyDetector.KEYCODE_VOLUME_DOWN, VolumeKeyDetector.ACTION_DOWN, 0, baseTime + 200);
        assertEquals(2, detector.getPressCount());

        // Interleaved Volume Up resets sequence
        boolean upConsumed = detector.onKeyEvent(VolumeKeyDetector.KEYCODE_VOLUME_UP, VolumeKeyDetector.ACTION_DOWN, 0, baseTime + 400);
        assertFalse("Volume Up should not be consumed", upConsumed);
        assertEquals("Volume Up should reset sequence", 0, detector.getPressCount());

        // 3rd Volume Down starts a fresh sequence (count = 1), does NOT trigger
        detector.onKeyEvent(VolumeKeyDetector.KEYCODE_VOLUME_DOWN, VolumeKeyDetector.ACTION_DOWN, 0, baseTime + 600);
        assertEquals(1, detector.getPressCount());
        assertEquals("Mixed Volume Up and Down must not trigger SOS", 0, triggerCount.get());
    }

    @Test
    public void testDuplicateKeyDownAndRepeatEventsDoNotInflateCount() {
        long baseTime = 1000000L;

        // First key down
        detector.onKeyEvent(VolumeKeyDetector.KEYCODE_VOLUME_DOWN, VolumeKeyDetector.ACTION_DOWN, 0, baseTime);
        assertEquals(1, detector.getPressCount());

        // Key held down repeating events (repeatCount > 0)
        detector.onKeyEvent(VolumeKeyDetector.KEYCODE_VOLUME_DOWN, VolumeKeyDetector.ACTION_DOWN, 1, baseTime + 50);
        detector.onKeyEvent(VolumeKeyDetector.KEYCODE_VOLUME_DOWN, VolumeKeyDetector.ACTION_DOWN, 2, baseTime + 100);
        assertEquals("Held down repeat events must not increment count", 1, detector.getPressCount());

        // Key up events (ACTION_UP)
        detector.onKeyEvent(VolumeKeyDetector.KEYCODE_VOLUME_DOWN, VolumeKeyDetector.ACTION_UP, 0, baseTime + 150);
        assertEquals("Key up events must not increment count", 1, detector.getPressCount());

        assertEquals(0, triggerCount.get());
    }

    @Test
    public void testManualResetClearsCounter() {
        long baseTime = 1000000L;

        detector.onKeyEvent(VolumeKeyDetector.KEYCODE_VOLUME_DOWN, VolumeKeyDetector.ACTION_DOWN, 0, baseTime);
        detector.onKeyEvent(VolumeKeyDetector.KEYCODE_VOLUME_DOWN, VolumeKeyDetector.ACTION_DOWN, 0, baseTime + 300);
        assertEquals(2, detector.getPressCount());

        detector.reset();
        assertEquals(0, detector.getPressCount());
        assertEquals(0L, detector.getFirstPressTimeMs());
    }

    @Test
    public void testSecondSequenceAfterFirstTriggerTriggersAgain() {
        long baseTime = 1000000L;

        // First sequence (t=0, 500, 1000)
        detector.onKeyEvent(VolumeKeyDetector.KEYCODE_VOLUME_DOWN, VolumeKeyDetector.ACTION_DOWN, 0, baseTime);
        detector.onKeyEvent(VolumeKeyDetector.KEYCODE_VOLUME_DOWN, VolumeKeyDetector.ACTION_DOWN, 0, baseTime + 500);
        detector.onKeyEvent(VolumeKeyDetector.KEYCODE_VOLUME_DOWN, VolumeKeyDetector.ACTION_DOWN, 0, baseTime + 1000);
        assertEquals(1, triggerCount.get());

        // Second sequence later (t=5000, 5500, 6000)
        detector.onKeyEvent(VolumeKeyDetector.KEYCODE_VOLUME_DOWN, VolumeKeyDetector.ACTION_DOWN, 0, baseTime + 5000);
        detector.onKeyEvent(VolumeKeyDetector.KEYCODE_VOLUME_DOWN, VolumeKeyDetector.ACTION_DOWN, 0, baseTime + 5500);
        detector.onKeyEvent(VolumeKeyDetector.KEYCODE_VOLUME_DOWN, VolumeKeyDetector.ACTION_DOWN, 0, baseTime + 6000);
        assertEquals(2, triggerCount.get());
    }
}
