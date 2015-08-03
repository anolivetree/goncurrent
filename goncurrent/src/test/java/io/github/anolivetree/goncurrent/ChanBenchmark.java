package io.github.anolivetree.goncurrent;

import org.junit.Test;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ChanBenchmark {

    @Test
    public void benchmark_1to1() {
        final int[] depthTable = new int[] { 0, 1, 10, 100, 1000, 10000, 100000 };
        final int num = 1000000;

        for (int depth : depthTable) {
            System.out.printf("--depth %d--\n", depth);
            // channel version
            {
                Timer timer = new Timer();
                timer.start();
                benchmark_chan(depth, num, 1);
                timer.stop();
                timer.dump("ch");
            }

            // linked blocking queue
            if (depth > 0)
            {
                Timer timer = new Timer();
                timer.start();
                benchmark_linkedlist(depth, num, 1);
                timer.stop();
                timer.dump("linked");

            }
            // linked blocking queue
            if (depth > 0)
            {
                Timer timer = new Timer();
                timer.start();
                benchmark_arraylist(depth, num, 1);
                timer.stop();
                timer.dump("array");

            }
        }

    }

    @Test
    public void benchmark_1to1_multisets() {
        final int[] depthTable = new int[] { 0, 1, 10, 100, 1000, 10000 };
        final int num = 10000;
        final int sets = 100;

        for (final int depth : depthTable) {
            System.out.printf("--depth %d--\n", depth);
            // channel version
            {
                final Chan<Integer> done = Chan.create(sets);
                for (int set = 0; set < sets; set++) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            benchmark_chan(depth, num, 1);
                            done.send(0);
                        }
                    }).start();
                }
                Timer timer = new Timer();
                timer.start();
                for (int set = 0; set < sets; set++) {
                    done.receive();
                }
                timer.stop();
                timer.dump("ch");
            }

            // linked blocking queue
            if (depth > 0)
            {
                final Chan<Integer> done = Chan.create(sets);
                for (int set = 0; set < sets; set++) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            benchmark_linkedlist(depth, num, 1);
                            done.send(0);
                        }
                    }).start();
                }

                Timer timer = new Timer();
                timer.start();
                for (int set = 0; set < sets; set++) {
                    done.receive();
                }
                timer.stop();
                timer.dump("linked");

            }
            // linked blocking queue
            if (depth > 0)
            {
                final Chan<Integer> done = Chan.create(sets);
                for (int set = 0; set < sets; set++) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            benchmark_arraylist(depth, num, 1);
                            done.send(0);
                        }
                    }).start();
                }

                Timer timer = new Timer();
                timer.start();
                for (int set = 0; set < sets; set++) {
                    done.receive();
                }
                timer.stop();
                timer.dump("array");
            }
        }

    }

    @Test
    public void benchmark_Nto1() {
        final int[] depthTable = new int[] { 0, 1, 10, 100, 1000, 10000, 100000 };
        final int num = 100000;
        final int numThreads = 10;

        for (int depth : depthTable) {
            System.out.printf("--depth %d--\n", depth);
            // channel version
            {
                Timer timer = new Timer();
                timer.start();
                benchmark_chan(depth, num, numThreads);
                timer.stop();
                timer.dump("ch");
            }

            // linked blocking queue
            if (depth > 0)
            {
                Timer timer = new Timer();
                timer.start();
                benchmark_linkedlist(depth, num, numThreads);
                timer.stop();
                timer.dump("linked");

            }
            // linked blocking queue
            if (depth > 0)
            {
                Timer timer = new Timer();
                timer.start();
                benchmark_arraylist(depth, num, numThreads);
                timer.stop();
                timer.dump("array");

            }
        }

    }

    private void benchmark_chan(int depth, final int num, int numThreads) {
        final Chan<Integer> ch = Chan.create(depth);
        for (int i = 0; i < numThreads; i++) {
            Thread th = new Thread(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < num; i++) {
                        ch.send(i);
                    }
                }
            });
            th.start();
        }

        for (int i = 0; i < num * numThreads; i++) {
            ch.receive();
        }
    }

    private void benchmark_linkedlist(int depth, final int num, int numThreads) {
        final LinkedBlockingQueue<Integer> queue = new LinkedBlockingQueue<Integer>(depth);
        for (int i = 0; i < numThreads; i++) {
            Thread th = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        for (int i = 0; i < num; i++) {
                            queue.put(i);
                        }
                    } catch (Exception e) {
                    }
                }
            });
            th.start();
        }

        try {
            for (int i = 0; i < num * numThreads; i++) {
                queue.take();
            }
        } catch (Exception e) {
        }
    }

    private void benchmark_arraylist(int depth, final int num, int numThreads) {
        final ArrayBlockingQueue<Integer> queue = new ArrayBlockingQueue<Integer>(depth);
        for (int i = 0; i < numThreads; i++) {
            Thread th = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        for (int i = 0; i < num; i++) {
                            queue.put(i);
                        }
                    } catch (Exception e) {
                    }
                }
            });
            th.start();
        }

        try {
            for (int i = 0; i < num * numThreads; i++) {
                queue.take();
            }
        } catch (Exception e) {
        }

    }


}