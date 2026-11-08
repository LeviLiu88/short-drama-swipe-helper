package local.hongguo.adhelper;

public final class CountdownDetector {
    private int lastSeconds = -1;
    private boolean sawPositive;
    private boolean sawDecrease;
    private boolean fired;
    private long lastAdAt = -1;
    private long lastPositiveAt;
    private long zeroAt = -1;
    private long directAt = -1;

    public boolean observe(boolean adVisible, Integer seconds, boolean ready, long now) {
        if (!adVisible) {
            directAt = -1;
            if (lastAdAt >= 0 && now - lastAdAt >= 1500) reset();
            return false;
        }
        lastAdAt = now;
        if (fired) return false;
        if (ready && seconds == null) {
            if (directAt < 0) directAt = now;
            if (now - directAt >= 600) return fire();
        } else directAt = -1;
        if (seconds != null) {
            if (seconds < 0 || seconds > 120) return false;
            if (lastSeconds >= 0 && seconds > lastSeconds + 1) {
                sawPositive = false;
                sawDecrease = false;
                zeroAt = -1;
            }
            if (seconds > 0) {
                sawPositive = true;
                lastPositiveAt = now;
                zeroAt = -1;
            }
            if (lastSeconds > seconds) sawDecrease = true;
            lastSeconds = seconds;
            if (seconds == 0 && sawPositive && sawDecrease) {
                if (zeroAt < 0) zeroAt = now;
                if (now - zeroAt >= 500) return fire();
            }
        } else if (ready && sawPositive && sawDecrease && lastSeconds <= 1
                && now - lastPositiveAt >= 1500 && now - lastPositiveAt <= 4000) {
            return fire();
        }
        return false;
    }

    private boolean fire() {
        fired = true;
        return true;
    }

    public void reset() {
        lastSeconds = -1;
        sawPositive = false;
        sawDecrease = false;
        fired = false;
        lastAdAt = -1;
        zeroAt = -1;
        directAt = -1;
    }
}
