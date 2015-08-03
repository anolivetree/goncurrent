package io.github.anolivetree.goncurrent;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SelectTest {

    @Test
    public void selectReceive1() {
        final Chan<Integer> chan1 = Chan.create(3);
        Select select = new Select();
        select.receive(chan1);

        TestUtil.asyncSleepAndSendIntegers(300, chan1, 100);

        int index = select.select();
        assertEquals(0, index);
        assertEquals((Integer) select.getData(), (Integer) 100);
    }

    @Test
    public void selectReceive2() {
        final Chan<Integer> chan1 = Chan.create(3);
        final Chan<Integer> chan2 = Chan.create(3);
        Select select = new Select();
        select.receive(chan1);
        select.receive(chan2);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println("send 100 and 200");
                chan1.send(100);
                chan2.send(200);
                System.out.println("send 100 and 200 done");
            }
        }).start();

        //System.out.println("calling select");
        int index = select.select();
        //System.out.println("select returned");
        int data = (Integer)select.getData();
        //System.out.println("got " + data);
        assertEquals(true, (index == 0 && data == 100) || (index == 1 && data == 200));

        select.receive(chan1);
        select.receive(chan2);
        index = select.select();
        //System.out.println("select returned");
        data = (Integer)select.getData();
        //System.out.println("got " + data);
        assertEquals(true, (index == 0 && data == 100) || (index == 1 && data == 200));
    }

    @Test
    public void selectSend1() {
        final Chan<Integer> chan1 = Chan.create(3);
        Select select = new Select();

        select.send(chan1, 100);
        select.send(chan1, 200);
        int index = select.select();
        assertTrue(index == 0 || index == 1);
        assertEquals(select.getData(), null);

    }

    @Test
    public void channelIsSelectedRandomly() {
        Chan<Integer> ch1 = Chan.create(100);
        Chan<Float> ch2 = Chan.create(100);
        Chan<Integer> ch3 = Chan.create(100);

        for (int i = 0; i < 100; i++) {
            ch1.send(i);
            ch2.send((float)i);
        }

        Select select = new Select();
        int num1 = 0;
        int num2 = 0;
        int num3 = 0;
        for (int i = 0; i < 50; i++) {
            select.receive(ch1);
            select.receive(ch2);
            select.send(ch3, 0);
            int index = select.select();
            if (index == 0) {
                num1++;
            } else if (index == 1) {
                num2++;
            } else if (index == 2) {
                num3++;
            } else {
                System.out.printf("index=%d\n", index);
                assertTrue(false);
            }
        }
        assertTrue(num1 > 5);
        assertTrue(num2 > 5);
        assertTrue(num3 > 5);
        assertTrue(num1 + num2 + num3 == 50);
        System.out.printf("num1=%d num2=%d num3=%d\n", num1, num2, num3);
    }

    @Test
    public void waitOn_ClosedReadChannel() {
        Chan<Integer> ch1 = Chan.create(100);
        Chan<Float> ch2 = Chan.create(100);

        for (int i = 0; i < 100; i++) {
            ch1.send(i);
        }
        ch2.close((float)99);

        Select select = new Select();
        int num1 = 0;
        int num2 = 0;
        for (int i = 0; i < 50; i++) {
            select.receive(ch1);
            select.receive(ch2);
            int index = select.select();
            if (index == 0) {
                num1++;
            } else {
                num2++;
                assertEquals(Float.valueOf(99), select.getData());
            }
        }
        assertTrue(num1 > 5);
        assertTrue(num2 > 5);
        assertTrue(num1 + num2 == 50);
        System.out.printf("num1=%d num2=%d\n", num1, num2);
    }

    @Test
    public void waitOn_ClosedWriteChannel_expectException() {
        Chan<Integer> ch1 = Chan.create(100);
        Chan<Float> ch2 = Chan.create(100);

        for (int i = 0; i < 100; i++) {
            ch1.send(i);
        }
        ch2.close();

        Select select = new Select();
        int num1 = 0;
        int num2 = 0;
        for (int i = 0; i < 50; i++) {
            select.receive(ch1);
            select.send(ch2, 0);
            try {
                int index = select.select();
                if (index == 0) {
                    num1++;
                } else {
                    assertTrue(false);
                }
            } catch (Exception e) {
                num2++;
            }
        }
        assertTrue(num1 > 5);
        assertTrue(num2 > 5);
        assertTrue(num1 + num2 == 50);
        System.out.printf("num1=%d num2=%d\n", num1, num2);
    }

    @Test
    public void waitOn_NullReadChannel_expectIgnore() {
        Chan<Integer> ch1 = Chan.create(100);

        for (int i = 0; i < 100; i++) {
            ch1.send(i);
        }

        Select select = new Select();
        for (int i = 0; i < 100; i++) {
            select.receive(null);
            select.receive(ch1);
            int index = select.select();
            assertEquals(1, index);
            assertEquals((Integer) i, select.getData());
        }
    }

    @Test
    public void waitOn_NullReadChannel_expectWaitForever() {
        final Chan<Integer> done = Chan.create(0);

        final Thread th = new Thread(new Runnable() {
            @Override
            public void run() {
                final Select select = new Select();
                select.receive(null);
                int index = select.select();
                assertEquals(-1, index);
                done.close();
            }
        });
        th.start();

        new Thread(new Runnable() {
            @Override
            public void run() {
                TestUtil.sleep(300);
                th.interrupt();
            }
        }).start();

        Timer.assertBlockedForAround(new Runnable() {
            @Override
            public void run() {
                done.receive();
            }
        }, 300, 100);
    }

    @Test
    public void waitOn_NullWriteChannel_expectIgnore() {
        Chan<Integer> ch1 = Chan.create(100);

        Select select = new Select();
        for (int i = 0; i < 100; i++) {
            select.send(null, 0);
            select.send(ch1, i);
            int index = select.select();
            assertEquals(1, index);
        }

        for (int i = 0; i < 100; i++) {
            assertEquals((Integer)i, ch1.receive());
        }
    }


    @Test
    public void waitOn_NullWriteChannel_expectWaitForever() {
        final Chan<Integer> done = Chan.create(0);

        final Thread th = new Thread(new Runnable() {
            @Override
            public void run() {
                final Select select = new Select();
                select.send(null, 0);
                int index = select.select();
                assertEquals(-1, index);
                done.close();
            }
        });
        th.start();

        new Thread(new Runnable() {
            @Override
            public void run() {
                TestUtil.sleep(300);
                th.interrupt();
            }
        }).start();

        Timer.assertBlockedForAround(new Runnable() {
            @Override
            public void run() {
                done.receive();
            }
        }, 300, 100);
    }



    @Test
    public void receiveWhenQueueHasSomeData() {
        Chan<Integer> ch1 = Chan.create(100);

        for (int i = 0; i < 10; i++) {
            ch1.send(i);
        }

        Select select = new Select();
        for (int i = 0; i < 10; i++) {
            select.receive(ch1);
            int index = select.select();
            assertEquals(0, index);
            assertEquals((Integer) i, select.getData());
        }
    }

    @Test
    public void receiveWhenQueueIsFullAndSenderExists() {
        final Chan<Integer> ch1 = Chan.create(100);

        for (int i = 0; i < 100; i++) {
            ch1.send(i);
        }

        for (int i = 0; i < 3; i++) {
            final int num = i * 100;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < 100; i++) {
                        ch1.send(num);
                    }
                }
            }).start();
        }

        Select select = new Select();
        for (int i = 0; i < 100; i++) {
            select.receive(ch1);
            int index = select.select();
            assertEquals(0, index);
            assertEquals((Integer) i, select.getData());
        }

        int th1 = 0;
        int th2 = 0;
        int th3 = 0;
        for (int i = 0; i < 300; i++) {
            select.receive(ch1);
            int index = select.select();
            assertEquals(0, index);
            int data = (Integer)select.getData();
            if (data == 0) {
                th1++;
            } else if (data == 100) {
                th2++;
            } else if (data == 200) {
                th3++;
            } else {
                assertTrue(false);
            }
        }
        assertEquals(th1, 100);
        assertEquals(th2, 100);
        assertEquals(th3, 100);


    }

    @Test
    public void receiveWhenQueueIsEmptyButSenderExists() { // depth==0 is the only case
        final Chan<Integer> ch1 = Chan.create(0);

        for (int i = 0; i < 3; i++) {
            final int num = i * 100;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < 100; i++) {
                        System.out.printf("send i=%d\n", i);
                        ch1.send(num);
                    }
                }
            }).start();
        }

        Select select = new Select();
        int th1 = 0;
        int th2 = 0;
        int th3 = 0;
        for (int i = 0; i < 300; i++) {
            select.receive(ch1);
            System.out.printf("call select\n");
            int index = select.select();
            System.out.printf("selected i=%d index=%d\n", i, index);
            assertEquals(0, index);
            int data = (Integer)select.getData();
            if (data == 0) {
                th1++;
            } else if (data == 100) {
                th2++;
            } else if (data == 200) {
                th3++;
            } else {
                assertTrue(false);
            }
        }
        assertEquals(th1, 100);
        assertEquals(th2, 100);
        assertEquals(th3, 100);
    }

    @Test
    public void receiveWhenQueueIsEmptyAndNoSenderExists() {
        final Chan<Integer> ch1 = Chan.create(100);
        final Chan<Integer> done = Chan.create(0);

        TestUtil.asyncSendIntLater(done, ch1, 300, 99);

        final Select select = new Select();
        select.receive(ch1);
        Timer.assertBlockedForAround(new Runnable() {
            @Override
            public void run() {
                int index = select.select();
                assertEquals(0, index);
                assertEquals((Integer) 99, select.getData());
            }
        }, 300, 100);

        done.receive();
    }

    @Test
    public void writeWhenQueueIsNotFullAndReceiverExists() {
        // It's hard to make this situation reliably
    }

    @Test
    public void writeWhenQueueIsNotFullAndNoReceiverExists() {
        final Chan<Integer> ch1 = Chan.create(100);

        final Select select = new Select();
        for (int i = 0; i < 100; i++) {
            select.send(ch1, i);
            int index = select.select();
            assertEquals(0, index);
        }

        for (int i = 0; i < 100; i++) {
            int data = ch1.receive();
            assertEquals(i, data);
        }

    }

    @Test
    public void writeWhenQueueIsFullAndReceiverExists() { // depth==0 is the only case
        final Chan<Integer> ch1 = Chan.create(0);
        final Chan<Integer> done = Chan.create(0);

        TestUtil.asyncReceiveIntAndExpect(done, ch1, 0, 1, 2, 3);

        final Select select = new Select();
        for (int i = 0; i < 4; i++) {
            select.send(ch1, i);
            System.out.printf("send %d\n", i);
            int index = select.select();
            System.out.printf("send %d done\n", i);
            assertEquals(0, index);
        }

        done.receive();
    }

    @Test
    public void writeWhenQueueIsFullAndNoReceiverExists() {
        final Chan<Integer> ch1 = Chan.create(3);
        final Chan<Integer> done = Chan.create(0);

        ch1.send(0);
        ch1.send(1);
        ch1.send(2);

        TestUtil.asyncReceiveIntLaterAndExpect(done, ch1, 300, 0, 1, 2, 3);

        final Select select = new Select();
        Timer.assertBlockedForAround(new Runnable() {
            @Override
            public void run() {
                select.send(ch1, 3);
                int index = select.select();
                assertEquals(0, index);
            }
        }, 300, 100);

        done.receive();
    }

    @Test
    public void whileWaitingToRead_close() {
        final Chan<Integer> ch1 = Chan.create(3);

        TestUtil.asyncClose(300, ch1, null);

        final Select select = new Select();
        select.receive(ch1);
        Timer.assertBlockedForAround(new Runnable() {
            @Override
            public void run() {
                int index = select.select();
                assertEquals(0, index);
                assertEquals(null, select.getData());
            }
        }, 300, 100);
    }

    @Test
    public void whileWaitingToRead_close_multiChan() {
        final Chan<Integer> ch1 = Chan.create(3);
        final Chan<Integer> ch2 = Chan.create(3);


        // close ch1 later
        TestUtil.asyncClose(300, ch1, null);

        // send data to ch2
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (true) {
                        ch2.send(0);
                    }
                } catch (Exception e) {
                }
            }
        }).start();

        // read ch1 and ch2
        final Select select = new Select();
        final AtomicInteger count = new AtomicInteger();
        Timer.assertBlockedForAround(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    select.receive(ch1);
                    select.receive(ch2);
                    int index = select.select();
                    if (index == 0) {
                        return;
                    }
                    count.incrementAndGet();
                }
            }
        }, 300, 100);

        ch2.close();

        assertTrue(count.get() > 100);

    }

    @Test
    public void whileWaitingToWrite_close() {
        for (int i = 0; i < 10; i++) {
            final Chan<Integer> ch1 = Chan.create(3);

            TestUtil.asyncClose(300, ch1, null);

            final Select select = new Select();
            Timer.assertBlockedForAround(new Runnable() {
                @Override
                public void run() {
                    TestUtil.expectException(new Runnable() {
                        @Override
                        public void run() {
                            while (true) {
                                select.send(ch1, 0);
                                int index = select.select();
                                assertEquals(0, index);
                            }
                        }
                    });
                }
            }, 300, 100);
        }
    }

    @Test
    public void whileWaitingToRead_interrupt_expectMinusOne () {
        final Chan<Integer> ch1 = Chan.create(0);
        final Chan<Integer> done = Chan.create(0);

        Thread th = new Thread(new Runnable() {
            @Override
            public void run() {
                final Select select = new Select();
                select.receive(ch1);
                int index = select.select();
                assertEquals(-1, index);
                done.close();
            }
        });
        th.start();

        TestUtil.sleep(300);
        th.interrupt();

        done.receive();
    }

    @Test
    public void whileWaitingToWrite_interrupt () {
        final Chan<Integer> ch1 = Chan.create(0);
        final Chan<Integer> done = Chan.create(0);

        Thread th = new Thread(new Runnable() {
            @Override
            public void run() {
                final Select select = new Select();
                select.send(ch1, 0);
                int index = select.select();
                assertEquals(-1, index);
                done.close();
            }
        });
        th.start();

        TestUtil.sleep(300);
        th.interrupt();

        done.receive();
    }

    @Test
    public void addSameChannelMoreThanOne_selectSendFirst_expectRandom() {

        // select-send first
        {
            final AtomicInteger n1 = new AtomicInteger(0);
            final AtomicInteger n2 = new AtomicInteger(0);
            int n = 50;
            for (int i = 0; i < n; i++) {
                System.out.printf("i=%d\n", i);
                final Chan<Integer> ch1 = Chan.create(0);

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        TestUtil.sleep(100);
                        Integer val = ch1.receive();
                        if (val == 1) {
                            n1.incrementAndGet();
                        } else if (val == 2) {
                            n2.incrementAndGet();
                        }
                    }
                }).start();

                Select select = new Select();
                select.send(ch1, 1);
                select.send(ch1, 2);
                select.select();
            }

            if (n1.get() <= n / 10 || n2.get() <= n / 10) {
                System.out.printf("n1=%d,n2=%d,n=%d\n", n1.get(), n2.get(), n);
                assertTrue(false);
            }
        }
    }

    @Test
    public void addSameChannelMoreThanOne_selectSendLater_expectRandom() {

        // select-send later
        {
            final AtomicInteger n1 = new AtomicInteger(0);
            final AtomicInteger n2 = new AtomicInteger(0);
            int n = 50;
            for (int i = 0; i < n; i++) {
                System.out.printf("i=%d\n", i);
                final Chan<Integer> ch1 = Chan.create(0);

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Integer val = ch1.receive();
                        if (val == 1) {
                            n1.incrementAndGet();
                        } else if (val == 2) {
                            n2.incrementAndGet();
                        }
                    }
                }).start();

                TestUtil.sleep(100);

                Select select = new Select();
                select.send(ch1, 1);
                select.send(ch1, 2);
                select.select();
            }

            if (n1.get() <= n / 10 || n2.get() <= n / 10) {
                System.out.printf("n1=%d,n2=%d,n=%d\n", n1.get(), n2.get(), n);
                assertTrue(false);
            }
        }

    }

    @Test
    public void addSameChannelMoreThanOne_selectRecieveFirst_expectRandom() {
        {
            final AtomicInteger n1 = new AtomicInteger(0);
            final AtomicInteger n2 = new AtomicInteger(0);
            int n = 50;
            for (int i = 0; i < n; i++) {
                System.out.printf("i=%d\n", i);
                final Chan<Integer> ch1 = Chan.create(0);

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        TestUtil.sleep(100);
                        ch1.send(0);
                    }
                }).start();

                Select select = new Select();
                select.receive(ch1);
                select.receive(ch1);
                int index = select.select();
                if (index == 0) {
                    n1.incrementAndGet();
                } else if (index == 1) {
                    n2.incrementAndGet();
                }

            }

            if (n1.get() <= n / 10 || n2.get() <= n / 10) {
                System.out.printf("n1=%d,n2=%d,n=%d\n", n1.get(), n2.get(), n);
                assertTrue(false);
            }
        }
    }

    @Test
    public void addSameChannelMoreThanOne_selectRecieveLater_expectRandom() {
        {
            final AtomicInteger n1 = new AtomicInteger(0);
            final AtomicInteger n2 = new AtomicInteger(0);
            int n = 50;
            for (int i = 0; i < n; i++) {
                System.out.printf("i=%d\n", i);
                final Chan<Integer> ch1 = Chan.create(0);

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        ch1.send(0);
                    }
                }).start();

                TestUtil.sleep(100);

                Select select = new Select();
                select.receive(ch1);
                select.receive(ch1);
                int index = select.select();
                if (index == 0) {
                    n1.incrementAndGet();
                } else if (index == 1) {
                    n2.incrementAndGet();
                }

            }

            if (n1.get() <= n / 10 || n2.get() <= n / 10) {
                System.out.printf("n1=%d,n2=%d,n=%d\n", n1.get(), n2.get(), n);
                assertTrue(false);
            }
        }
    }

    @Test
    public void addSameChannelMoreThanOne_stress() {
        int n = 1000;

        final Chan<Integer> ch1 = Chan.create(0);
        final Chan<Integer> ch2 = Chan.create(0);
        final Chan<Integer> ch3 = Chan.create(1);

        new Thread(new Runnable() {
            @Override
            public void run() {
                Select select = new Select();
                while (true) {
                    select.receive(ch1);
                    select.receive(ch1);
                    select.send(ch2, 100);
                    select.send(ch2, 100);
                    select.receive(ch3);
                    select.receive(ch3);
                    int index = select.select();
                    if (index == 5 && select.getData() == null) {
                        return;
                    }

                }
            }
        }).start();

        TestUtil.sleep(100);

        Select select = new Select();
        for (int i = 0; i < n; i++) {
            select.send(ch1, 1);
            select.send(ch1, 2);
            select.receive(ch2);
            select.receive(ch2);
            select.send(ch3, 5);
            select.send(ch3, 6);
            select.select();
        }
        ch3.close();

    }

    @Test
    public void sendAndReceiveNullData() {
        assertTrue(false);
    }

    @Test
    public void many() {
        final Chan<Integer> chan1 = Chan.create(0);
        final Chan<Integer> chan2 = Chan.create(0);
        final Chan<Integer> chan3 = Chan.create(1);
        final Chan<Integer> chan4 = Chan.create(1);
        final Chan<Integer> chan5 = Chan.create(5);
        final Chan<Integer> chan6 = Chan.create(5);
        final Chan<Integer> chan7 = Chan.create(10);
        final Chan<Integer> chan8 = Chan.create(10);

        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    int n = chan1.receive();
                    chan2.send( n);
                }

            }
        }).start();
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    int n = chan3.receive();
                    chan4.send( n);
                }

            }
        }).start();
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    int n = chan5.receive();
                    chan6.send( n);
                }

            }
        }).start();
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    int n = chan7.receive();
                    chan8.send(n);
                }

            }
        }).start();

        Select select = new Select();
        int i = 0;
        int totalReceived = 0;
        Chan<Integer> chan1_ = chan1;
        Chan<Integer> chan2_ = chan2;
        Chan<Integer> chan3_ = chan3;
        Chan<Integer> chan4_ = chan4;
        Chan<Integer> chan5_ = chan5;
        Chan<Integer> chan6_ = chan6;
        Chan<Integer> chan7_ = chan7;
        Chan<Integer> chan8_ = chan8;
        while (true) {
            select.send(chan1_, i);
            select.receive(chan2_);
            select.send(chan3_, i);
            select.receive(chan4_);
            select.send(chan5_, i);
            select.receive(chan6_);
            select.send(chan7_, i);
            select.receive(chan8_);
            int chIndex = select.select();
            if ((chIndex % 2) == 0) {
                //System.out.printf("sent %d\n", i);
                if (i == 1000000) {
                    chan1_ = null;
                    chan3_ = null;
                    chan5_ = null;
                    chan7_ = null;
                }
                i++;
            } else {
                int r = (Integer)select.getData();
                //System.out.printf("recv %d\n", r);
                totalReceived += r;
                if (r == 1000000) {
                    break;
                }
            }
        }
    }

    @Test
    public void many_pingpong() {
        final Chan<Integer> ch1 = Chan.create(10);
        final Chan<Integer> ch2 = Chan.create(10);
        final Chan<Integer> ch3 = Chan.create(10);
        final Chan<Integer> done = Chan.create(0);

        //              x2           +1
        // th1 --ch1--> th2 --ch2--> th3 --ch3-->th1

        new Thread(new Runnable() {
            @Override
            public void run() {
                Select select = new Select();
                Integer val = null;
                while (true) {
                    select.receive(val == null ? ch1 : null);
                    select.send(val != null ? ch2 : null, val);
                    select.receive(done);
                    int index = select.select();
                    if (index == 0) {
                        val = (Integer) select.getData() * 2;
                    } else if (index == 1) {
                        val = null;
                    } else if (index == 2) {
                        return;
                    }
                }
            }
        }).start();

        new Thread(new Runnable() {
            @Override
            public void run() {
                Select select = new Select();
                Integer val = null;
                while (true) {
                    select.receive(val == null ? ch2 : null);
                    select.send(val != null ? ch3 : null, val);
                    select.receive(done);
                    int index = select.select();
                    if (index == 0) {
                        val = (Integer) select.getData() + 1;
                    } else if (index == 1) {
                        val = null;
                    } else if (index == 2) {
                        return;
                    }
                }
            }
        }).start();

        Select select = new Select();
        int sendCount = 0;
        int recvCount = 0;
        while (true) {
            select.send(ch1, sendCount);
            select.receive(ch3);
            int index = select.select();
            if (index == 0) {
                sendCount++;
            } if (index == 1) {
                int val = (Integer) select.getData();
                assertEquals(recvCount * 2 + 1, val);
                //System.out.printf("val=%d\n", val);
                recvCount++;
                if (recvCount == 10000) {
                    break;
                }
            }
        }
        done.close();


    }

}