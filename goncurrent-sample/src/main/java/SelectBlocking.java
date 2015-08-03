import io.github.anolivetree.goncurrent.Chan;
import io.github.anolivetree.goncurrent.Select;

public class SelectBlocking {

    static public void main(String[] args) {
        final Chan<Integer> ch1 = Chan.create(3);
        final Chan<Integer> ch2 = Chan.create(3);
        final Chan<Integer> done = Chan.create(0);

        new Thread(new Runnable() {
            @Override
            public void run() {
                for (Integer i : ch1) {
                    System.out.printf("ch1: %d\n", i);
                }
                done.send(0);
            }
        }).start();

        new Thread(new Runnable() {
            @Override
            public void run() {
                for (Integer i : ch2) {
                    System.out.printf("ch2: %d\n", i);
                }
                done.send(0);
            }
        }).start();

        Select select = new Select();
        for (int i = 0; i < 20; i++) {
            select.send(ch1, i);
            select.send(ch2, i);
            int index = select.select(); // returns the index of the channel chosen. channel is chosen randomly
            System.out.printf("sent to ch%d\n", (index + 1));
        }
        ch1.close();
        ch2.close();

        done.receive();
        done.receive();

    }
}
