package com.wesaphzt.privatelock.motion;

/**
 * The original Private Lock detector: it measures how much the raw accelerometer
 * reading changes between two samples (jerk), so a sudden jolt locks at once.
 *
 * <p>Per axis, a change below {@link #NOISE} counts as zero. The remaining changes
 * are combined into one magnitude, and a sample triggers when that magnitude is
 * above the threshold (the sensitivity slider value).
 *
 * <p>Plain Java, so it can be unit tested. Feed it raw TYPE_ACCELEROMETER values
 * at SENSOR_DELAY_NORMAL, as the original app did: the threshold depends on the
 * sample rate.
 */
public class MotionDetector {

    /** Per-axis change (m/s^2) treated as sensor noise. */
    public static final float NOISE = 2.0f;

    private volatile float threshold;

    private float lastX, lastY, lastZ;
    private boolean initialized = false;
    private float lastMagnitude = 0f;

    public MotionDetector(float threshold) {
        this.threshold = threshold;
    }

    /** The 0..40 sensitivity slider value is used directly as the threshold. */
    public static float thresholdFromSensitivity(int sensitivity) {
        return sensitivity;
    }

    public void setThreshold(float threshold) {
        this.threshold = threshold;
    }

    /** Feed one accelerometer sample. Returns true if the change since the last sample is above the threshold. */
    public boolean update(float x, float y, float z) {
        if (!initialized) {
            lastX = x;
            lastY = y;
            lastZ = z;
            lastMagnitude = 0f;
            initialized = true;
            return false;
        }

        float deltaX = denoise(Math.abs(lastX - x));
        float deltaY = denoise(Math.abs(lastY - y));
        float deltaZ = denoise(Math.abs(lastZ - z));

        lastX = x;
        lastY = y;
        lastZ = z;

        lastMagnitude = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
        return lastMagnitude > threshold;
    }

    /** Change measured on the last sample, for the sensitivity-test screen and stats. */
    public float getLastMagnitude() {
        return lastMagnitude;
    }

    /** Make the next sample the new baseline (after unlock, pause, or arriving home). */
    public void reset() {
        initialized = false;
        lastMagnitude = 0f;
    }

    private static float denoise(float delta) {
        return delta < NOISE ? 0f : delta;
    }
}
