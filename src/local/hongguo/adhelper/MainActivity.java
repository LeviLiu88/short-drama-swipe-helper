package local.hongguo.adhelper;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final int INK = 0xff17253e;
    private static final int BLUE = 0xff245bdd;
    private SharedPreferences prefs;
    private TextView state;
    private TextView detail;
    private TextView count;
    private Button control;
    private final Handler handler = new Handler();
    private final Runnable refresh = new Runnable() {
        @Override public void run() { render(); handler.postDelayed(this, 800); }
    };

    @Override public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        prefs = getSharedPreferences("helper", MODE_PRIVATE);
        getWindow().setStatusBarColor(0xfff5f7fc);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        scroll.setBackgroundColor(0xfff5f7fc);
        LinearLayout page = column();
        page.setPadding(dp(22), dp(22), dp(22), dp(28));
        scroll.addView(page);
        page.addView(label("短剧滑动助手", 28, INK, true));
        page.addView(label("识别广告，接着看。", 17, 0xff65738a, false));

        LinearLayout hero = column();
        hero.setPadding(dp(22), dp(20), dp(22), dp(18));
        hero.setBackground(shape(BLUE, 24));
        add(page, hero, 24);
        hero.addView(label("自动滑动", 15, 0xffd9e5ff, false));
        state = label("准备就绪", 29, Color.WHITE, true);
        hero.addView(state);
        detail = label("", 15, Color.WHITE, false);
        hero.addView(detail);
        control = button("开始自动滑动", Color.WHITE, BLUE);
        control.setOnClickListener(view -> toggle());
        add(hero, control, 18);
        count = label("", 13, 0xffd9e5ff, false);
        add(hero, count, 8);

        add(page, label("先完成授权", 20, INK, true), 26);
        add(page, label("系统授权由你确认，助手会在返回后检查状态。", 14, 0xff65738a, false), 3);
        LinearLayout setup = card();
        add(page, setup, 12);
        setup.addView(label("1  允许受限制的设置", 17, INK, true));
        setup.addView(label("如果出现“系统已拒绝授予访问权限”，先进入应用信息，查看“更多”中的“允许受限制的设置”。", 15, 0xff65738a, false));
        add(setup, action("打开应用信息", () -> launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())))), 10);
        add(setup, action("查看授权问题指引", this::guide), 4);
        add(setup, label("2  启用无障碍服务", 17, INK, true), 18);
        setup.addView(label("进入“已安装的应用”，找到短剧滑动助手，阅读系统提示后决定是否启用。", 15, 0xff65738a, false));
        add(setup, action("打开无障碍设置", () -> launch(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))), 10);

        add(page, label("验证与使用", 20, INK, true), 24);
        add(page, action("运行倒计时测试", () -> {
            if (!serviceEnabled() || !prefs.getBoolean("armed", false)) {
                toast("请先完成授权并开始自动滑动"); return;
            }
            startActivity(new Intent(this, TestActivity.class));
        }), 10);
        add(page, action("运行无读秒广告测试", () -> {
            if (!serviceEnabled() || !prefs.getBoolean("armed", false)) {
                toast("请先完成授权并开始自动滑动"); return;
            }
            startActivity(new Intent(this, TestActivity.class).putExtra("direct", true));
        }), 6);
        add(page, action("打开红果短剧", () -> {
            Intent intent = getPackageManager().getLaunchIntentForPackage(AdHelperService.TARGET);
            if (intent == null) toast("尚未找到红果官方应用"); else launch(intent);
        }), 6);
        add(page, action("查看最近一次检测诊断", this::diagnostics), 6);
        LinearLayout privacy = card();
        add(page, privacy, 22);
        privacy.addView(label("你的权限，你来掌控", 17, INK, true));
        privacy.addView(label("无障碍服务可以读取界面和模拟手势。本应用仅处理红果前台与内置测试页，状态保存在本机，安装包无联网权限。", 14, 0xff65738a, false));
        add(privacy, action("识别范围与隐私说明", this::privacy), 8);
        add(page, label("版本 1.0.2  ·  开源版本 / MIT License", 12, 0xff65738a, false), 20);
        setContentView(scroll);
    }

    private void toggle() {
        if (prefs.getBoolean("armed", false)) {
            prefs.edit().putBoolean("armed", false).putString("status", "已暂停").apply();
        } else if (!serviceEnabled()) {
            new AlertDialog.Builder(this).setTitle("先启用无障碍服务")
                    .setMessage("助手需要读取红果的广告倒计时并模拟一次向上滑动。请阅读系统权限说明后自行确认。遇到系统拒绝时，可查看主界面的授权指引。")
                    .setPositiveButton("前往系统设置", (dialog, which) -> launch(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                    .setNegativeButton("暂不开启", null).show();
        } else {
            prefs.edit().putBoolean("armed", true).apply();
        }
        render();
    }

    private void diagnostics() {
        long at = prefs.getLong("last_detection_at", 0);
        String time = at == 0 ? "尚未读取到目标页面" : "最近检测变化 " + android.text.format.DateFormat.format("MM-dd HH:mm:ss", at);
        String report = time + "\n\n" + prefs.getString("last_detection", "请开启自动滑动，进入红果广告页，再返回这里查看。")
                + "\n\n广告标记为 false，说明当前规则未识别广告；文字节点为 0 或窗口为空，说明界面未提供可读取文字。倒计时为 null 表示秒数无法确认。"
                + "\n\n本诊断仅保存节点数量和判定结果，不保存页面原文。请将此页截图与广告录屏一起反馈。";
        new AlertDialog.Builder(this).setTitle("本机检测诊断").setMessage(report).setPositiveButton("关闭", null).show();
    }

    private void guide() {
        new AlertDialog.Builder(this).setTitle("系统拒绝授权怎么办")
                .setMessage("Android 13 及以上版本可能对侧载应用的无障碍权限加以限制。\n\n1. 返回主界面，点击“打开应用信息”。\n2. 查看右上角更多菜单，若有“允许受限制的设置”，阅读提示后自行决定是否允许。\n3. 返回无障碍设置，在“已安装的应用”中启用短剧滑动助手。\n\n不同品牌的入口位置可能不同。如果没有该选项，或仍被拒绝，可能涉及系统保护、企业管理或安装来源限制。请保留系统版本和提示截图，咨询设备厂商或管理员。\n\n应用无法代替你授予权限。只有在信任代码和安装来源时才授权。")
                .setPositiveButton("打开应用信息", (dialog, which) -> launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))))
                .setNegativeButton("关闭", null).show();
    }

    private void privacy() {
        new AlertDialog.Builder(this).setTitle("识别范围与隐私")
                .setMessage("识别条件\n识别到“上滑继续观看短剧”等明确提示时自动上滑。有读秒时等待秒数递减并确认结束。图片或视频中的文字无法通过当前方式读取，多段广告可能需要继续等待。\n\n权限与数据\n无障碍具有读取界面和模拟手势的能力。代码仅检查红果及内置测试页，日志只记录状态，未保存页面原文。累计次数包含模拟测试。没有账号、联网权限或第三方统计 SDK。\n\n停止使用\n点击暂停会停止后续判断。彻底撤销访问，请到系统无障碍设置中关闭本服务。手势一旦已交给系统，可能仍会完成。\n\n兼容性\n内置测试已在模拟器验证。真实红果广告及各品牌真机仍需实测，滑动后的页面由红果决定。")
                .setPositiveButton("知道了", null).show();
    }

    private void launch(Intent intent) {
        try { startActivity(intent); }
        catch (ActivityNotFoundException | SecurityException error) {
            new AlertDialog.Builder(this).setTitle("请手动打开系统设置")
                    .setMessage("系统未允许打开此页面。请从手机设置进入“应用 → 短剧滑动助手”查看应用信息，或进入“无障碍 → 已安装的应用”查找本服务。")
                    .setPositiveButton("知道了", null).show();
        }
    }

    private boolean serviceEnabled() {
        AccessibilityManager manager = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        for (AccessibilityServiceInfo info : manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            android.content.pm.ServiceInfo service = info.getResolveInfo().serviceInfo;
            if (getPackageName().equals(service.packageName) && AdHelperService.class.getName().equals(service.name)) return true;
        }
        return false;
    }

    private void render() {
        boolean connected = serviceEnabled();
        boolean armed = prefs.getBoolean("armed", false);
        state.setText(!connected ? "等待授权" : armed ? "自动滑动已开启" : "已暂停");
        detail.setText(!connected ? "完成下方两步，授权后即可开始。" : armed ? prefs.getString("status", "等待红果前台页面") : "开启后识别广告，有读秒时等待结束。");
        control.setText(armed ? "暂停自动滑动" : connected ? "开始自动滑动" : "前往授权");
        count.setText("累计上滑 " + prefs.getInt("swipes", 0) + " 次，包含内置测试");
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }
    private LinearLayout card() {
        LinearLayout layout = column();
        layout.setPadding(dp(18), dp(16), dp(18), dp(16));
        layout.setBackground(shape(Color.WHITE, 16));
        return layout;
    }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color); drawable.setCornerRadius(dp(radius)); return drawable;
    }
    private TextView label(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setFontFeatureSettings("kern");
        view.setLineSpacing(dp(4), 1);
        view.setPadding(0, dp(4), 0, dp(4));
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }
    private Button button(String title, int background, int foreground) {
        Button button = new Button(this);
        button.setText(title); button.setTextSize(16); button.setAllCaps(false);
        button.setTextColor(foreground);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x30245bdd), shape(background, 12), shape(Color.WHITE, 12)));
        button.setMinHeight(dp(50)); button.setPadding(dp(14), dp(10), dp(14), dp(10));
        button.setStateListAnimator(null);
        return button;
    }
    private Button action(String title, Runnable action) {
        Button button = button(title, 0xffe8efff, BLUE);
        button.setOnClickListener(view -> action.run()); return button;
    }
    private void add(LinearLayout parent, View child, int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(top); parent.addView(child, params);
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    @Override protected void onResume() { super.onResume(); handler.post(refresh); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
}
