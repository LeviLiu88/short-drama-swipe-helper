package local.hongguo.adhelper;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class TestActivity extends Activity {
    private final Handler handler = new Handler();
    private TextView timer;
    private TextView result;
    private int seconds = 3;
    private boolean passed;
    private boolean direct;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            timer.setText(seconds > 0 ? seconds + "秒后可继续上滑观看短剧" : "上滑继续观看短剧⌃");
            if (seconds > 0) { seconds--; handler.postDelayed(this, 1000); }
        }
    };
    private final Runnable timeout = () -> {
        if (!passed) result.setText("尚未收到上滑手势。\n\n请返回主界面，确认无障碍服务和自动滑动已开启，再重新测试。");
    };

    @Override public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        direct = getIntent().getBooleanExtra("direct", false);
        TestPanel page = new TestPanel();
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(24), dp(24), dp(24), dp(24));
        page.setBackgroundColor(0xfff5f7fc);
        page.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(dp(24) + insets.getSystemWindowInsetLeft(), dp(24) + insets.getSystemWindowInsetTop(),
                    dp(24) + insets.getSystemWindowInsetRight(), dp(24) + insets.getSystemWindowInsetBottom());
            return insets;
        });
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        TextView title = text("广告演示", 22); page.addView(title);
        page.addView(text("优惠券00:29:53后失效", 14));
        page.addView(text("观看40s后可多领100金币", 14));
        timer = text("", 19); timer.setTextColor(0xff245bdd);
        result = text("请等待助手自动完成上滑。\n\n这项测试会检查倒计时识别和手势执行。请保持屏幕解锁，等待约 5 秒。", 18);
        LinearLayout.LayoutParams fill = new LinearLayout.LayoutParams(-1, 0, 1);
        fill.topMargin = dp(30); page.addView(result, fill);
        page.addView(timer);
        Button back = new Button(this); back.setText("返回主界面"); back.setTextSize(16);
        back.setOnClickListener(view -> finish()); page.addView(back, new LinearLayout.LayoutParams(-1, dp(56)));
        setContentView(page);
    }

    private final class TestPanel extends LinearLayout {
        private float downY;
        TestPanel() { super(TestActivity.this); setClickable(true); }
        @Override public boolean dispatchTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) downY = event.getY();
            if (event.getActionMasked() == MotionEvent.ACTION_UP && downY - event.getY() > dp(80) && seconds == 0 && !passed) {
                passed = true;
                handler.removeCallbacks(timeout);
                result.setText("已接收到向上滑动手势。\n\n如果你没有手动滑动，说明助手已完成倒计时识别和自动手势。\n\n真实红果广告页仍需要单独验证。");
                result.setTextColor(0xff167548);
                timer.setText("测试通过");
            }
            return super.dispatchTouchEvent(event);
        }
    }
    private TextView text(String value, int size) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size);
        view.setTextColor(0xff17253e); view.setLineSpacing(dp(5), 1); return view;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    @Override protected void onResume() {
        super.onResume(); AdHelperService.testActive = true;
        seconds = direct ? 0 : 3; passed = false;
        handler.post(tick); handler.postDelayed(timeout, 15000);
    }
    @Override protected void onPause() {
        AdHelperService.testActive = false;
        handler.removeCallbacksAndMessages(null);
        super.onPause();
    }
}
