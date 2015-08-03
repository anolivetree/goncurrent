package io.github.anolivetree.goncurrent;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ChanTest {

    @Test
    public void basic_sendAndReceiveNonBlocking() {
        Chan<Integer> ch = Chan.create(3);
        ch.send(1);
        ch.send(2);
        ch.send(3);
        assertEquals((Integer) 1, ch.receive());
        assertEquals((Integer) 2, ch.receive());
        assertEquals((Integer) 3, ch.receive());
    }

    @Test
    public void callReceive_onEmpty_expectWait() {
        for (int depth = 0; depth < 4; depth++) {
            final Chan<Integer> ch = Chan.create(depth);
            final Chan<Integer> done = Chan.create(0);

            TestUtil.asyncSendIntLater(done, ch, 300, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9);

            Timer.assertBlockedForAround(new Runnable() {
                @Override
                public void run() {
                    int num = ch.receive();
                    assertEquals(0, num);
                }
            }, 300, 100);
            for (int i = 1; i < 10; i++) {
                int num = ch.receive();
                assertEquals(i, num);
            }
            done.receive();
        }
    }

    @Test
    public void callReceive_afterClosedWithNull_expectNull() {
        final Chan<Integer> ch = Chan.create(1);

        TestUtil.asyncSendIntegersAndClose(ch, null, 100, 200, 300);

        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        int num = ch.receive();
        assertEquals(100, num);
        num = ch.receive();
        assertEquals(200, num);
        num = ch.receive();
        assertEquals(300, num);
        assertEquals(null, ch.receive());
        assertEquals(null, ch.receive());
    }

    @Test
    public void callReceive_afterClosedWithObject_expectObject() {
        final Chan<Integer> ch = Chan.create(1);

        TestUtil.asyncSendIntegersAndClose(ch, 99, 100, 200, 300);

        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        int num = ch.receive();
        assertEquals(100, num);
        num = ch.receive();
        assertEquals(200, num);
        num = ch.receive();
        assertEquals(300, num);
        num = ch.receive();
        assertEquals(99, num);
        num = ch.receive();
        assertEquals(99, num);
    }

    @Test
    public void callSend_onFull_expectWait() {
        for (int depth = 0; depth < 4; depth++) {
            final Chan<Integer> ch = Chan.create(depth);
            final Chan<Integer> done = Chan.create(0);

            // fill the queue
            int i = 0;
            for (i = 0; i < depth; i++) {
                ch.send(i);
            }

            TestUtil.asyncReceiveIntLaterAndExpect(done, ch, 300, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9);

            final int i2 = i;
            Timer.assertBlockedForAround(new Runnable() {
                @Override
                public void run() {
                    ch.send(i2);
                }
            }, 300, 100);

            // send until 9
            for (i++; i < 10; i++) {
                ch.send(i);
            }
            done.receive();
        }
    }

    @Test
    public void callSend_afterClosed_expectException() {
        final Chan<Integer> ch = Chan.create(3);

        ch.send(1);
        ch.send(2);
        ch.send(3);
        ch.close(99);

        boolean hasException = false;
        try {
            ch.send(100);
        } catch (Exception e) {
            hasException = true;
        }
        assertTrue(hasException);

        int num = ch.receive();
        assertEquals(1, num);
        num = ch.receive();
        assertEquals(2, num);
        num = ch.receive();
        assertEquals(3, num);
        num = ch.receive();
        assertEquals(99, num);
        num = ch.receive();
        assertEquals(99, num);
    }

    @Test
    public void whileWaitingOnSend_close_expectException() {

        for (int depth = 0; depth < 3; depth++) {
            final Chan<Integer> ch = Chan.create(depth);

            // fill the channel
            for (int i = 0; i < depth; i++) {
                ch.send(i);
            }

            TestUtil.asyncClose(300, ch, null);

            TestUtil.expectException(new Runnable() {
                @Override
                public void run() {
                    //System.out.println("send 99");
                    ch.send(99);
                    //System.out.println("send done");
                }
            });
        }
    }

    @Test
    public void whileWaitingOnSend_interrupt_expectReturn() {
        final Chan<Integer> ch = Chan.create(0);
        final Chan<Integer> done = Chan.create(0);

        Thread th = new Thread(new Runnable() {
            @Override
            public void run() {
                Timer.assertBlockedForAround(new Runnable() {
                    @Override
                    public void run() {
                        ch.send(1);
                        done.send(1);
                    }
                }, 300, 100);
            }
        });
        th.start();

        TestUtil.sleep(300);

        th.interrupt();
        done.receive();
    }

    @Test
    public void whileWaitingOnReceive_closeWithNull_expectNull() {
        for (int depth = 0; depth < 3; depth++) {
            final Chan<Integer> ch = Chan.create(depth);

            TestUtil.asyncClose(300, ch, null);

            Timer.assertBlockedForAround(new Runnable() {
                @Override
                public void run() {
                    Integer num = ch.receive();
                    assertEquals(null, num);
                }
            }, 300, 100);
        }
    }

    @Test
    public void whileWaitingOnReceive_closeWithObject_expectObject() {
        for (int depth = 0; depth < 3; depth++) {
            final Chan<Integer> ch = Chan.create(depth);

            TestUtil.asyncClose(300, ch, 99);

            Timer.assertBlockedForAround(new Runnable() {
                @Override
                public void run() {
                    Integer num = ch.receive();
                    assertEquals((Integer) 99, num);
                }
            }, 300, 100);
        }
    }

    @Test
    public void whileWaitingOnReceive_interrupt_expectReturnNull() {
        final Chan<Integer> ch = Chan.create(0);
        final Chan<Integer> done = Chan.create(0);

        Thread th = new Thread(new Runnable() {
            @Override
            public void run() {
                Timer.assertBlockedForAround(new Runnable() {
                    @Override
                    public void run() {
                        Integer num = ch.receive();
                        assertEquals(null, num);
                        done.send(0);
                    }
                }, 300, 100);
            }
        });
        th.start();

        TestUtil.sleep(300);

        th.interrupt();

        done.receive();
    }

    @Test
    public void lengthCapacity() {
        final Chan<Integer> ch1 = Chan.create(0);
        final Chan<Integer> ch2 = Chan.create(3);

        assertEquals(0, ch1.capacity());
        assertEquals(0, ch1.length());
        assertEquals(3, ch2.capacity());
        assertEquals(0, ch2.length());

        // thread A
        new Thread(new Runnable() {
            @Override
            public void run() {
                ch2.send(1);
                ch1.send(0);

                ch2.send(1);
                ch2.send(1);
                ch2.send(1);
            }
        }).start();

        TestUtil.sleep(100);

        assertEquals(0, ch1.capacity());
        assertEquals(0, ch1.length()); // length is 0 even when there's a thread waiting to send.
        assertEquals(3, ch2.capacity());
        assertEquals(1, ch2.length());

        ch1.receive(); // unblock threadA

        TestUtil.sleep(100);

        assertEquals(0, ch1.capacity());
        assertEquals(0, ch1.length());
        assertEquals(3, ch2.capacity());
        assertEquals(3, ch2.length());

        ch2.receive();
        assertEquals(3, ch2.capacity());
        assertEquals(3, ch2.length());
        ch2.receive();
        assertEquals(3, ch2.capacity());
        assertEquals(2, ch2.length());
        ch2.receive();
        assertEquals(3, ch2.capacity());
        assertEquals(1, ch2.length());
        ch2.receive();
        assertEquals(3, ch2.capacity());
        assertEquals(0, ch2.length());
    }

    @Test
    public void testIterator() {
        for (int depth : new int[] { 0, 10}) {
            // for-loop
            {
                final Chan<Integer> ch1 = Chan.create(depth);

                TestUtil.asyncSleepAndSendIntegersAndClose(100, ch1, null, 1,2,3,4,5,6,7,8,9,10,11,12,13,14,15);

                int count = 1;
                for (Integer i : ch1) {
                    assertEquals((Integer)count, i);
                    count++;
                }
                assertEquals(16, count);
            }

            // use iterator directly
            {
                final Chan<Integer> ch1 = Chan.create(depth);

                TestUtil.asyncSleepAndSendIntegersAndClose(100, ch1, null, 1,2,3,4,5,6,7,8,9,10,11,12,13,14,15);

                int count = 1;
                Iterator<Integer> iter = ch1.iterator();
                while (iter.hasNext()) {
                    Integer i = iter.next();
                    assertEquals((Integer)count, i);
                    count++;
                }
                assertEquals(16, count);

            }

            // iterator breaks when interrupted
            {
                final Chan<Integer> ch1 = Chan.create(depth);
                final Chan<Integer> done = Chan.create(0);

                Thread th = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        int c = 0;
                        for (Integer i : ch1) {
                            c++;
                        }
                        done.send(c);
                    }
                });
                th.start();

                ch1.send(0);
                ch1.send(1);
                th.interrupt();
                assertEquals((Integer)2, done.receive());
            }

        }

    }

    @Test
    public void pingPong() {
        final Chan<Integer> ch = Chan.create(100);

        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 100000; i++) {
                    ch.send(i);
                }
            }
        }).start();

        for (int i = 0; i < 100000; i++) {
            int r = ch.receive();
            assertEquals(i, r);
        }
    }

    private void startGenerate(final Chan<Integer> ch) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 2;; i++) { // 2,3,4,...
                    ch.send(i);
                }
            }
        }).start();
    }

    private void startFilter(final Chan<Integer> in, final Chan<Integer> out, final int prime) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    int i = in.receive();
                    if ((i % prime) != 0) {
                        out.send(i);
                    }
                }
            }
        }).start();
    }

    // https://golang.org/doc/play/sieve.go
    @Test
    public void primeSieve() {

        int[] expected = new int[] {
                2,
                3,
                5,
                7,
                11,
                13,
                17,
                19,
                23,
                29,
        };
        ArrayList<Integer> result = new ArrayList<Integer>();

        Chan<Integer> ch = Chan.create(0);
        startGenerate(ch);
        for (int i = 0; i < 10; i++) {
            int prime = ch.receive();
            System.out.println("" + prime);
            result.add(prime);
            Chan<Integer> ch1 = Chan.create(0);
            startFilter(ch, ch1, prime);
            ch = ch1;
        }

        assertEquals(expected.length, result.size());
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], (int)result.get(i));
        }
    }

}