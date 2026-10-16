package com.zhoufz.tools.db;

import java.util.ArrayList;
import java.util.List;

/**
 * 分表视图生成结果
 *
 * @author zhoufz
 */
public class ShardingViewResult {

    private final List<String> createdViews = new ArrayList<>();

    private final List<String> skippedViews = new ArrayList<>();

    private final List<String> errors = new ArrayList<>();

    private String databases = "";

    private int shardCount;

    private long elapsedMs;

    public List<String> getCreatedViews() {
        return createdViews;
    }

    public List<String> getSkippedViews() {
        return skippedViews;
    }

    public List<String> getErrors() {
        return errors;
    }

    public String getDatabases() {
        return databases;
    }

    public void setDatabases(String databases) {
        this.databases = databases;
    }

    public int getShardCount() {
        return shardCount;
    }

    public void setShardCount(int shardCount) {
        this.shardCount = shardCount;
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

    public void addCreatedView(String view) {
        createdViews.add(view);
    }

    public void addSkippedView(String view) {
        skippedViews.add(view);
    }

    public void addError(String error) {
        errors.add(error);
    }
}
