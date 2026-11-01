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
     * 读取 SQL 文件内容：优先 UTF-8，非法字节则回退 GBK（基线里部分 initdata 为 GBK）。
     * 自动去掉 UTF-8 BOM（U+FEFF），否则去注释后会粘在首条 CREATE 上导致语法错误。
     */
    public static String readSqlContent(Path sqlFile) throws IOException {
        byte[] bytes = Files.readAllBytes(sqlFile);
        int offset = 0;
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF) {
            offset = 3;
        }
        byte[] payload = offset == 0 ? bytes : java.util.Arrays.copyOfRange(bytes, offset, bytes.length);
        CharsetDecoder utf8 = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        String text;
        try {
            text = utf8.decode(ByteBuffer.wrap(payload)).toString();
        } catch (CharacterCodingException e) {
            text = GBK.decode(ByteBuffer.wrap(payload)).toString();
        }
        return stripBomChar(text);
    }

    /** 去掉残留的 BOM 字符（含语句中间偶发粘连） */
    public static String stripBomChar(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        if (text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        return text.replace("\uFEFF", "");
    }

    /**
     * spsql 的 *.mysql.proc.DDL.sql 通常无 DELIMITER，mysql 客户端无法直接执行。
     * 按行把 CREATE PROCEDURE … END; 包成 DELIMITER // … END //。
     */
    public static String wrapProceduresWithDelimiter(String sql) {
        if (sql == null || sql.isEmpty()) {
            return sql;
        }
        String normalized = stripBomChar(sql);
        if (containsDelimiterText(normalized)) {
            return normalized;
        }
        StringBuilder out = new StringBuilder(normalized.length() + 64);
        boolean inProcedure = false;
        for (String line : normalized.split("\n", -1)) {
            String trimmed = line.trim();
            String upper = trimmed.toUpperCase(Locale.ROOT);
            if (!inProcedure && (upper.startsWith("CREATE PROCEDURE")
                    || upper.startsWith("CREATE DEFINER"))) {
                out.append("DELIMITER //\n");
                out.append(line).append('\n');
                inProcedure = true;
                continue;
            }
            if (inProcedure && (upper.equals("END;") || upper.equals("END ;"))) {
                out.append("END //\n");
                out.append("DELIMITER ;\n");
                inProcedure = false;
                continue;
            }
            out.append(line).append('\n');
        }
        return out.toString();
    }

    private static boolean containsDelimiterText(String sql) {
        for (String line : sql.split("\n", -1)) {
            if (line.trim().toUpperCase(Locale.ROOT).startsWith("DELIMITER ")) {
                return true;
            }
        }
        return false;
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
