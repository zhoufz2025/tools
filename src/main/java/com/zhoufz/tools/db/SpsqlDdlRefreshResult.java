package com.zhoufz.tools.db;

import java.util.ArrayList;
import java.util.List;

/**
 * spsql 包 DDL 刷新结果
 *
 * @author zhoufz
 */
public class SpsqlDdlRefreshResult {

    private String packageVersion = "";

    private String modules = "";

    private boolean useLedger;

    private long elapsedMs;

    private final List<String> executedFiles = new ArrayList<>();

    private final List<String> skippedFiles = new ArrayList<>();

    private final List<String> ignoredStatements = new ArrayList<>();

    private final List<String> ledgerSkipped = new ArrayList<>();

    private final List<String> errors = new ArrayList<>();

    private int successStatements;

    private int ignoredCount;

    private int ledgerSkipCount;

    public boolean isSuccess() {
        return errors.isEmpty();
    }

    public String getPackageVersion() {
        return packageVersion;
    }

    public void setPackageVersion(String packageVersion) {
        this.packageVersion = packageVersion;
    }

    public String getModules() {
        return modules;
    }

    public void setModules(String modules) {
        this.modules = modules;
    }

    public boolean isUseLedger() {
        return useLedger;
    }

    public void setUseLedger(boolean useLedger) {
        this.useLedger = useLedger;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }

    public void setElapsedMs(long elapsedMs) {
        this.elapsedMs = elapsedMs;
    }

    public List<String> getExecutedFiles() {
        return executedFiles;
    }

    public List<String> getSkippedFiles() {
        return skippedFiles;
    }

    public List<String> getIgnoredStatements() {
        return ignoredStatements;
    }

    public List<String> getLedgerSkipped() {
        return ledgerSkipped;
    }

    public List<String> getErrors() {
        return errors;
    }

    public int getSuccessStatements() {
        return successStatements;
    }

    public void addSuccessStatement() {
        successStatements++;
    }

    public int getIgnoredCount() {
        return ignoredCount;
    }

    public void addIgnored(String detail) {
        ignoredCount++;
        if (ignoredStatements.size() < 200) {
            ignoredStatements.add(detail);
        }
    }

    public int getLedgerSkipCount() {
        return ledgerSkipCount;
    }

    public void addLedgerSkip(String detail) {
        ledgerSkipCount++;
        if (ledgerSkipped.size() < 200) {
            ledgerSkipped.add(detail);
        }
    }

    public void addExecutedFile(String file) {
        executedFiles.add(file);
    }

    public void addSkippedFile(String file) {
        skippedFiles.add(file);
    }

    public void addError(String error) {
        errors.add(error);
    }
}
