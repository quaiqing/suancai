package com.qing.timetap;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int CLOCK_SYSTEM = 0;
    private static final int CLOCK_CHINA = 1;
    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.CHINA);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private EditText etX, etY, etDate, etTime, etOffset;
    private TextView tvDeviceClock, tvNetworkClock, tvSyncInfo, tvResolution, tvPoint,
            tvTarget, tvCountdown, tvStatus;
    private RadioGroup rgClock, rgAdjust;
    private int screenWidth = 1;
    private int screenHeight = 1;
    private boolean receiverRegistered = false;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (TimerAccessibilityService.ACTION_POINT_SELECTED.equals(intent.getAction())) {
                int x = intent.getIntExtra(TimerAccessibilityService.EXTRA_X, 0);
                int y = intent.getIntExtra(TimerAccessibilityService.EXTRA_Y, 0);
                etX.setText(String.valueOf(x));
                etY.setText(String.valueOf(y));
                showPoint(x, y);
            } else if (TimerAccessibilityService.ACTION_STATUS.equals(intent.getAction())) {
                String status = intent.getStringExtra(TimerAccessibilityService.EXTRA_STATUS);
                if (status != null) tvStatus.setText("任务状态：" + status);
            }
        }
    };

    private final Runnable clockUpdater = new Runnable() {
        @Override
        public void run() {
            updateClockUi();
            updateCountdownUi();
            handler.postDelayed(this, 10L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindViews();
        readScreenSize();
        loadSavedSettings();
        setDefaultDateAndTime();
        wireButtons();
        registerAppReceiver();
        updateAccessibilityButton();
        handler.post(clockUpdater);
    }

    private void bindViews() {
        etX = findViewById(R.id.etX);
        etY = findViewById(R.id.etY);
        etDate = findViewById(R.id.etDate);
        etTime = findViewById(R.id.etTime);
        etOffset = findViewById(R.id.etOffset);
        tvDeviceClock = findViewById(R.id.tvDeviceClock);
        tvNetworkClock = findViewById(R.id.tvNetworkClock);
        tvSyncInfo = findViewById(R.id.tvSyncInfo);
        tvResolution = findViewById(R.id.tvResolution);
        tvPoint = findViewById(R.id.tvPoint);
        tvTarget = findViewById(R.id.tvTarget);
        tvCountdown = findViewById(R.id.tvCountdown);
        tvStatus = findViewById(R.id.tvStatus);
        rgClock = findViewById(R.id.rgClock);
        rgAdjust = findViewById(R.id.rgAdjust);
    }

    private void readScreenSize() {
        Point point = new Point();
        Display display = ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay();
        display.getRealSize(point);
        screenWidth = Math.max(1, point.x);
        screenHeight = Math.max(1, point.y);
        tvResolution.setText("当前屏幕分辨率：" + screenWidth + " × " + screenHeight
                + "（X：0–" + (screenWidth - 1) + "，Y：0–" + (screenHeight - 1) + "）");
    }

    private void loadSavedSettings() {
        android.content.SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        int x = prefs.getInt("x", -1);
        int y = prefs.getInt("y", -1);
        if (x >= 0 && y >= 0) {
            etX.setText(String.valueOf(x));
            etY.setText(String.valueOf(y));
            showPoint(x, y);
        }
        int clockMode = prefs.getInt("clockMode", CLOCK_SYSTEM);
        rgClock.check(clockMode == CLOCK_CHINA ? R.id.rbChina : R.id.rbSystem);
        int adjustMode = prefs.getInt("adjustMode", 0);
        if (adjustMode == 1) rgAdjust.check(R.id.rbAdvance);
        else if (adjustMode == 2) rgAdjust.check(R.id.rbDelay);
        else rgAdjust.check(R.id.rbNoAdjust);
        etOffset.setText(String.valueOf(prefs.getLong("offsetMs", 0L)));
    }

    private void setDefaultDateAndTime() {
        ZoneId zone = getClockMode() == CLOCK_CHINA ? ZoneId.of("Asia/Shanghai") : ZoneId.systemDefault();
        LocalDateTime nextMinute = LocalDateTime.ofInstant(Instant.now(), zone).plusMinutes(1)
                .withSecond(0).withNano(0);
        etDate.setText(nextMinute.toLocalDate().toString());
        etTime.setText(String.format(Locale.US, "%02d:%02d:00.000", nextMinute.getHour(), nextMinute.getMinute()));
        rgClock.setOnCheckedChangeListener((group, checkedId) -> {
            ZoneId selectedZone = checkedId == R.id.rbChina ? ZoneId.of("Asia/Shanghai") : ZoneId.systemDefault();
            etDate.setText(LocalDate.now(selectedZone).toString());
            updateCountdownUi();
        });
    }

    private void wireButtons() {
        TextWatcher pointWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { updatePointFromInputs(); }
        };
        etX.addTextChangedListener(pointWatcher);
        etY.addTextChangedListener(pointWatcher);

        Button btnSync = findViewById(R.id.btnSync);
        Button btnPick = findViewById(R.id.btnPick);
        Button btnEnable = findViewById(R.id.btnEnableAccessibility);
        Button btnStart = findViewById(R.id.btnStart);
        Button btnStop = findViewById(R.id.btnStop);

        btnSync.setOnClickListener(v -> {
            tvSyncInfo.setText("网络时间状态：正在向 NTP 服务器校时…");
            new Thread(() -> {
                boolean success = ChinaTime.syncNow();
                runOnUiThread(() -> {
                    if (success) {
                        tvSyncInfo.setText("校时成功：与 NTP 偏差约 " + ChinaTime.getOffsetMillis()
                                + " ms，网络往返延迟约 " + ChinaTime.getDelayMillis() + " ms");
                        Toast.makeText(this, "中国网络时间已同步", Toast.LENGTH_SHORT).show();
                    } else {
                        tvSyncInfo.setText("网络时间状态：校时失败，请检查网络或稍后重试");
                        Toast.makeText(this, "NTP 校时失败", Toast.LENGTH_LONG).show();
                    }
                });
            }, "TimeTap-NTP-Manual").start();
        });

        btnPick.setOnClickListener(v -> {
            TimerAccessibilityService service = TimerAccessibilityService.getInstance();
            if (service == null || !isAccessibilityEnabled()) {
                Toast.makeText(this, "请先开启本软件的无障碍辅助功能", Toast.LENGTH_LONG).show();
                return;
            }
            service.startPointPicker();
        });

        btnEnable.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        btnStart.setOnClickListener(v -> startTask());

        btnStop.setOnClickListener(v -> {
            TimerAccessibilityService service = TimerAccessibilityService.getInstance();
            if (service != null) service.stopTask();
            else tvStatus.setText("任务状态：无障碍服务当前未连接");
        });
    }

    private void startTask() {
        if (!isAccessibilityEnabled()) {
            Toast.makeText(this, "请先开启无障碍辅助功能", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }
        TimerAccessibilityService service = TimerAccessibilityService.getInstance();
        if (service == null) {
            Toast.makeText(this, "无障碍服务正在连接，请返回软件后再试一次", Toast.LENGTH_LONG).show();
            return;
        }

        int x, y;
        try {
            x = Integer.parseInt(etX.getText().toString().trim());
            y = Integer.parseInt(etY.getText().toString().trim());
        } catch (Exception e) {
            Toast.makeText(this, "请正确输入 X、Y 坐标", Toast.LENGTH_LONG).show();
            return;
        }
        if (x < 0 || y < 0 || x >= screenWidth || y >= screenHeight) {
            Toast.makeText(this, "坐标超出当前屏幕范围", Toast.LENGTH_LONG).show();
            return;
        }

        int clockMode = getClockMode();
        long targetEpoch;
        try {
            LocalDate date = LocalDate.parse(etDate.getText().toString().trim(), DateTimeFormatter.ISO_LOCAL_DATE);
            LocalTime time = parseTime(etTime.getText().toString().trim());
            ZoneId zone = clockMode == CLOCK_CHINA ? ZoneId.of("Asia/Shanghai") : ZoneId.systemDefault();
            targetEpoch = LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli();
        } catch (Exception e) {
            Toast.makeText(this, "时间格式错误。日期用 yyyy-MM-dd，时间用 HH:mm:ss.SSS", Toast.LENGTH_LONG).show();
            return;
        }

        int adjustId = rgAdjust.getCheckedRadioButtonId();
        int correctionSign = adjustId == R.id.rbAdvance ? -1 : (adjustId == R.id.rbDelay ? 1 : 0);
        long correctionMs;
        try {
            String raw = etOffset.getText().toString().trim();
            correctionMs = TextUtils.isEmpty(raw) ? 0L : Long.parseLong(raw);
            if (correctionMs < 0L || correctionMs > 86400000L) throw new NumberFormatException();
        } catch (Exception e) {
            Toast.makeText(this, "校准值请输入 0–86400000 的毫秒数", Toast.LENGTH_LONG).show();
            return;
        }
        long effective = targetEpoch + correctionSign * correctionMs;
        long now = clockMode == CLOCK_CHINA && ChinaTime.isSynced()
                ? ChinaTime.nowNetworkMillis() : System.currentTimeMillis();
        if (effective <= now) {
            Toast.makeText(this, "有效目标时刻已到或已过，请设置未来的时间", Toast.LENGTH_LONG).show();
            return;
        }

        getSharedPreferences("settings", MODE_PRIVATE).edit()
                .putInt("x", x).putInt("y", y).putInt("clockMode", clockMode)
                .putInt("adjustMode", correctionSign == -1 ? 1 : (correctionSign == 1 ? 2 : 0))
                .putLong("offsetMs", correctionMs).apply();
        tvStatus.setText("任务状态：正在准备定时任务…");
        service.startScheduledTask(x, y, clockMode, targetEpoch, correctionSign, correctionMs);
        Toast.makeText(this, "定时任务已启动，请保持屏幕亮起", Toast.LENGTH_LONG).show();
    }

    private LocalTime parseTime(String raw) {
        String[] parts = raw.split(":", -1);
        if (parts.length < 2 || parts.length > 3) throw new IllegalArgumentException("time");
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);
        int second = 0;
        int millis = 0;
        if (parts.length == 3) {
            String secPart = parts[2];
            int dot = secPart.indexOf('.');
            if (dot >= 0) {
                second = Integer.parseInt(secPart.substring(0, dot));
                String msText = secPart.substring(dot + 1);
                if (msText.isEmpty() || msText.length() > 3) throw new IllegalArgumentException("millis");
                while (msText.length() < 3) msText += "0";
                millis = Integer.parseInt(msText);
            } else {
                second = Integer.parseInt(secPart);
            }
        }
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59
                || second < 0 || second > 59 || millis < 0 || millis > 999) {
            throw new IllegalArgumentException("out of range");
        }
        return LocalTime.of(hour, minute, second, millis * 1_000_000);
    }

    private int getClockMode() {
        return rgClock.getCheckedRadioButtonId() == R.id.rbChina ? CLOCK_CHINA : CLOCK_SYSTEM;
    }

    private void updateClockUi() {
        long deviceNow = System.currentTimeMillis();
        tvDeviceClock.setText("设备时间：" + DISPLAY_FORMAT.withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(deviceNow)));
        if (ChinaTime.isSynced()) {
            long chinaNow = ChinaTime.nowNetworkMillis();
            tvNetworkClock.setText("中国网络时间：" + DISPLAY_FORMAT.withZone(ZoneId.of("Asia/Shanghai")).format(Instant.ofEpochMilli(chinaNow)));
            long offset = ChinaTime.getOffsetMillis();
            long delay = ChinaTime.getDelayMillis();
            tvSyncInfo.setText("最近校时偏差约 " + offset + " ms；NTP 往返延迟约 " + delay + " ms");
        } else {
            tvNetworkClock.setText("中国网络时间：未同步");
            if (!tvSyncInfo.getText().toString().contains("正在向 NTP")) {
                tvSyncInfo.setText("网络时间状态：尚未校时（选择网络时间后会自动尝试）");
            }
        }
    }

    private void updateCountdownUi() {
        try {
            if (etDate == null || TextUtils.isEmpty(etDate.getText()) || TextUtils.isEmpty(etTime.getText())) return;
            LocalDate date = LocalDate.parse(etDate.getText().toString().trim(), DateTimeFormatter.ISO_LOCAL_DATE);
            LocalTime time = parseTime(etTime.getText().toString().trim());
            int mode = getClockMode();
            ZoneId zone = mode == CLOCK_CHINA ? ZoneId.of("Asia/Shanghai") : ZoneId.systemDefault();
            long target = LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli();
            int adjustId = rgAdjust.getCheckedRadioButtonId();
            int sign = adjustId == R.id.rbAdvance ? -1 : (adjustId == R.id.rbDelay ? 1 : 0);
            long ms = 0L;
            try { ms = Long.parseLong(etOffset.getText().toString().trim()); } catch (Exception ignored) {}
            ms = Math.max(0L, Math.min(86400000L, ms));
            long effective = target + sign * ms;
            long now = mode == CLOCK_CHINA && ChinaTime.isSynced()
                    ? ChinaTime.nowNetworkMillis() : System.currentTimeMillis();
            tvTarget.setText("有效目标时刻：" + DISPLAY_FORMAT.withZone(zone).format(Instant.ofEpochMilli(effective)));
            long remaining = effective - now;
            if (remaining < 0L) {
                tvCountdown.setText("剩余时间：目标时刻已过 " + formatDuration(-remaining));
            } else {
                tvCountdown.setText("剩余时间：" + formatDuration(remaining));
            }
        } catch (Exception e) {
            tvTarget.setText("有效目标时刻：请检查日期和时间格式");
            tvCountdown.setText("剩余时间：--");
        }
    }

    private String formatDuration(long ms) {
        long hours = ms / 3600000L;
        long minutes = (ms % 3600000L) / 60000L;
        long seconds = (ms % 60000L) / 1000L;
        long millis = ms % 1000L;
        return String.format(Locale.US, "%02d:%02d:%02d.%03d", hours, minutes, seconds, millis);
    }

    private void showPoint(int x, int y) {
        tvPoint.setText("当前点击点坐标：X=" + x + "，Y=" + y);
    }

    private void updatePointFromInputs() {
        if (etX == null || etY == null || tvPoint == null) return;
        String xText = etX.getText().toString().trim();
        String yText = etY.getText().toString().trim();
        if (xText.isEmpty() || yText.isEmpty()) {
            tvPoint.setText("当前点击点：请输入完整的 X/Y 坐标");
            return;
        }
        try {
            showPoint(Integer.parseInt(xText), Integer.parseInt(yText));
        } catch (NumberFormatException e) {
            tvPoint.setText("当前点击点：坐标格式不正确");
        }
    }

    private boolean isAccessibilityEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        ComponentName expected = new ComponentName(this, TimerAccessibilityService.class);
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabled);
        for (String item : splitter) {
            ComponentName enabledComponent = ComponentName.unflattenFromString(item);
            if (expected.equals(enabledComponent)) return true;
        }
        return false;
    }

    private void updateAccessibilityButton() {
        Button button = findViewById(R.id.btnEnableAccessibility);
        button.setText(isAccessibilityEnabled() ? "无障碍辅助功能已开启" : "开启无障碍辅助功能");
    }

    private void registerAppReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(TimerAccessibilityService.ACTION_POINT_SELECTED);
        filter.addAction(TimerAccessibilityService.ACTION_STATUS);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(receiver, filter);
        }
        receiverRegistered = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (tvStatus != null) updateAccessibilityButton();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(clockUpdater);
        if (receiverRegistered) {
            try { unregisterReceiver(receiver); } catch (Exception ignored) {}
        }
        super.onDestroy();
    }
}
