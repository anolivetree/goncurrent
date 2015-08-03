package io.github.anolivetree.goncurrent;

import static org.junit.Assert.assertTrue;

class Timer {

    private long t1;
    private long t2;

    public void start() {
        t1 = System.currentTimeMillis();
    }

    public void stop() {
        t2 = System.currentTimeMillis();
    }

    public void assertAround(long t, long delta) {
        long d = t2 - t1;
        assertTrue(d >= t - delta && d <= t + delta);
    }

    public void dump(String label) {
        System.out.printf("%10s: %dms\n", label, (t2-t1));
    }

    static public void assertBlockedForAround(Runnable runnable, long t, long delta) {
        Timer timer = new Timer();
        timer.start();
        runnable.run();
        timer.stop();
        timer.assertAround(t, delta);
    }
}
