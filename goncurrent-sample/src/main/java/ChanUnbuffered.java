import io.github.anolivetree.goncurrent.Chan;

public class ChanUnbuffered {

    static public void main(String[] args) {
        final Chan<Integer> ch1 = Chan.create(0); // unbuffered

        System.out.printf("cap=%d, len=%d\n", ch1.capacity(), ch1.length()); // prints cap=0 len=0

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

        ch1.send(0); // blocks until ch1.receive() is called

    }
}
