package com.polytech.remotetesting.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class Server extends Thread {
    private final int port;

    private ServerSocket server;
    private final List<ClientThread> clientThreadList;
    private final Map<String,String> users;
    private final Map<String,String> results;

    public Server(int port) {
        this.port = port;
        this.clientThreadList = Collections.synchronizedList(new LinkedList<>());
        this.users = Collections.synchronizedMap(new HashMap<>());
        this.results = Collections.synchronizedMap(new HashMap<>());
    }

    @Override
    public void run() {
        try {
            server = new ServerSocket(port);
            while (!server.isClosed()) {
                Socket socket = server.accept();
                ClientThread clientThread = new ClientThread(socket, clientThreadList, users, results);
                clientThread.start();
                clientThreadList.add(clientThread);
            }
        } catch (IOException exception) {
            closeServer();
        }
    }

    public void closeServer() {
        for (ClientThread clientThread : clientThreadList) {
            clientThread.closeClientThread();
            try {
                clientThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        try {
            server.close();
        } catch (IOException exception) {
            exception.printStackTrace();
        }
        interrupt();
        System.out.println("Сервер выключен.");
    }
}
