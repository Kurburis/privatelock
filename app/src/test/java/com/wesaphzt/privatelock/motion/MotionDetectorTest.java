package com.wesaphzt.privatelock.motion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Behaviour of the original wesaphzt jerk detector (change between two samples). */
public class MotionDetectorTest {

    private static final float DELTA = 0.0001f;
    private static final float GRAVITY = 9.81f;

    @Test
    public void firstSample_neverTriggers() {
        MotionDetector detector = new MotionDetector(0f);

        assertFalse(detector.update(100f, 100f, 100f));
        assertEquals(0f, detector.getLastMagnitude(), DELTA);
    }

    @Test
    public void suddenJolt_triggersOnTheNextSample() {
        MotionDetector detector = new MotionDetector(10f);
        detector.update(0f, 0f, GRAVITY);

        boolean hit = detector.update(12f, 0f, GRAVITY);

        assertTrue(hit);
        assertEquals(12f, detector.getLastMagnitude(), DELTA);
    }

    @Test
    public void combinesAllAxes() {
        MotionDetector detector = new MotionDetector(10f);
        detector.update(0f, 0f, 0f);

        //sqrt(6^2 + 8^2) = 10, which is not above 10
        assertFalse(detector.update(6f, 8f, 0f));
        assertEquals(10f, detector.getLastMagnitude(), DELTA);
    }

    @Test
    public void triggersOnlyAboveThreshold() {
        MotionDetector detector = new MotionDetector(10f);
        detector.update(0f, 0f, 0f);

        assertTrue(detector.update(6f, 8.1f, 0f));
    }

    @Test
    public void ignoresAxisChangesBelowNoise() {
        MotionDetector detector = new MotionDetector(0f);
        detector.update(0f, 0f, GRAVITY);

        //1.9 per axis is below the 2.0 noise floor, so every axis counts as zero
        assertFalse(detector.update(1.9f, 1.9f, GRAVITY + 1.9f));
        assertEquals(0f, detector.getLastMagnitude(), DELTA);
    }

    @Test
    public void steadyHoldInAnyOrientation_doesNotTrigger() {
        MotionDetector detector = new MotionDetector(1f);
        detector.update(3f, 4f, 8f);

        assertFalse(detector.update(3f, 4f, 8f));
        assertFalse(detector.update(3.5f, 4.5f, 8.5f));
    }

    @Test
    public void comparesWithThePreviousSampleNotTheFirst() {
        MotionDetector detector = new MotionDetector(10f);
        detector.update(0f, 0f, 0f);
        detector.update(8f, 0f, 0f);

        //8 -> 16 is a change of 8, below 10, although 0 -> 16 would be above
        assertFalse(detector.update(16f, 0f, 0f));
    }

    @Test
    public void reset_makesNextSampleTheNewBaseline() {
        MotionDetector detector = new MotionDetector(10f);
        detector.update(0f, 0f, 0f);

        detector.reset();

        assertFalse(detector.update(50f, 0f, 0f));
        assertEquals(0f, detector.getLastMagnitude(), DELTA);
    }

    @Test
    public void setThreshold_appliesToNextSample() {
        MotionDetector detector = new MotionDetector(20f);
        detector.update(0f, 0f, 0f);
        assertFalse(detector.update(15f, 0f, 0f));

        detector.setThreshold(10f);

        assertTrue(detector.update(0f, 0f, 0f));
    }

    @Test
    public void sliderValueIsTheThreshold() {
        assertEquals(10f, MotionDetector.thresholdFromSensitivity(10), DELTA);
        assertEquals(0f, MotionDetector.thresholdFromSensitivity(0), DELTA);
    }
}
