import local.hongguo.adhelper.CountdownDetector;

public final class CountdownDetectorTest {
    public static void main(String[] args) {
        CountdownDetector d = new CountdownDetector();
        assert !d.observe(false, 3, false, 0);
        assert !d.observe(false, 0, true, 4000);
        assert !d.observe(true, 0, true, 5000);
        d.reset();
        assert !d.observe(true, 3, false, 0);
        assert !d.observe(true, 2, false, 1000);
        assert !d.observe(true, 1, false, 2000);
        assert !d.observe(true, 0, true, 3000);
        assert d.observe(true, 0, true, 3600);
        assert !d.observe(true, 0, true, 5000);
        assert !d.observe(true, 3, false, 6000);
        assert !d.observe(false, null, false, 8000);
        assert !d.observe(true, 2, false, 9000);
        assert !d.observe(true, 1, false, 10000);
        assert !d.observe(true, null, true, 11000);
        assert d.observe(true, null, true, 11600);
        d.reset();
        assert !d.observe(true, 5, false, 0);
        assert !d.observe(true, 5, false, 30000);
        assert !d.observe(true, null, true, 31000);
        d.reset();
        assert !d.observe(true, 2, false, 0);
        assert !d.observe(true, 1, false, 1000);
        assert !d.observe(true, 30, false, 2000);
        assert !d.observe(true, null, true, 3000);
        assert !d.observe(true, null, false, 60000);
        d.reset();
        assert !d.observe(true, null, true, 0);
        assert d.observe(true, null, true, 600);
        assert !d.observe(true, null, true, 4000);
        d.reset();
        assert !d.observe(true, null, true, 0);
        assert !d.observe(true, 5, false, 400);
        assert !d.observe(true, null, false, 4000);
        d.reset();
        assert !d.observe(true, null, true, 0);
        assert !d.observe(false, null, false, 300);
        assert !d.observe(true, null, true, 500);
        assert d.observe(true, null, true, 1100);
        System.out.println("Countdown state regression checks passed.");
    }
}
