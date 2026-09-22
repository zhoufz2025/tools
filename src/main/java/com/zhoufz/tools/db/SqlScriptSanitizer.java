package com.zhoufz.tools.db;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 清理 SQL 脚本中的注释，避免基线脚本里未配对的块注释结束符导致 MySQL 语法错误
 *
 * @author zhoufz
 */
public class SqlScriptSanitizer {

    private static final Charset GBK = Charset.forName("GBK");

    private SqlScriptSanitizer() {
    }

    /**
     * 读取 SQL 文件内容：优先 UTF-8，非法字节则回退 GBK（基线里部分 initdata 为 GBK）
     */
    public static String readSqlContent(Path sqlFile) throws IOException {
        byte[] bytes = Files.readAllBytes(sqlFile);
        CharsetDecoder utf8 = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return utf8.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return GBK.decode(ByteBuffer.wrap(bytes)).toString();
        }
    }

    /**
     * 是否包含 DELIMITER（存储过程脚本需原样交给 mysql 客户端）
     */
    public static boolean containsDelimiter(Path sqlFile) throws IOException {
        for (String line : readSqlContent(sqlFile).split("\n", -1)) {
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
        return stripComments(readSqlContent(sqlFile));
    }

    /**
     * 将 INSERT INTO / INSERT IGNORE INTO 统一为 INSERT IGNORE INTO，
     * 避免 initdata 写入已有 lcptpub 时因主键重复中断。
     */
    public static String rewriteInsertToIgnore(String sql) {
        if (sql == null || sql.isEmpty()) {
            return sql;
        }
        return INSERT_INTO_PATTERN.matcher(sql).replaceAll("INSERT IGNORE INTO");
    }

    private static final Pattern INSERT_INTO_PATTERN =
            Pattern.compile("(?i)\\bINSERT(?:\\s+IGNORE)?\\s+INTO\\b");
}
