import io.github.anolivetree.goncurrent.Chan;

public class ChanReceiveWithResult {

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
            Chan.Result<Integer> result = ch1.receiveWithResult();
            if (!result.ok) {
                break;
            }
            System.out.printf("%d\n", result.data);
        }
    }
}
