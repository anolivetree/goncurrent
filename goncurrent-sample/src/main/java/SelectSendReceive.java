import io.github.anolivetree.goncurrent.Chan;
import io.github.anolivetree.goncurrent.Select;

public class SelectSendReceive {

    static public void main(String[] args) {
        final Chan<Integer> ch1 = Chan.create(3);
        final Chan<Integer> ch2 = Chan.create(3);
        final Chan<Integer> done = Chan.create(0);

        // Thread1
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 10; i++) {
                    ch1.send(i);
                }
                ch1.close();
            }
        }).start();

        // Thread2
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (Integer i : ch2) {
                    System.out.printf("ch2: %d\n", i);
                }
                done.send(0);
            }
        }).start();

        // receive data from Thread1 and forward it to Thread2
        Select select = new Select();
        Integer val = null;
        while (true) {
            if (val == null) {
                select.receive(ch1);
                select.send(null, val);
            } else {
                select.receive(null);
                select.send(ch2, val);
            }
            int index = select.select();
            if (index == 0) {
                // received from ch1
                val = (Integer) select.getData();
                if (val == null) {
                    break;
                }
            } else {
                // sent to ch2
                val = null;
            }
        }
        ch2.close();

        done.receive();

    }
}
