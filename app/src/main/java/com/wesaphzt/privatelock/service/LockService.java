package com.wesaphzt.privatelock.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.widget.Toast;

import com.wesaphzt.privatelock.MainActivity;
import com.wesaphzt.privatelock.R;
import com.wesaphzt.privatelock.motion.MotionDetector;
import com.wesaphzt.privatelock.receivers.DeviceAdminReceiver;
import com.wesaphzt.privatelock.receivers.NotificationReceiver;
import com.wesaphzt.privatelock.receivers.PauseReceiver;
import com.wesaphzt.privatelock.receivers.PresenceReceiver;
import com.wesaphzt.privatelock.widget.LockWidgetProvider;
import com.wesaphzt.privatelock.wifi.TrustedWifiMonitor;

import static androidx.core.app.NotificationCompat.PRIORITY_LOW;
import static androidx.core.app.NotificationCompat.PRIORITY_MIN;

public class LockService extends Service {

    private Context context;

    public static SensorManager mSensorManager;
    public static Sensor mAccelerometer;

    public static SensorEventListener activeListener;
    //motion detection armed state; toggled off while paused / screen off
    public static boolean disabled = true;
    //whether the foreground service itself is running; distinct from `disabled`,
    //which only reflects whether detection is currently armed
    public static boolean serviceRunning = false;
    //true while connected to a trusted WiFi network; kept apart from `disabled`
    //so unlock, pause and screen-off cannot override it. main thread only.
    public static boolean onTrustedWifi = false;

    //sustained-motion detector; static so the presence receiver can re-arm it
    public static MotionDetector motionDetector;

    //DevicePolicyManager
    DevicePolicyManager mDPM;
    ComponentName mDeviceAdmin;

    //used to guard against locking when the screen is already off
    PowerManager mPowerManager;

    //re-read on every sensor event so the setting can change without a restart
    boolean isRunConstant = false;

    //notifications
    Notification notification;
    NotificationManager notificationManager;
    //ids
    public static String CHANNEL_ID;
    public static String CHANNEL_NAME;
    public static final int NOTIFICATION_ID = 1000;
    //intents
    public static PendingIntent pendingIntent;
    public static PendingIntent pendingCloseIntent;
    public static PendingIntent pendingPauseIntent;

    //sensitivity
    public static final int DEFAULT_SENSITIVITY = 10;
    public static int SENSITIVITY;

    public static final String ACTION_STOP_FOREGROUND_SERVICE = "ACTION_STOP_FOREGROUND_SERVICE";
    public static final String ACTION_START_FOREGROUND_SERVICE = "ACTION_START_FOREGROUND_SERVICE";

    PresenceReceiver presenceReceiver;
    TrustedWifiMonitor trustedWifiMonitor;

    //vibrate
    boolean isHaptic = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        context = getApplicationContext();
        CHANNEL_ID = getString(R.string.notification_main_channel_id);
        CHANNEL_NAME = getString(R.string.notification_main_channel_name);
        //------------------------------------------------------------------------------------------
        if (intent != null) {
            String action = intent.getAction();

            LockWidgetProvider lockWidgetProvider = new LockWidgetProvider();
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

            assert action != null;
            switch (action) {
                case ACTION_START_FOREGROUND_SERVICE:
                    presenceReceiver = new PresenceReceiver();

                    IntentFilter intentFilter = new IntentFilter(Intent.ACTION_USER_PRESENT);
                    intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
                    registerReceiver(presenceReceiver, intentFilter);
                    //------------------------------------------------------------------------------------------
                    try {
                        SENSITIVITY = prefs.getInt(MainActivity.PREFS_THRESHOLD, DEFAULT_SENSITIVITY);
                    } catch (Exception e) {
                        Toast.makeText(context, "Unable to retrieve threshold", Toast.LENGTH_LONG).show();
                    }
                    //------------------------------------------------------------------------------------------
                    notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

                    //dpm
                    mDPM = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
                    mDeviceAdmin = new ComponentName(this, DeviceAdminReceiver.class);

                    mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);

                    motionDetector = new MotionDetector(
                            MotionDetector.thresholdFromSensitivity(SENSITIVITY),
                            MotionDetector.DEFAULT_MIN_MOTION_DURATION_MS);

                    mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
                    mAccelerometer = MotionDetector.getBestSensor(mSensorManager);

                    setSensorListener();
                    //SENSOR_DELAY_GAME (~20ms) gives the detector enough resolution
                    //to tell a brief impact from sustained motion
                    mSensorManager.registerListener(activeListener, mAccelerometer, SensorManager.SENSOR_DELAY_GAME);

                    setNotification(runningTitle());
                    serviceRunning = true;
                    //------------------------------------------------------------------------------------------
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        //create foreground service
                        startForeground(NOTIFICATION_ID, notification);
                        lockWidgetProvider.setWidgetStart(context);
                        disabled = false;

                    } else {
                        notificationManager.notify(NOTIFICATION_ID, notification);
                        lockWidgetProvider.setWidgetStart(context);
                        disabled = false;
                    }
                    startTrustedWifiMonitor();

                    break;

                case ACTION_STOP_FOREGROUND_SERVICE:
                    try {
                        mSensorManager.unregisterListener(activeListener);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    try {
                        unregisterReceiver(presenceReceiver);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    stopTrustedWifiMonitor();
                    disabled = true;
                    serviceRunning = false;

                    if (PauseReceiver.isRunning) {
                        PauseReceiver.mCountdown.cancel();
                        PauseReceiver.isRunning = false;
                    }

                    //remove the foreground notification before stopping; deleting the
                    //channel while still in foreground throws SecurityException
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE);
                    } else {
                        legacyStopForeground();
                    }

                    lockWidgetProvider.setWidgetStop(context);
                    stopSelf();

                    break;
            }
        } else {
            return LockService.START_REDELIVER_INTENT;
        }

        return LockService.START_REDELIVER_INTENT;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        //clear serviceRunning first so the monitor's final callback does not
        //post a notification after the foreground state is gone
        serviceRunning = false;
        stopTrustedWifiMonitor();
        super.onDestroy();
    }

    private void setSensorListener() {
        activeListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                if(LockService.disabled || LockService.onTrustedWifi)
                    return;

                //check prefs here so options can be changed without service restart
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

                isHaptic = prefs.getBoolean("HAPTIC_FEEDBACK", false);
                isRunConstant = prefs.getBoolean("RUN_CONSTANT", false);

                sensorCalc(event);
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {  }
        };
    }

    private void sensorCalc(SensorEvent event) {
        //returns true once per sustained-motion burst; a lone impact is ignored
        if (!motionDetector.update(event)) {
            return;
        }

        //don't force a PIN lock if the screen is already off. this also closes
        //the race where a spike from pressing power is processed before
        //ACTION_SCREEN_OFF arrives and disables detection.
        if (!isScreenInteractive() && !isRunConstant) {
            return;
        }

        try {
            if (isActiveAdmin()) {
                mDPM.lockNow();
                if (isHaptic) {
                    vibrateItBaby();
                }
            } else {
                Toast.makeText(context, "Device admin not enabled", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(context, "Unknown locking error", Toast.LENGTH_SHORT).show();
        }
    }

    private void vibrateItBaby() {
        int vibrateTime = 100;

        Vibrator vibrator;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vibratorManager =
                    (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            vibrator = vibratorManager.getDefaultVibrator();
        } else {
            vibrator = legacyVibrator();
        }
        if (vibrator == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(vibrateTime, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            legacyVibrate(vibrator, vibrateTime);
        }
    }

    @SuppressWarnings("deprecation")
    private void legacyStopForeground() {
        stopForeground(true);
    }

    @SuppressWarnings("deprecation")
    private Vibrator legacyVibrator() {
        return (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    }

    @SuppressWarnings("deprecation")
    private void legacyVibrate(Vibrator vibrator, int milliseconds) {
        vibrator.vibrate(milliseconds);
    }

    private boolean isActiveAdmin() {
        return mDPM.isAdminActive(mDeviceAdmin);
    }

    private boolean isScreenInteractive() {
        //if we can't determine the state, assume interactive so locking still works
        return mPowerManager == null || mPowerManager.isInteractive();
    }

    private void startTrustedWifiMonitor() {
        stopTrustedWifiMonitor();
        trustedWifiMonitor = new TrustedWifiMonitor(context, this::onTrustedWifiChanged);
        trustedWifiMonitor.start();
    }

    private void stopTrustedWifiMonitor() {
        if (trustedWifiMonitor != null) {
            trustedWifiMonitor.stop();
            trustedWifiMonitor = null;
        }
        onTrustedWifi = false;
    }

    private void onTrustedWifiChanged(boolean trusted, String ssid) {
        boolean leftTrustedWifi = onTrustedWifi && !trusted;
        onTrustedWifi = trusted;
        //motion seen before arriving home must not count once we leave
        if (leftTrustedWifi && motionDetector != null) {
            motionDetector.reset();
        }
        if (!serviceRunning || notificationManager == null) return;
        setNotification(trusted ? getString(R.string.notification_trusted_wifi, ssid) : runningTitle());
        notificationManager.notify(NOTIFICATION_ID, notification);
    }

    private String runningTitle() {
        return getString(R.string.app_name) + " is running";
    }

    private void setNotification(String title) {
        //notification
        pendingIntent = PendingIntent.getActivity(context, 0,
                new Intent(context, MainActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE);

        //notification stop button
        Intent intentStopAction = new Intent(context, NotificationReceiver.class);
        intentStopAction.putExtra("lock_service","lock_service_notification");
        pendingCloseIntent = PendingIntent.getBroadcast(context,0, intentStopAction, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        //notification pause button
        Intent intentPauseAction = new Intent(context, PauseReceiver.class);
        intentPauseAction.putExtra("pause_service","pause_service_time");
        pendingPauseIntent = PendingIntent.getBroadcast(context,0, intentPauseAction, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder notificationBuilder;

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
            notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID);
            notification = notificationBuilder
                    .setSmallIcon(R.drawable.ic_lock_white_24dp)
                    .setContentTitle(title)
                    .setCategory(NotificationCompat.CATEGORY_SERVICE)
                    .setContentIntent(pendingIntent)
                    .setWhen(System.currentTimeMillis())
                    .setTicker(title)
                    .addAction(android.R.drawable.ic_menu_close_clear_cancel, "STOP", pendingCloseIntent)
                    .addAction(android.R.drawable.ic_menu_close_clear_cancel, "PAUSE", pendingPauseIntent)
                    .setPriority(PRIORITY_LOW)
                    .setOngoing(true)
                    .build();

        } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.N| Build.VERSION.SDK_INT == Build.VERSION_CODES.N_MR1) {
            notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID);
            notification = notificationBuilder
                    .setSmallIcon(R.drawable.ic_lock_white_24dp)
                    .setContentTitle(title)
                    .setCategory(NotificationCompat.CATEGORY_SERVICE)
                    .setColor(getColor(R.color.colorPrimary))
                    .setContentIntent(pendingIntent)
                    .setWhen(System.currentTimeMillis())
                    .setTicker(title)
                    .addAction(android.R.drawable.ic_menu_close_clear_cancel, "STOP", pendingCloseIntent)
                    .addAction(android.R.drawable.ic_menu_close_clear_cancel, "PAUSE", pendingPauseIntent)
                    .setPriority(PRIORITY_LOW)
                    .setOngoing(true)
                    .build();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);
            channel.setImportance(NotificationManager.IMPORTANCE_LOW);
            channel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
            notificationManager.createNotificationChannel(channel);

            notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID);
            notification = notificationBuilder
                    .setSmallIcon(R.drawable.ic_lock_white_24dp)
                    .setContentTitle(title)
                    .setPriority(PRIORITY_MIN)
                    .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                    .setCategory(NotificationCompat.CATEGORY_SERVICE)
                    .setColor(getColor(R.color.colorPrimary))
                    .setContentIntent(pendingIntent)
                    .setWhen(System.currentTimeMillis())
                    .setTicker(title)
                    .addAction(android.R.drawable.ic_menu_close_clear_cancel, "STOP", pendingCloseIntent)
                    .addAction(android.R.drawable.ic_menu_close_clear_cancel, "PAUSE", pendingPauseIntent)
                    .setOngoing(true)
                    .build();
        }
    }
}
