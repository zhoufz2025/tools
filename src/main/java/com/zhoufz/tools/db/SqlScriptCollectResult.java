package com.zhoufz.tools.db;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * SQL 脚本收集结果
 *
 * @author zhoufz
 */
public class SqlScriptCollectResult {

    private final List<java.nio.file.Path> scripts;

    private final List<String> skippedScripts;

    public SqlScriptCollectResult(List<java.nio.file.Path> scripts, List<String> skippedScripts) {
        this.scripts = scripts;
        this.skippedScripts = skippedScripts;
    }

    public List<java.nio.file.Path> getScripts() {
        return scripts;
    }

    public List<String> getSkippedScripts() {
        return skippedScripts;
    }

    public static SqlScriptCollectResult empty() {
        return new SqlScriptCollectResult(Collections.emptyList(), Collections.emptyList());
    }

    public static SqlScriptCollectResult of(List<java.nio.file.Path> scripts, List<String> skippedScripts) {
        return new SqlScriptCollectResult(scripts, skippedScripts);
    }

    public static List<String> newSkippedList() {
        return new ArrayList<>();
    }
}
