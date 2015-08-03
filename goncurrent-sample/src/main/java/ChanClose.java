import io.github.anolivetree.goncurrent.Chan;

public class ChanClose {

    static public void main(String[] args) {
        final Chan<Integer> ch1 = Chan.create(3);

        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 10; i++) {
                    ch1.send(i);
                }
                ch1.close();
            }
        }).start();

        while (true) {
            Integer val = ch1.receive();
            if (val == null) { // receive null when channel is closed.
                break;
            }
            System.out.printf("%d\n", val);
        }
    }
}
