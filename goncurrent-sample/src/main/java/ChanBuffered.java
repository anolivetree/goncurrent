import io.github.anolivetree.goncurrent.Chan;

public class ChanBuffered {

    static public void main(String[] args) {
        final Chan<Integer> ch1 = Chan.create(3); // buffered

        System.out.printf("cap=%d, len=%d\n", ch1.capacity(), ch1.length()); // prints cap=3 len=0

        ch1.send(0);
        ch1.send(1);
        ch1.send(2);

        System.out.printf("cap=%d, len=%d\n", ch1.capacity(), ch1.length()); // prints cap=3 len=3

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                }
                ch1.receive();
            }
        }).start();

        ch1.send(3); // channel is full. blocks until ch1.receive() is called

    }
}
