package local.hongguo.adhelper;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.KeyguardManager;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayDeque;

public final class AdHelperService extends AccessibilityService {
    static final String PACKAGE = "local.hongguo.adhelper";
    static final String TARGET = "com.phoenix.read";
    static volatile boolean testActive;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final CountdownDetector detector = new CountdownDetector();
    private SharedPreferences prefs;
    private boolean gestureRunning;
    private long lastSwipeAt = -1;
    private String lastStatus = "";

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            try { inspect(); }
            catch (RuntimeException error) {
                gestureRunning = false;
                detector.reset();
                status("页面读取失败，已停止本次判断");
            }
            handler.postDelayed(this, 400);
        }
    };

    @Override protected void onServiceConnected() {
        prefs = getSharedPreferences("helper", MODE_PRIVATE);
        status("服务已连接，等待开启自动滑动");
        handler.removeCallbacks(poll);
        handler.post(poll);
    }

    private void inspect() {
        if (!prefs.getBoolean("armed", false)) {
            detector.reset();
            status("已暂停");
            return;
        }
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        KeyguardManager keyguard = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (!power.isInteractive() || keyguard.isKeyguardLocked()) {
            detector.reset();
            status("屏幕锁定，已暂停判断");
            return;
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            detector.reset();
            status("无法读取前台界面，请运行诊断");
            diagnostic("前台窗口为空，系统或目标应用未提供可读取界面");
            return;
        }
        String packageName = String.valueOf(root.getPackageName());
        boolean testing = PACKAGE.equals(packageName) && testActive;
        if (!TARGET.equals(packageName) && !testing) {
            root.recycle();
            detector.reset();
            status("等待红果前台页面");
            return;
        }
        Frame frame = read(root);
        long now = SystemClock.elapsedRealtime();
        diagnostic("可读取节点 " + frame.nodes + "，文字节点 " + frame.textNodes
                + "\n广告标记 " + frame.signals.ad + "，倒计时 " + frame.signals.seconds()
                + "，计时提示 " + frame.signals.timerLabel + "，结束提示 " + frame.signals.ready());
        // A new ad may appear without a window change or a blank ad-marker frame.
        if (lastSwipeAt >= 0 && now - lastSwipeAt >= 3000
                && (frame.signals.ready() || frame.signals.seconds() != null && frame.signals.seconds() > 0)) {
            detector.reset();
            lastSwipeAt = -1;
        }
        boolean swipe = detector.observe(frame.signals.ad, frame.signals.seconds(), frame.signals.ready(), now);
        if (swipe && !gestureRunning) swipeUp(frame);
        else if (gestureRunning) status("正在执行一次上滑");
        else if (frame.signals.ad && frame.signals.seconds() != null) status("识别到广告倒计时 " + frame.signals.seconds() + " 秒");
        else if (frame.signals.canTryDirectSwipe()) status("识别到可上滑提示，正在确认");
        else if (frame.signals.ad) status("广告页，等待可确认的倒计时结束");
        else status("监测中，未识别到广告倒计时");
    }

    private Frame read(AccessibilityNodeInfo root) {
        Frame frame = new Frame();
        root.getBoundsInScreen(frame.bounds);
        frame.windowId = root.getWindowId();
        frame.packageName = String.valueOf(root.getPackageName());
        ArrayDeque<AccessibilityNodeInfo> nodes = new ArrayDeque<>();
        nodes.add(root);
        int visited = 0;
        try {
            while (!nodes.isEmpty() && visited++ < 400) {
                AccessibilityNodeInfo node = nodes.removeFirst();
                try {
                    frame.nodes++;
                    Rect bounds = new Rect();
                    node.getBoundsInScreen(bounds);
                    if (node.isVisibleToUser() && !bounds.isEmpty() && Rect.intersects(bounds, frame.bounds)) {
                        String id = node.getViewIdResourceName();
                        boolean countdown = id != null && (id.contains("countdown") || id.contains("count_down") || id.contains("skip_time"));
                        frame.text(node.getText(), bounds, countdown);
                        frame.text(node.getContentDescription(), bounds, countdown);
                    }
                    for (int i = 0; i < node.getChildCount() && nodes.size() < 400; i++) {
                        AccessibilityNodeInfo child = node.getChild(i);
                        if (child != null) nodes.addLast(child);
                    }
                } finally { node.recycle(); }
            }
        } finally {
            while (!nodes.isEmpty()) nodes.removeFirst().recycle();
        }
        return frame;
    }

    private static final class Frame {
        final Rect bounds = new Rect();
        final AdSignals signals = new AdSignals();
        int windowId;
        String packageName;
        int nodes;
        int textNodes;

        void text(CharSequence value, Rect location, boolean countdownId) {
            if (value == null || value.length() == 0) return;
            textNodes++;
            boolean edge = location.centerY() < bounds.top + bounds.height() * 0.35f
                    || location.centerY() > bounds.top + bounds.height() * 0.65f;
            signals.accept(value, edge, countdownId);
        }
    }

    private void swipeUp(Frame frame) {
        if (!prefs.getBoolean("armed", false)) {
            detector.reset();
            return;
        }
        AccessibilityNodeInfo current = getRootInActiveWindow();
        boolean sameWindow = current != null && current.getWindowId() == frame.windowId
                && frame.packageName.equals(String.valueOf(current.getPackageName()));
        if (current != null) current.recycle();
        if (!sameWindow) {
            status("页面已切换，已取消滑动");
            return;
        }
        Rect bounds = frame.bounds;
        if (bounds.width() < 200 || bounds.height() < 400) {
            status("页面尺寸异常，已取消滑动");
            return;
        }
        Path path = new Path();
        float x = bounds.left + bounds.width() * 0.5f;
        path.moveTo(x, bounds.top + bounds.height() * 0.72f);
        path.lineTo(x, bounds.top + bounds.height() * 0.30f);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 350)).build();
        gestureRunning = true;
        boolean accepted = dispatchGesture(gesture, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription description) {
                gestureRunning = false;
                lastSwipeAt = SystemClock.elapsedRealtime();
                prefs.edit().putInt("swipes", prefs.getInt("swipes", 0) + 1).apply();
                status("广告已上滑一次");
            }
            @Override public void onCancelled(GestureDescription description) {
                gestureRunning = false;
                status("手势被取消，等待下一次广告");
            }
        }, handler);
        if (!accepted) {
            gestureRunning = false;
            status("系统未接受手势，请检查无障碍权限");
        }
    }

    private void diagnostic(String summary) {
        if (!summary.equals(prefs.getString("last_detection", ""))) {
            prefs.edit().putString("last_detection", summary).putLong("last_detection_at", System.currentTimeMillis()).apply();
        }
    }

    private void status(String message) {
        if (!message.equals(lastStatus)) {
            lastStatus = message;
            android.util.Log.i("AdSwipeHelper", message);
            prefs.edit().putString("status", message).apply();
        }
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { detector.reset(); }
    @Override public void onDestroy() {
        handler.removeCallbacks(poll);
        super.onDestroy();
    }
}
