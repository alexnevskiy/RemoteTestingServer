package com.polytech.remotetesting.json;

import java.util.List;

public class Task {
    private String taskNumber;
    private String title;
    private List<String> questions;
    private List<List<String>> answers;
    private List<List<String>> rightAnswers;

    public String getTaskNumber() {
        return taskNumber;
    }

    public void setTaskNumber(String taskNumber) {
        this.taskNumber = taskNumber;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<String> getQuestions() {
        return questions;
    }

    public void setQuestions(List<String> questions) {
        this.questions = questions;
    }

    public List<List<String>> getAnswers() {
        return answers;
    }

    public void setAnswers(List<List<String>> answers) {
        this.answers = answers;
    }

    public List<List<String>> getRightAnswers() {
        return rightAnswers;
    }

    public void setRightAnswers(List<List<String>> rightAnswers) {
        this.rightAnswers = rightAnswers;
    }
}
