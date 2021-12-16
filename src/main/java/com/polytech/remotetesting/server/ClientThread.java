package com.polytech.remotetesting.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.primitives.Bytes;
import com.polytech.protocol.remotetesting.model.Header;
import com.polytech.protocol.remotetesting.model.RemoteTestingMessage;
import com.polytech.protocol.remotetesting.model.ResourceRecord;
import com.polytech.protocol.remotetesting.model.Util;
import com.polytech.remotetesting.json.Task;
import org.apache.commons.lang3.ArrayUtils;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ClientThread extends Thread {
    private final Socket socket;
    private final DataInputStream inputStream;
    private final DataOutputStream outputStream;
    private final List<ClientThread> clientThreadList;
    private final Map<String,String> users;
    private final Map<String,String> results;
    private final List<Task> tasks;
    private String login;

    public ClientThread(Socket socket, List<ClientThread> clientThreadList, Map<String,String> users,
                        Map<String,String> results) throws IOException {
        this.socket = socket;
        this.inputStream = new DataInputStream(socket.getInputStream());
        this.outputStream = new DataOutputStream(socket.getOutputStream());
        this.clientThreadList = clientThreadList;
        this.users = users;
        this.results = results;
        ObjectMapper objectMapper = new ObjectMapper();
        this.tasks = objectMapper.readValue(ClientThread.class.getResourceAsStream("/tasks.json"),
                new TypeReference<>() {});
    }

    @Override
    public void run() {
        boolean isAdded = false;
        while (!isInterrupted()) {
            try {
                if (hasMessage()) {
                    RemoteTestingMessage message = readMessage();
                    if (isAdded) {
                        switch (message.getHeader().getMode()) {
                            case 1:
                                closeClientThread();
                                break;
                            case 2:
                                sendResult();
                                break;
                            case 3:
                                sendTasks();
                                break;
                            case 4:
                                int taskNumber = Integer.parseInt(
                                        message.getResourceRecords().get(0).getDataString().trim()
                                ) - 1;
                                if (taskNumber >= tasks.size()) {
                                    writeMessageWithRCode(message.getHeader().getMode(), (byte) 2);
                                } else {
                                    startTesting(taskNumber);
                                    sendResult();
                                }
                                break;
                            default:
                                writeMessageWithRCode(message.getHeader().getMode(), (byte) 4);
                                break;
                        }
                    } else {
                        isAdded = addUser(message);
                        if (isAdded) {
                            sendResult();
                            sendTasks();
                        }
                    }
                }
            } catch (IOException exception) {
                closeClientThread();
            }
        }
    }

    private boolean addUser(RemoteTestingMessage message) throws IOException {
        RemoteTestingMessage messageToUser = new RemoteTestingMessage();
        messageToUser.getHeader().setCs(true);
        if (message.getHeader().getMode() != 0) {
            messageToUser.getHeader().setRCode((byte) 4);
            writeMessage(messageToUser);
            return false;
        }
        login = message.getResourceRecords().get(0).getDataString();
        String password = message.getResourceRecords().get(1).getDataString();
        if (users.containsKey(login)) {
            if (!users.get(login).equals(password)) {
                messageToUser.getHeader().setRCode((byte) 1);
                writeMessage(messageToUser);
                return false;
            }
        } else {
            users.put(login, password);
            System.out.println("Подключился клиент: " + login);
        }
        writeMessage(messageToUser);
        return true;
    }

    private void sendResult() throws IOException {
        byte[] result = results.getOrDefault(login, "Нет результата").getBytes(StandardCharsets.UTF_8);
        RemoteTestingMessage messageToUser = new RemoteTestingMessage();
        messageToUser.getHeader().setCs(true);
        messageToUser.getHeader().setMode((byte) 2);
        messageToUser.getHeader().setRrCount(1);

        ResourceRecord resourceRecord = new ResourceRecord();
        resourceRecord.setLength((short) result.length);
        resourceRecord.setData(result);
        messageToUser.getResourceRecords().add(resourceRecord);
        writeMessage(messageToUser);
    }

    private void sendTasks() throws IOException {
        RemoteTestingMessage messageToUser = new RemoteTestingMessage();
        messageToUser.getHeader().setCs(true);
        messageToUser.getHeader().setMode((byte) 3);
        messageToUser.getHeader().setRrCount(tasks.size());
        for (Task task : tasks) {
            ResourceRecord resourceRecord = new ResourceRecord();
            resourceRecord.setLength((short) task.getTitle().getBytes(StandardCharsets.UTF_8).length);
            resourceRecord.setData(task.getTitle().getBytes(StandardCharsets.UTF_8));
            messageToUser.getResourceRecords().add(resourceRecord);
        }
        writeMessage(messageToUser);
    }

    private void startTesting(int taskNumber) throws IOException {
        Task task = tasks.get(taskNumber);
        int rightAnswersCount = 0;
        for (int i = 0; i < task.getQuestions().size(); i++) {
            String question = task.getQuestions().get(i);
            List<String> answers = task.getAnswers().get(i);
            sendQuestion(question, answers);

            List<String> rightAnswers = task.getRightAnswers().get(i);
            List<String> answersFromUser = receiveAnswer(answers.size());
            if (compareAnswers(rightAnswers, answersFromUser)) {
                rightAnswersCount++;
            }
        }
        int correctAnswers = (int) ((rightAnswersCount / (double) task.getAnswers().size()) * 100);
        results.put(login, String.valueOf(correctAnswers));
    }

    private void sendQuestion(String question, List<String> answers) throws IOException {
        RemoteTestingMessage messageToUser = new RemoteTestingMessage();
        messageToUser.getHeader().setCs(true);
        messageToUser.getHeader().setMode((byte) 5);
        messageToUser.getHeader().setRrCount(answers.size() + 1);

        ResourceRecord questionRR = new ResourceRecord();
        questionRR.setLength((short) question.getBytes(StandardCharsets.UTF_8).length);
        questionRR.setData(question.getBytes(StandardCharsets.UTF_8));
        messageToUser.getResourceRecords().add(questionRR);

        for (String answer : answers) {
            ResourceRecord answerRR = new ResourceRecord();
            answerRR.setLength((short) answer.getBytes(StandardCharsets.UTF_8).length);
            answerRR.setData(answer.getBytes(StandardCharsets.UTF_8));
            messageToUser.getResourceRecords().add(answerRR);
        }
        writeMessage(messageToUser);
    }

    private List<String> receiveAnswer(int answersNumber) throws IOException {
        boolean isCorrectMessage = false;
        List<String> answersList = new ArrayList<>();
        while (!isCorrectMessage) {
            RemoteTestingMessage message = readMessage();
            if (message.getHeader().getMode() != 5) {
                if (message.getHeader().getMode() == 1) {
                    System.out.println("Клиент " + login + " отключился");
                    closeClientThread();
                    return new ArrayList<>();
                } else {
                    writeMessageWithRCode(message.getHeader().getMode(), (byte) 4);
                    continue;
                }
            }
            boolean isCorrectAnswer = true;
            for (ResourceRecord answerRR : message.getResourceRecords()) {
                String answer = answerRR.getDataString().trim();
                if (Integer.parseInt(answer) > answersNumber || Integer.parseInt(answer) < 1) {
                    writeMessageWithRCode(message.getHeader().getMode(), (byte) 3);
                    isCorrectAnswer = false;
                    break;
                }
                answersList.add(answer);
            }
            isCorrectMessage = isCorrectAnswer;
        }
        return answersList;
    }

    private boolean compareAnswers(List<String> rightAnswers, List<String> answersFromUser) {
        Collections.sort(rightAnswers);
        Collections.sort(answersFromUser);
        return rightAnswers.equals(answersFromUser);
    }

    private boolean hasMessage() throws IOException {
        return inputStream.available() > 0;
    }

    private RemoteTestingMessage readMessage() throws IOException {
        byte[] headerBytes = inputStream.readNBytes(6);
        Header header = new Header(headerBytes);
        if (Integer.toUnsignedLong(header.getRrCount()) > 0) {
            List<Byte> resourceRecordsBytesList = new ArrayList<>(Bytes.asList(headerBytes));
            for (long i = 0; i < Integer.toUnsignedLong(header.getRrCount()); i++) {
                byte[] dataLength = inputStream.readNBytes(2);
                resourceRecordsBytesList.addAll(Bytes.asList(dataLength));

                byte[] dataBytes = inputStream.readNBytes(Short.toUnsignedInt(Util.convertToShort(dataLength)));
                resourceRecordsBytesList.addAll(Bytes.asList(dataBytes));
            }
            Byte[] bytes = resourceRecordsBytesList.toArray(new Byte[0]);
            return new RemoteTestingMessage(ArrayUtils.toPrimitive(bytes));
        }
        return new RemoteTestingMessage(headerBytes);
    }

    private void writeMessageWithRCode(byte mode, byte rCode) throws IOException {
        RemoteTestingMessage messageToUser = new RemoteTestingMessage();
        messageToUser.getHeader().setCs(true);
        messageToUser.getHeader().setMode(mode);
        messageToUser.getHeader().setRCode(rCode);
        writeMessage(messageToUser);
    }

    private void writeMessage(RemoteTestingMessage message) throws IOException {
        outputStream.write(message.getMessageBytes());
    }

    public void closeClientThread() {
        try {
            inputStream.close();
            outputStream.close();
            socket.close();
            clientThreadList.remove(this);
            interrupt();
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    public void closeClientThreadWithoutRemoving() {
        try {
            inputStream.close();
            outputStream.close();
            socket.close();
            interrupt();
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }
}
