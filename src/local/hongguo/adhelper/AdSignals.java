package local.hongguo.adhelper;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Signals from exposed accessibility text, without retaining page content. */
public final class AdSignals {
    private static final Pattern SECONDS = Pattern.compile("(?<![0-9])([0-9]{1,3})\\s*(?:秒|[sS])(?![A-Za-z])|(?:跳过|关闭|倒计时|剩余)\\s*[:：]?\\s*([0-9]{1,3})(?![0-9:：])");
    private static final Pattern RETURN_TIMER = Pattern.compile("(?<![0-9])([0-9]{1,3})秒后可继续上滑观看(?:短剧|剧集)");
    private static final Pattern CLOCK = Pattern.compile("(?<![0-9])0{1,2}:([0-5][0-9])(?![0-9])");
    private final Set<Integer> continuationSeconds = new HashSet<>();
    public boolean continuationReady;
    private final Set<Integer> labeled = new HashSet<>();
    private final Set<Integer> edgeSeconds = new HashSet<>();
    private final Set<Integer> bareSeconds = new HashSet<>();
    public boolean ad;
    private boolean ready;
    public boolean timerLabel;

    public void accept(CharSequence value, boolean edge, boolean countdownId) {
        if (value == null) return;
        String text = value.toString().replace('\u00a0', ' ').replace('\u200b', ' ').trim();
        if (text.isEmpty() || text.length() > 160) return;
        if (text.matches("广告[\\s·|｜,，:：0-9秒sS().（）-]*") || text.equals("广告演示")
                || text.contains("跳过广告") || text.contains("关闭广告")
                || text.startsWith("广告剩余") || text.startsWith("广告倒计时")) ad = true;
        String compact = text.replaceAll("\\s+", "");
        Matcher continuation = RETURN_TIMER.matcher(compact);
        if (continuation.find()) {
            ad = true;
            timerLabel = true;
            int seconds = Integer.parseInt(continuation.group(1));
            if (seconds <= 120) continuationSeconds.add(seconds);
            return;
        }
        if (compact.contains("上滑继续观看短剧") || compact.contains("上滑继续观看剧集")) {
            ad = true;
            continuationReady = true;
            return;
        }
        if (text.contains("金币") && (text.contains("多领") || text.contains("领取"))
                || text.contains("优惠券") && text.contains("失效")) return;
        boolean context = countdownId || text.contains("倒计时") || text.contains("跳过")
                || text.contains("后可") || text.contains("后关闭") || text.contains("剩余");
        timerLabel |= context && (countdownId || text.contains("倒计时") || text.contains("后可")
                || text.contains("后关闭") || text.contains("剩余"));
        if (text.equals("跳过") || text.equals("关闭广告") || text.equals("广告结束")
                || text.equals("继续观看") || text.contains("上滑跳过") || text.contains("滑动跳过")) ready = true;
        if (!edge && !context) return;
        Set<Integer> destination = context ? labeled : edgeSeconds;
        Matcher match = SECONDS.matcher(text);
        while (match.find()) {
            int seconds = Integer.parseInt(match.group(1) != null ? match.group(1) : match.group(2));
            if (seconds <= 120) destination.add(seconds);
        }
        match = CLOCK.matcher(text);
        while (match.find()) destination.add(Integer.parseInt(match.group(1)));
        if (text.matches("[0-9]{1,3}")) {
            int seconds = Integer.parseInt(text);
            if (seconds <= 120) (countdownId ? labeled : bareSeconds).add(seconds);
        }
    }

    public Integer seconds() {
        if (!continuationSeconds.isEmpty()) return unique(continuationSeconds);
        if (continuationReady) return null;
        if (!labeled.isEmpty()) return unique(labeled);
        if (!edgeSeconds.isEmpty()) return unique(edgeSeconds);
        if (timerLabel || ready) return unique(bareSeconds);
        return null;
    }

    private Integer unique(Set<Integer> values) {
        return values.size() == 1 ? values.iterator().next() : null;
    }

    public boolean ready() {
        if (!continuationSeconds.isEmpty()) return false;
        if (continuationReady) return true;
        return ready && !timerLabel && labeled.isEmpty() && edgeSeconds.isEmpty() && bareSeconds.isEmpty();
    }

    public boolean canTryDirectSwipe() {
        return ad && ready() && seconds() == null;
    }
}
