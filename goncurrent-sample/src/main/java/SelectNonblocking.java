import io.github.anolivetree.goncurrent.Chan;
import io.github.anolivetree.goncurrent.Select;

public class SelectNonblocking {

    static public void main(String[] args) {
        final Chan<Integer> ch1 = Chan.create(2);
        final Chan<Integer> ch2 = Chan.create(2);

        Select select = new Select();
        for (int i = 0; i < 20; i++) {
            select.send(ch1, i);
            select.send(ch2, i);
            int index = select.selectNonblock();
            if (index == -1) {
                break; // when both channels are full, -1 is returned.
            }
            System.out.printf("sent to ch%d\n", (index + 1));
        }
        ch1.close();
        ch2.close();
    }
}
