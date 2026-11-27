package com.safepath.ai.ai;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for CodewordDetector verifying phrase matching and false trigger rejection.
 */
public class CodewordDetectorTest {

    @Test
    public void testStrictMatchingForBlueMoon() {
        String target = "blue moon";

        // Exact match
        assertEquals(1.0f, CodewordDetector.calculateSimilarity("blue moon", target), 0.001f);
        assertEquals(1.0f, CodewordDetector.calculateSimilarity("BLUE MOON", target), 0.001f);

        // Phrase embedded in sentence
        assertEquals(1.0f, CodewordDetector.calculateSimilarity("help me blue moon please", target), 0.001f);
        assertEquals(1.0f, CodewordDetector.calculateSimilarity("i say blue moon right now", target), 0.001f);
    }

    @Test
    public void testSingleWordRejection() {
        String target = "blue moon";

        // Single words must NOT trigger (0.0f)
        assertEquals(0.0f, CodewordDetector.calculateSimilarity("blue", target), 0.001f);
        assertEquals(0.0f, CodewordDetector.calculateSimilarity("moon", target), 0.001f);
        assertEquals(0.0f, CodewordDetector.calculateSimilarity("the sky is blue today", target), 0.001f);
        assertEquals(0.0f, CodewordDetector.calculateSimilarity("look at the full moon", target), 0.001f);
    }

    @Test
    public void testUnrelatedWordsRejection() {
        String target = "blue moon";

        assertEquals(0.0f, CodewordDetector.calculateSimilarity("hello world", target), 0.001f);
        assertEquals(0.0f, CodewordDetector.calculateSimilarity("safe path emergency", target), 0.001f);
        assertEquals(0.0f, CodewordDetector.calculateSimilarity("", target), 0.001f);
    }
}
