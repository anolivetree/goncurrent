package io.github.anolivetree.goncurrent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestUtil {

    static public void asyncReceiveIntAndExpect(final Chan<Integer> done, final Chan<Integer> chan, final int... expect) {
        asyncReceiveIntLaterAndExpect(done, chan, 0, expect);

    }

    static public void asyncReceiveIntLaterAndExpect(final Chan<Integer> done, final Chan<Integer> chan, final long delay, final int... expect) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                for (int i : expect) {
                    int num = chan.receive();
                    //assertEquals(1, num);
                    assertEquals(i, num);
                }
                done.send(0);

            }
        }).start();
    }

    static public void asyncSendIntLater(final Chan<Integer> done, final Chan<Integer> chan, final long delay, final int... data) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                for (int i : data) {
                    chan.send(i);
                }
                done.send(0);
            }
        }).start();
    }

    static public void sleep(long delay) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    static public void expectException(Runnable runnable) {
        boolean hasException = false;
        try {
            runnable.run();
        } catch (Exception e) {
            hasException = true;
        }
        assertTrue(hasException);
    }

    static public void asyncSendIntegers(final Chan ch, final int... nums) {
        asyncSleepAndSendIntegers(0, ch, nums);
    }

    static public void asyncSleepAndSendIntegers(final long sleep, final Chan ch, final int... nums) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(sleep);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                for (int i : nums) {
                    ch.send(i);
                }
            }
        }).start();

    }

    static public void asyncSendIntegersAndClose(final Chan ch, final Integer end, final int... nums) {
        asyncSleepAndSendIntegersAndClose(0, ch, end, nums);
    }

    static public void asyncClose(final long sleep, final Chan ch, final Integer end) {
        asyncSleepAndSendIntegersAndClose(sleep, ch, end);
    }

    static public void asyncSleepAndSendIntegersAndClose(final long sleep, final Chan ch, final Integer end, final int... nums) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(sleep);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                for (int i : nums) {
                    ch.send(i);
                }
                if (end != null) {
                    ch.close(end);
                } else {
                    //System.out.println("call close");
                    ch.close();
                    //System.out.println("call close done");
                }
            }
        }).start();

    }
}
