import io.github.anolivetree.goncurrent.Chan;

public class ChanCloseTerminator {

    static public void main(String[] args) {
        final Chan<Integer> ch1 = Chan.create(3);

        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 10; i++) {
                    ch1.send(i);
                }
                ch1.close(99); // close with terminator object
            }
        }).start();

        while (true) {
            Integer val = ch1.receive();
            if (val == 99) { // receives terminator object
                break;
            }
            System.out.printf("%d\n", val);
        }

    }
}
