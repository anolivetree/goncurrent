package io.github.anolivetree.goncurrent;

import org.junit.Test;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class SelectBenchmark {

    @Test
    public void benchmarkVsConcurrentQueue() {


        final int count = 1000000;
        final int[] depthTable = new int[] { 0, 1, 10, 100, 1000, 10000, 100000 };

        for (int depth : depthTable) {
            System.out.printf("--depth %d--\n", depth);

            if (true) {
                Timer timer = new Timer();
                timer.start();
                benchmark_select(count, depth);
                timer.stop();
                timer.dump("select");
            }

            if (true) {
                Timer timer = new Timer();
                timer.start();
                benchmark_chan(count, depth);
                timer.stop();
                timer.dump("chan");
            }

            if (depth > 0) {
                Timer timer = new Timer();
                timer.start();
                benchmark_linkedlist(count, depth);
                timer.stop();
                timer.dump("linkedlist");
            }

            if (depth > 0) {
                Timer timer = new Timer();
                timer.start();
                benchmark_arraylist(count, depth);
                timer.stop();
                timer.dump("array");
            }
        }
    }

    // th1 --ch1--> th2 --ch2--> th3
    private void benchmark_select(final int count, int depth) {
        final Chan<Integer> ch1 = Chan.create(depth);
        final Chan<Integer> ch2 = Chan.create(depth);

        // th1
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < count; i++) {
                    ch1.send(i);
                }
                ch1.close();
            }
        }).start();

        // th2
        new Thread(new Runnable() {
            @Override
            public void run() {
                Select select = new Select();
                Integer val = null;
                while (true) {
                    select.receive(val == null ? ch1 : null);
                    select.send(val != null ? ch2 : null, val);
                    int index = select.select();
                    if (index == 0) {
                        Object received = select.getData();
                        if (received == null) {
                            ch2.close();
                            return;
                        }
                        val = (Integer)received;
                    } else if (index == 1) {
                        val = null;
                    }
                }
            }
        }).start();

        while (true) {
            Integer received = ch2.receive();
            if (received == count - 1) {
                break;
            }
        }
    }

    private void benchmark_chan(final int count, int depth) {
        final Chan<Integer> ch1 = Chan.create(depth);
        final Chan<Integer> ch2 = Chan.create(depth);

        // th1
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < count; i++) {
                    ch1.send(i);
                }
                ch1.close();
            }
        }).start();

        // th2
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    Object received = ch1.receive();
                    if (received == null) {
                        ch2.close();
                        return;
                    }
                    ch2.send((Integer)received);
                }
            }
        }).start();

        while (true) {
            Integer received = ch2.receive();
            if (received == count - 1) {
                break;
            }
        }
    }

    private void benchmark_linkedlist(final int count, int depth) {
        final LinkedBlockingQueue<Integer> ch1 = new LinkedBlockingQueue<Integer>(depth);
        final LinkedBlockingQueue<Integer> ch2 = new LinkedBlockingQueue<Integer>(depth);

        // th1
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    for (int i = 0; i < count; i++) {
                        ch1.put(i);
                    }
                    ch1.put(-1);
                } catch (Exception e) {
                }
            }
        }).start();

        // th2
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (true) {
                        Integer val = ch1.take();
                        ch2.put(val);
                        if (val == -1) {
                            return;
                        }
                    }
                } catch (Exception e) {
                }
            }
        }).start();

        try {
            while (true) {
                Integer received = ch2.take();
                if (received == count - 1) {
                    break;
                }
            }
        } catch (Exception e) {
        }
    }


    private void benchmark_arraylist(final int count, int depth) {
        final ArrayBlockingQueue<Integer> ch1 = new ArrayBlockingQueue<Integer>(depth);
        final ArrayBlockingQueue<Integer> ch2 = new ArrayBlockingQueue<Integer>(depth);

        // th1
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    for (int i = 0; i < count; i++) {
                        ch1.put(i);
                    }
                    ch1.put(-1);
                } catch (Exception e) {
                }
            }
        }).start();

        // th2
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (true) {
                        Integer val = ch1.take();
                        ch2.put(val);
                        if (val == -1) {
                            return;
                        }
                    }
                } catch (Exception e) {
                }
            }
        }).start();

        try {
            while (true) {
                Integer received = ch2.take();
                if (received == count - 1) {
                    break;
                }
            }
        } catch (Exception e) {
        }
    }


}