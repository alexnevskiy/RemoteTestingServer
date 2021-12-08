import com.polytech.remotetesting.server.Server;

import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class Main {
    public static final int PORT = 61337;

    private static Server server;
    private static final Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);

    public static void main(String[] args) {
        server = new Server(PORT);
        server.start();

        while (!server.isInterrupted()) {
            readCommand();
        }
    }

    private static void readCommand() {
        if (scanner.hasNext()) {
            String text = scanner.nextLine();
            if (text.trim().length() > 0) {
                if ("-exit".equals(text)) {
                    server.closeServer();
                } else {
                    System.out.println("Неправильная команда. Введите -exit, чтобы выключить сервер.");
                }
            }
        }
    }
}
