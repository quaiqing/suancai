package com.qing.timetap;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.TextView;

public class TimerAccessibilityService extends AccessibilityService {
    static final String ACTION_POINT_SELECTED = "com.qing.timetap.POINT_SELECTED";
    static final String ACTION_STATUS = "com.qing.timetap.STATUS";
    static final String EXTRA_X = "x";
    static final String EXTRA_Y = "y";
    static final String EXTRA_STATUS = "status";

    private static volatile TimerAccessibilityService instance;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean taskRunning = false;
    private volatile long taskGeneration = 0L;
    private Thread taskThread;
    private WindowManager windowManager;
    private View pickerView;
    private View markerView;
    private WindowManager.LayoutParams markerParams;
    private TextView pickerHintView;
    private int pickerScreenWidth = 1;
    private int pickerScreenHeight = 1;
    private int markerSizePx = 1;

    public static TimerAccessibilityService getInstance() {
        return instance;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        broadcastStatus("无障碍服务已连接");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {
        stopTask();
    }

    @Override
    public void onDestroy() {
        stopTask();
        removePicker();
        if (instance == this) instance = null;
        super.onDestroy();
    }

    public void startPointPicker() {
        mainHandler.post(() -> {
            if (pickerView != null || windowManager == null) return;

            Point screen = new Point();
            windowManager.getDefaultDisplay().getRealSize(screen);
            pickerScreenWidth = Math.max(1, screen.x);
            pickerScreenHeight = Math.max(1, screen.y);
            markerSizePx = dp(58);

            LinearLayout toolbar = new LinearLayout(this);
            toolbar.setOrientation(LinearLayout.HORIZONTAL);
            toolbar.setGravity(Gravity.CENTER_VERTICAL);
            toolbar.setPadding(dp(8), dp(4), dp(8), dp(4));
            toolbar.setBackgroundColor(Color.rgb(28, 37, 55));

            pickerHintView = new TextView(this);
            pickerHintView.setText("拖动红点定位 · X=" + (pickerScreenWidth / 2)
                    + " Y=" + (pickerScreenHeight / 2));
            pickerHintView.setTextColor(Color.WHITE);
            pickerHintView.setTextSize(13f);
            pickerHintView.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            toolbar.addView(pickerHintView, hintParams);

            Button save = new Button(this);
            save.setText("保存");
            save.setTextSize(12f);
            toolbar.addView(save, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(46)));
            Button cancel = new Button(this);
            cancel.setText("取消");
            cancel.setTextSize(12f);
            toolbar.addView(cancel, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(46)));

            pickerView = toolbar;
            WindowManager.LayoutParams toolbarParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT);
            toolbarParams.gravity = Gravity.TOP | Gravity.START;

            TextView marker = new TextView(this);
            marker.setText("◎");
            marker.setTextColor(Color.WHITE);
            marker.setTextSize(30f);
            marker.setGravity(Gravity.CENTER);
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(0xE6E53935);
            circle.setStroke(dp(3), Color.WHITE);
            marker.setBackground(circle);
            markerView = marker;
            int markerFlags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
            markerParams = new WindowManager.LayoutParams(markerSizePx, markerSizePx,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, markerFlags,
                    PixelFormat.TRANSLUCENT);
            markerParams.gravity = Gravity.TOP | Gravity.START;
            markerParams.x = Math.max(0, (pickerScreenWidth - markerSizePx) / 2);
            markerParams.y = Math.max(dp(80), (pickerScreenHeight - markerSizePx) / 2);

            marker.setOnTouchListener(new View.OnTouchListener() {
                private float downRawX;
                private float downRawY;
                private int startX;
                private int startY;

                @Override
                public boolean onTouch(View view, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        startX = markerParams.x;
                        startY = markerParams.y;
                        return true;
                    }
                    if (event.getAction() == MotionEvent.ACTION_MOVE) {
                        markerParams.x = clamp(startX + Math.round(event.getRawX() - downRawX),
                                0, Math.max(0, pickerScreenWidth - markerSizePx));
                        markerParams.y = clamp(startY + Math.round(event.getRawY() - downRawY),
                                0, Math.max(0, pickerScreenHeight - markerSizePx));
                        try { windowManager.updateViewLayout(markerView, markerParams); }
                        catch (Exception ignored) {}
                        updatePickerCoordinateHint();
                        return true;
                    }
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        updatePickerCoordinateHint();
                        return true;
                    }
                    return true;
                }
            });

            save.setOnClickListener(v -> {
                if (markerParams == null) return;
                int x = markerParams.x + markerSizePx / 2;
                int y = markerParams.y + markerSizePx / 2;
                getSharedPreferences("settings", MODE_PRIVATE).edit()
                        .putInt("x", x).putInt("y", y).apply();
                Intent selected = new Intent(ACTION_POINT_SELECTED).setPackage(getPackageName());
                selected.putExtra(EXTRA_X, x);
                selected.putExtra(EXTRA_Y, y);
                sendBroadcast(selected);
                removePicker();
                broadcastStatus("已保存坐标：X=" + x + "，Y=" + y);
            });
            cancel.setOnClickListener(v -> removePicker());

            try {
                windowManager.addView(pickerView, toolbarParams);
                windowManager.addView(markerView, markerParams);
                updatePickerCoordinateHint();
                broadcastStatus("点选模式已开启：可切换到目标应用，拖动红点后点工具条上的“保存”");
            } catch (Exception e) {
                removePicker();
                broadcastStatus("无法打开坐标点选层：" + e.getMessage());
            }
        });
    }

    private void updatePickerCoordinateHint() {
        if (pickerHintView != null && markerParams != null) {
            int x = markerParams.x + markerSizePx / 2;
            int y = markerParams.y + markerSizePx / 2;
            pickerHintView.setText("拖动红点定位 · X=" + x + " Y=" + y);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void removePicker() {
        mainHandler.post(() -> {
            if (windowManager != null) {
                if (markerView != null) {
                    try { windowManager.removeView(markerView); } catch (Exception ignored) {}
                }
                if (pickerView != null) {
                    try { windowManager.removeView(pickerView); } catch (Exception ignored) {}
                }
            }
            markerView = null;
            pickerView = null;
            markerParams = null;
            pickerHintView = null;
        });
    }

    public synchronized void startScheduledTask(int x, int y, int clockMode, long targetEpochMs,
                                                int correctionSign, long correctionMs) {
        stopTask();
        taskRunning = true;
        final long generation = taskGeneration;
        taskThread = new Thread(() -> runScheduledTask(generation, x, y, clockMode, targetEpochMs,
                correctionSign, correctionMs), "TimeTap-Scheduler");
        taskThread.start();
    }

    public synchronized void stopTask() {
        taskRunning = false;
        taskGeneration++;
        if (taskThread != null) {
            taskThread.interrupt();
            taskThread = null;
        }
        broadcastStatus("任务已停止");
    }

    private void runScheduledTask(long generation, int x, int y, int clockMode, long targetEpochMs,
                                  int correctionSign, long correctionMs) {
        long correction = correctionSign * Math.max(0L, correctionMs);
        long effectiveTarget = targetEpochMs + correction;
        broadcastStatus(clockMode == 1 ? "正在同步中国网络时间…" : "任务已启动，等待设备时间目标点");

        if (clockMode == 1) {
            boolean ok = ChinaTime.syncNow();
            if (!isTaskCurrent(generation)) return;
            if (!ok) {
                taskRunning = false;
                broadcastStatus("网络校时失败：请检查网络后重新同步");
                return;
            }
            if (effectiveTarget <= ChinaTime.nowNetworkMillis()) {
                taskRunning = false;
                broadcastStatus("网络校时后发现目标时刻已到或已过；为避免误点，任务已取消");
                return;
            }
            broadcastStatus("网络时间已校准，等待目标时刻");
        }

        long lastSyncAttempt = SystemClock.elapsedRealtime();
        while (isTaskCurrent(generation)) {
            long now = (clockMode == 1) ? ChinaTime.nowNetworkMillis() : System.currentTimeMillis();
            long remaining = effectiveTarget - now;
            if (remaining <= 0L) break;

            if (clockMode == 1 && remaining > 15000L
                    && SystemClock.elapsedRealtime() - lastSyncAttempt > 120000L) {
                lastSyncAttempt = SystemClock.elapsedRealtime();
                ChinaTime.syncNow();
                continue;
            }

            try {
                if (remaining > 1000L) {
                    Thread.sleep(Math.min(remaining - 300L, 1000L));
                } else if (remaining > 30L) {
                    Thread.sleep(Math.max(1L, remaining - 10L));
                } else if (remaining > 3L) {
                    java.util.concurrent.locks.LockSupport.parkNanos((remaining - 2L) * 1_000_000L);
                } else {
                    // Final few milliseconds: spin against the selected clock to reduce wake-up overhead.
                }
            } catch (InterruptedException e) {
                if (!isTaskCurrent(generation)) return;
            }
        }

        if (!isTaskCurrent(generation)) return;
        taskRunning = false;
        Path path = new Path();
        path.moveTo(x, y);
        path.lineTo(x + 0.1f, y + 0.1f);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0L, 100L))
                .build();
        broadcastStatus("目标时刻已到，正在发送 100ms 点击手势…");
        boolean accepted;
        try {
            accepted = dispatchGesture(gesture, new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    broadcastStatus("点击手势已完成（坐标 X=" + x + "，Y=" + y + "）");
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    broadcastStatus("点击手势被系统取消");
                }
            }, null);
        } catch (Exception e) {
            accepted = false;
            broadcastStatus("发送点击失败：" + e.getMessage());
        }
        if (!accepted) broadcastStatus("系统未接受点击手势，请检查无障碍服务或屏幕状态");
    }

    private boolean isTaskCurrent(long generation) {
        return taskRunning && taskGeneration == generation;
    }

    private void broadcastStatus(String message) {
        Intent intent = new Intent(ACTION_STATUS).setPackage(getPackageName());
        intent.putExtra(EXTRA_STATUS, message);
        sendBroadcast(intent);
    }
}
