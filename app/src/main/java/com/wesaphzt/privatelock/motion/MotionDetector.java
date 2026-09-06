package com.wesaphzt.privatelock.motion;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;

/**
 * Decides whether the device is being deliberately moved (picked up / snatched)
 * as opposed to receiving a one-off impact (being set down firmly).
 *
 * <p>The old approach thresholded the frame-to-frame jerk of the raw
 * accelerometer. Research on drop/impact detection shows that a put-down impact
 * produces the <em>largest, most rapid</em> signal swing of all, while a pick-up
 * is a smaller, slow-changing, <em>sustained</em> signal - so jerk thresholding
 * fires hardest on exactly the event we want to ignore.
 *
 * <p>This detector instead works on linear acceleration (gravity removed) and
 * requires motion to be <em>sustained</em>:
 * <ol>
 *   <li>magnitude of linear acceleration is low-pass filtered, so a lone impact
 *       spike is damped away;</li>
 *   <li>a lock only triggers once the smoothed magnitude stays above the
 *       threshold continuously for {@link #DEFAULT_MIN_MOTION_DURATION_MS}. An
 *       impact is a sub-150ms transient; a pick-up lasts far longer.</li>
 * </ol>
 */
public class MotionDetector {

    //low-pass factor for the motion magnitude; higher = more smoothing, so a
    //single impact spike is damped while continuous motion accumulates
    private static final float MAGNITUDE_SMOOTHING = 0.8f;
    //low-pass factor used to estimate gravity when only the raw accelerometer is
    //available (device has no linear-acceleration sensor)
    private static final float GRAVITY_SMOOTHING = 0.8f;
    //motion must persist at least this long before a lock triggers; tuned high
    //so brief in-hand handling (showing someone the phone) is ignored and only
    //a deliberate, sustained move (someone taking it and walking off) locks
    public static final long DEFAULT_MIN_MOTION_DURATION_MS = 900;

    private volatile float threshold;        //m/s^2 of sustained linear motion
    private final long minMotionDurationMs;

    private float smoothedMagnitude = 0f;
    private float gravityX, gravityY, gravityZ;
    private boolean gravityInitialized = false;

    private long motionStartNanos = 0;       //0 = not currently in sustained motion
    private boolean triggered = false;       //latched so one continuous motion locks once

    public MotionDetector(float threshold, long minMotionDurationMs) {
        this.threshold = threshold;
        this.minMotionDurationMs = minMotionDurationMs;
    }

    /** Maps the 0..40 sensitivity slider to a linear-acceleration threshold in m/s^2. */
    public static float thresholdFromSensitivity(int sensitivity) {
        //0.5 -> default slider (10) needs ~5 m/s^2 of sustained motion, well
        //above gentle handling; the slider still spans very sensitive..very tolerant
        return sensitivity * 0.5f;
    }

    /** Prefer the linear-acceleration sensor; fall back to the raw accelerometer. */
    public static Sensor getBestSensor(SensorManager sensorManager) {
        Sensor sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        if (sensor == null) {
            sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
        return sensor;
    }

    public void setThreshold(float threshold) {
        this.threshold = threshold;
    }

    /** Feed one sensor sample. Returns true exactly once per sustained-motion burst. */
    public boolean update(SensorEvent event) {
        boolean isLinear = event.sensor != null
                && event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION;

        float lx, ly, lz;
        if (isLinear) {
            lx = event.values[0];
            ly = event.values[1];
            lz = event.values[2];
        } else {
            //isolate gravity with a low-pass filter, then subtract it out
            if (!gravityInitialized) {
                gravityX = event.values[0];
                gravityY = event.values[1];
                gravityZ = event.values[2];
                gravityInitialized = true;
            } else {
                gravityX = GRAVITY_SMOOTHING * gravityX + (1 - GRAVITY_SMOOTHING) * event.values[0];
                gravityY = GRAVITY_SMOOTHING * gravityY + (1 - GRAVITY_SMOOTHING) * event.values[1];
                gravityZ = GRAVITY_SMOOTHING * gravityZ + (1 - GRAVITY_SMOOTHING) * event.values[2];
            }
            lx = event.values[0] - gravityX;
            ly = event.values[1] - gravityY;
            lz = event.values[2] - gravityZ;
        }

        float magnitude = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);

        //low-pass the magnitude so a lone impact spike is damped out
        smoothedMagnitude = MAGNITUDE_SMOOTHING * smoothedMagnitude
                + (1 - MAGNITUDE_SMOOTHING) * magnitude;

        long now = event.timestamp; //nanoseconds, monotonic

        if (smoothedMagnitude >= threshold) {
            if (motionStartNanos == 0) {
                motionStartNanos = now;
            }
            long elapsedMs = (now - motionStartNanos) / 1_000_000L;
            if (!triggered && elapsedMs >= minMotionDurationMs) {
                triggered = true;
                return true;
            }
        } else {
            //motion stopped; re-arm for the next continuous movement
            motionStartNanos = 0;
            triggered = false;
        }
        return false;
    }

    /** Current smoothed motion level (m/s^2), for the sensitivity-test UI. */
    public float getSmoothedMagnitude() {
        return smoothedMagnitude;
    }

    /** Discard all accumulated state (e.g. when re-arming after an unlock). */
    public void reset() {
        smoothedMagnitude = 0f;
        gravityInitialized = false;
        motionStartNanos = 0;
        triggered = false;
    }
}
