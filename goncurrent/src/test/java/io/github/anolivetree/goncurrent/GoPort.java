package io.github.anolivetree.goncurrent;

import org.junit.Test;

import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;


public class GoPort {

    @Test
    public void TestChan() {
        int N = 200;
        for (int chanCap = 0; chanCap < N; chanCap++) {
            System.out.printf("%d/%d\n", chanCap, N);
            {
                // Ensure that receive from empty chan blocks.
                final Chan<Integer> c = Chan.create(chanCap);
                final AtomicBoolean recv1 = new AtomicBoolean(false);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        c.receive();
                        recv1.set(true);
                    }
                }).start();
                final AtomicBoolean recv2 = new AtomicBoolean(false);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        c.receive();
                        recv2.set(true);
                    }
                }).start();
                sleep(100);
                if (recv1.get() || recv2.get()) {
                    assertTrue(false);
                }
                // Ensure that non-blocking receive does not block.
                Select select = new Select();
                select.receive(c);
                if (select.selectNonblock() == 0) {
                    assertTrue(false);
                }
                c.send(0);
                c.send(0);
            }

            {
                // Ensure that send t ofull chan blocks.
                final Chan<Integer> c = Chan.create(chanCap);
                for (int i = 0; i < chanCap; i++) {
                    c.send(i);
                }
                final AtomicInteger sent = new AtomicInteger(0);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        c.send(0);
                        sent.set(1);
                    }
                }).start();
                sleep(100);
                if (sent.get() != 0) {
                    assertTrue(false);
                }
                // Ensure that non-blocking send does not block.
                Select select = new Select();
                select.send(c, 0);
                ;
                if (select.selectNonblock() == 0) {
                    assertTrue(false);
                }
                c.receive();
            }

            {
                // Ensure that we receive 0 from closed chan.
                Chan<Integer> c = Chan.create(chanCap);
                for (int i = 0; i < chanCap; i++) {
                    c.send(i);
                }
                c.close();
                for (int i = 0; i < chanCap; i++) {
                    int v = c.receive();
                    assertEquals(i, v);
                }
                assertEquals(null, c.receive());
                Chan.Result result = c.receiveWithResult();
                assertEquals(null, result.data);
                assertEquals(false, result.ok);
            }

            {
                //Ensure that close unblocks receive.
                final Chan<Integer> c = Chan.create(chanCap);
                final Chan<Boolean> done = Chan.create(0);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Chan.Result result = c.receiveWithResult();
                        done.send(result.data == null && result.ok == false);
                    }
                }).start();
                sleep(100);
                c.close();
                assertTrue(done.receive());
            }

            {
                // Send 100 integers.
                // ensure that we receive them non-corrupted in FIFO order.
                final Chan<Integer> c = Chan.create(chanCap);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        for (int i = 0; i < 100; i++) {
                            c.send(i);
                        }
                    }
                }).start();
                for (int i = 0; i < 100; i++) {
                    int v = c.receive();
                    assertEquals(i, v);
                }

                // Same, but using recv2.
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        for (int i = 0; i < 100; i++) {
                            c.send(i);
                        }
                    }
                }).start();
                for (int i = 0; i < 100; i++) {
                    Chan.Result result = c.receiveWithResult();
                    assertTrue(result.ok);
                    assertEquals(i, result.data);
                }

                // send 1000 integers in 4 goroutines,
                // ensure that we receive what we send.
                final int P = 4;
                final int L = 1000;
                for (int p = 0; p < P; p++) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            for (int i = 0; i < L; i++) {
                                c.send(i);
                            }
                        }
                    }).start();
                }
                final Chan<HashMap<Integer, Integer>> done = Chan.create(0);
                for (int p = 0; p < P; p++) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            HashMap<Integer, Integer> recv = new HashMap<>();
                            for (int i = 0; i < L; i++) {
                                int v = c.receive();
                                Integer prev = recv.get(v);
                                if (prev == null) {
                                    prev = Integer.valueOf(0);
                                }
                                recv.put(v, prev + 1);
                            }
                            done.send(recv);
                        }
                    }).start();
                }
                HashMap<Integer, Integer> recv = new HashMap<>();
                for (int p = 0; p < P; p++) {
                    HashMap<Integer, Integer> m = done.receive();
                    for (Integer k : m.keySet()) {
                        Integer v = m.get(k);
                        if (recv.containsKey(k)) {
                            recv.put(k, recv.get(k) + v);
                        } else {
                            recv.put(k, v);
                        }
                    }
                }
                assertEquals(L, recv.size());
                for (Integer k : recv.keySet()) {
                    Integer v = recv.get(k);
                    assertEquals(P, (int)v);
                }
            }

            {
                // Test len/cap
                Chan<Integer> c = Chan.create(chanCap);
                assertEquals(0, c.length());
                assertEquals(chanCap, c.capacity());
                for (int i = 0; i < chanCap; i++) {
                    c.send(i);
                }
                assertEquals(chanCap, c.length());
                assertEquals(chanCap, c.capacity());
            }
        }
    }

    @Test
    public void TestNonblockRecvRace() {
        int n = 10000;
        for (int i = 0; i < n; i++) {
            final Chan<Integer> c = Chan.create(1);
            c.send(1);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Select select = new Select();
                    select.receive(c);
                    assertTrue(select.selectNonblock() != -1);
                }
            }).start();
            c.close();
            c.receive();
        }
    }

    @Test
    public void TestSelfSelect() {
        // Ensure that send/recv on the same chan in select
        // does not crash not deadlock.
        int[] chanCapTable = new int[] { 0, 10 };
        for (final int chanCap : chanCapTable) {
            final Chan<Integer> c = Chan.create(chanCap);
            final Chan<Integer> done = Chan.create(2);
            for (int pp = 0; pp < 2; pp++) {
                final  int p = pp;
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        for (int i = 0; i < 1000; i++) {
                            if ((p == 0) || (i % 2 == 0)) {
                                Select select = new Select();
                                select.send(c, p);
                                select.receive(c);
                                if (select.select() == 1) {
                                    int v = (Integer)select.getData();
                                    assertFalse(chanCap == 0 && v == p);
                                }

                            } else {
                                Select select = new Select();
                                select.receive(c);
                                select.send(c, p);
                                if (select.select() == 0) {
                                    int v = (Integer)select.getData();
                                    assertFalse(chanCap == 0 && v == p);
                                }
                            }
                        }

                        done.send(0);
                    }
                }).start();

            }
            done.receive();
            done.receive();
        }
    }

    @Test
    public void TestSelectStress() {
        final Chan<Integer>[] c = new Chan[4];
        c[0] = Chan.create(0);
        c[1] = Chan.create(0);
        c[2] = Chan.create(2);
        c[3] = Chan.create(3);
        final int N = 100000;
        final CountDownLatch wg = new CountDownLatch(10);
        for (int kk = 0; kk < 4; kk++) {
            final int k = kk;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < N; i++) {
                        c[k].send(0);
                    }
                    System.out.printf("k=%d done\n", k);
                    wg.countDown();
                }
            }).start();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < N; i++) {
                        c[k].receive();
                    }
                    wg.countDown();
                    System.out.printf("k=%d done\n", k);
                }
            }).start();
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                final int[] n = new int[4];
                final Chan<Integer>[] c1 = new Chan[4];
                c1[0] = c[0];
                c1[1] = c[1];
                c1[2] = c[2];
                c1[3] = c[3];
                for (int i = 0; i < 4*N; i++) {
                    Select select = new Select();
                    select.send(c1[3], 0);
                    select.send(c1[2], 0);
                    select.send(c1[0], 0);
                    select.send(c1[1], 0);
                    switch (select.select()) {
                        case 0: {
                            n[3]++;
                            if (n[3] == N) {
                                c1[3] = null;
                            }
                            break;
                        }
                        case 1: {
                            n[2]++;
                            if (n[2] == N) {
                                c1[2] = null;
                            }
                            break;
                        }
                        case 2: {
                            n[0]++;
                            if (n[0] == N) {
                                c1[0] = null;
                            }
                            break;
                        }
                        case 3: {
                            n[1]++;
                            if (n[1] == N) {
                                c1[1] = null;
                            }
                            break;
                        }

                    }
                }
                wg.countDown();
            }
        }).start();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final int[] n = new int[4];
                final Chan<Integer>[] c1 = new Chan[4];
                c1[0] = c[0];
                c1[1] = c[1];
                c1[2] = c[2];
                c1[3] = c[3];
                for (int i = 0; i < 4*N; i++) {
                    Select select = new Select();
                    select.receive(c1[0]);
                    select.receive(c1[1]);
                    select.receive(c1[2]);
                    select.receive(c1[3]);;
                    switch (select.select()) {
                        case 0: {
                            n[0]++;
                            if (n[0] == N) {
                                c1[0] = null;
                            }
                            break;
                        }
                        case 1: {
                            n[1]++;
                            if (n[1] == N) {
                                c1[1] = null;
                            }
                            break;
                        }
                        case 2: {
                            n[2]++;
                            if (n[2] == N) {
                                c1[2] = null;
                            }
                            break;
                        }
                        case 3: {
                            n[3]++;
                            if (n[3] == N) {
                                c1[3] = null;
                            }
                            break;
                        }

                    }
                }
                wg.countDown();
            }
        }).start();
        try {
            wg.await();
        } catch (InterruptedException e) {
        }
    }

    @Test
    public void TestChanSendInterface() {
        // No equivalent test
    }

    @Test
    public void TestPseudoRandomSeed() {
        final int n = 100;
        for (int chanCap : new int[] { 0, n }) {
            final Chan<Integer> c = Chan.create(chanCap);
            final int[] l = new int[n];
            final Semaphore m = new Semaphore(1);
            try {
                m.acquire();
            } catch (InterruptedException e) {
            }
            new Thread(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < n; i++) {
                        System.out.printf("receive i=%d\n", i);
                        l[i] = c.receive();
                    }
                    m.release();
                }
            }).start();
            Select select = new Select();
            for (int i = 0; i < n; i++) {
                System.out.printf("send(select) i=%d\n", i);
                select.send(c, 1);
                select.send(c, 0);
                select.select();
            }
            try {
                m.acquire(); // wait
            } catch (InterruptedException e) {
            }
            int n0 = 0;
            int n1 = 0;
            for (int i : l) {
                n0 += (i + 1) % 2;
                n1 += i;
            }
            System.out.printf("n0=%d, n1=%d, n=%d\n", n0, n1, n);
            assertFalse(n0 <= n / 10 || n1 <= n / 10);
         }
    }

    @Test
    public void TestMultiConsumer() {
        final int nwork = 23;
        final int niter = 271828;

        final int[] pn = new int[]{2,3,7,11,13,17,19,23,27,31};

        final Chan<Integer> q = Chan.create(nwork*3);
        final Chan<Integer> r = Chan.create(nwork*3);

        // workers
        final CountDownLatch wg = new CountDownLatch(nwork);
        for (int i = 0; i < nwork; i++) {
            final int w = i;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    while (true) {
                        Integer v = q.receive();
                        if (v == null) {
                            break;
                        }
                        // mess with the fifo-ish nature of range
                        // if pn[w%len(pn0] == v {
                        //   runtime.Gosched()
                        // }
                        r.send(v);

                    }
                    wg.countDown();
                }
            }).start();
        }

        // feeder & closer
        final AtomicInteger expect = new AtomicInteger(0);
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < niter; i++) {
                    int v = pn[i%pn.length];
                    expect.addAndGet(v);
                    q.send(v);
                }
                q.close();  // no more work
                try {
                    wg.await(); // workers done
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                r.close();  // ... so there can be no more results
            }
        }).start();

        // consume & check
        int n = 0;
        int s = 0;
        while (true) {
            Integer v = r.receive();
            if (v == null) {
                break;
            }
            n++;
            s += v;
        }
        assertFalse(n != niter || s != expect.get());
    }

    @Test
    public void TestShrinkStackDuringBlockedSend() {
        // No equivalent test
    }

    @Test
    public void TestSelectDuplicateChannel() {
        // This test makes sure we can quque a G on
        // the same channel multiple times.
        final Chan<Integer> c = Chan.create(0);
        final Chan<Integer> d = Chan.create(0);
        final Chan<Integer> e = Chan.create(0);
        final Chan<Integer> f = Chan.create(0);

        // goroutine A
        new Thread(new Runnable() {
            @Override
            public void run() {
                Select select = new Select();
                select.receive(c);
                select.receive(c);
                select.receive(d);
                select.select();
                e.send(9);
            }
        }).start();
        sleep(300); // make sure goroutine A gets queued first on C

        // goroutine B
        new Thread(new Runnable() {
            @Override
            public void run() {
                c.receive();
                f.send(9);
            }
        }).start();
        sleep(300); // make sure goroutine B gets queued on c before continuing.

        d.send(7); // wake up A, it dequeues itself from c. This operation used to corrupt c.recvq.
        e.receive(); // A tells us it's done
        c.send(8); // wake up B. This operation used to fail because c.recvq was corrupted (it tries to make up an already running G instead of B)
        f.receive();
    }


    static private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
