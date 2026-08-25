package com.zhoufz.tools.db;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * 清理 SQL 脚本中的注释，避免基线脚本里未配对的块注释结束符导致 MySQL 语法错误
 *
 * @author zhoufz
 */
public class SqlScriptSanitizer {

    private SqlScriptSanitizer() {
    }

    /**
     * 是否包含 DELIMITER（存储过程脚本需原样交给 mysql 客户端）
     */
    public static boolean containsDelimiter(Path sqlFile) throws IOException {
        for (String line : Files.readAllLines(sqlFile, StandardCharsets.UTF_8)) {
            if (line.trim().toUpperCase(Locale.ROOT).startsWith("DELIMITER ")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 去除块注释与行注释，保留可执行 SQL
     */
    public static String stripComments(String sql) {
        StringBuilder output = new StringBuilder(sql.length());
        boolean inBlockComment = false;
        boolean inLineComment = false;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;

        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';

            if (inLineComment) {
                if (ch == '\n') {
                    inLineComment = false;
                    output.append(ch);
                }
                continue;
            }

            if (inBlockComment) {
                if (ch == '*' && next == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }

            if (!inSingleQuote && !inDoubleQuote) {
                if (ch == '-' && next == '-') {
                    inLineComment = true;
                    i++;
                    continue;
                }
                if (ch == '/' && next == '*') {
                    inBlockComment = true;
                    i++;
                    continue;
                }
                // 孤立 */（块注释未配对），直接跳过
                if (ch == '*' && next == '/') {
                    i++;
                    continue;
                }
            }

            if (ch == '\'' && !inDoubleQuote) {
                if (inSingleQuote && next == '\'') {
                    output.append(ch).append(next);
                    i++;
                    continue;
                }
                inSingleQuote = !inSingleQuote;
            } else if (ch == '"' && !inSingleQuote) {
                if (inDoubleQuote && next == '"') {
                    output.append(ch).append(next);
                    i++;
                    continue;
                }
                inDoubleQuote = !inDoubleQuote;
            }

            output.append(ch);
        }

        return output.toString();
    }

    public static String readAndStripComments(Path sqlFile) throws IOException {
        String content = new String(Files.readAllBytes(sqlFile), StandardCharsets.UTF_8);
        return stripComments(content);
    }
}
