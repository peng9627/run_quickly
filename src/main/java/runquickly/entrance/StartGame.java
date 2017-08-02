package runquickly.entrance;

/**
 * Author pengyi
 * Date 17-7-24.
 */

public class StartGame {

    public static void main(String[] args) {
        new Thread(new RunQuicklyTcpService()).start();
    }
}
