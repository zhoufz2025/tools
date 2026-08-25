package com.zhoufz.tools.db;

import java.util.ArrayList;
import java.util.List;

/**
 * 本地库初始化结果
 *
 * @author zhoufz
 */
public class LocalDbInitResult {

    private final List<String> createdDatabases = new ArrayList<>();

    private final List<String> executedScripts = new ArrayList<>();

    private final List<String> skippedScripts = new ArrayList<>();

    private final List<String> errors = new ArrayList<>();

    private String selectedCategories = "";

    private long elapsedMs;

    public List<String> getCreatedDatabases() {
        return createdDatabases;
    }

    public List<String> getExecutedScripts() {
        return executedScripts;
    }

    public List<String> getSkippedScripts() {
        return skippedScripts;
    }

    public List<String> getErrors() {
        return errors;
    }

    public String getSelectedCategories() {
        return selectedCategories;
    }

    public void setSelectedCategories(String selectedCategories) {
        this.selectedCategories = selectedCategories;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }

    public void setElapsedMs(long elapsedMs) {
        this.elapsedMs = elapsedMs;
    }

    public boolean isSuccess() {
        return errors.isEmpty();
    }

    public void addCreatedDatabase(String database) {
        createdDatabases.add(database);
    }

    public void addExecutedScript(String script) {
        executedScripts.add(script);
    }

    public void addSkippedScript(String script) {
        skippedScripts.add(script);
    }

    public void addError(String error) {
        errors.add(error);
    }
}
