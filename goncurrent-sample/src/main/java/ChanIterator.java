import io.github.anolivetree.goncurrent.Chan;

public class ChanIterator {

    static public void main(String[] args) {
        final Chan<Integer> ch1 = Chan.create(0);

        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 10; i++) {
                    ch1.send(i);
                }
                ch1.close();
            }
        }).start();

        // read until it closes.
        for (Integer i : ch1) {
            System.out.printf("%d\n", i);
        }

    }
}
