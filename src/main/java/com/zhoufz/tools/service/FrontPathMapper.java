package com.zhoufz.tools.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 前端工程目录与产品目录的路径映射，以及引用别名内容改写。
 * <p>
 * 路径：产品 {@code console-{biz}-vue} ↔ 工程 {@code console-{biz}-bank-vue}。
 * <p>
 * 内容（产品 → 工程）：
 * <ul>
 *   <li>{@code @ConsoleXxxVue/views/bank/} → {@code @ConsoleXxxBankVue/views/bank/}</li>
 *   <li>{@code @ConsoleXxxVue/router/bank/} → {@code @ConsoleXxxBankVue/router/bank/}</li>
 *   <li>webpackChunkName 中 {@code console-xxx-vue/bank/} → {@code console-xxx-bank-vue/bank/}</li>
 * </ul>
 * 标准组件/api 引用 {@code @ConsoleXxxVue/components}、{@code @ConsoleXxxVue/api} 保持不变。
 *
 * @author zhoufz
 * @date 20260721
 */
public final class FrontPathMapper {

    /**
     * 匹配 console-{biz}-vue（非 bank-vue）或 console-{biz}-bank-vue。
     * group1=前缀，group2=业务简称，group3=是否已是 bank（非空则是），group4=-vue/。
     */
    private static final Pattern CONSOLE_PKG = Pattern.compile(
            "(?i)(/console-)([a-z0-9]+)(-bank)?(-vue/)");

    /** 产品别名指向 bank 视图/路由：@ConsoleDxfundVue/views/bank/ → @ConsoleDxfundBankVue/views/bank/ */
    private static final Pattern PRODUCT_ALIAS_BANK_REF = Pattern.compile(
            "@Console([A-Za-z0-9]+)(?<!Bank)Vue/(views|router)/bank/");

    /** 工程别名：@ConsoleDxfundBankVue/ → @ConsoleDxfundVue/ */
    private static final Pattern PROJECT_BANK_ALIAS = Pattern.compile(
            "@Console([A-Za-z0-9]+)BankVue/");

    /** webpackChunkName：console-dxfund-vue/bank/ → console-dxfund-bank-vue/bank/ */
    private static final Pattern PRODUCT_CHUNK_BANK = Pattern.compile(
            "(?i)console-([a-z0-9]+)-vue/bank/");

    /** webpackChunkName：console-dxfund-bank-vue → console-dxfund-vue */
    private static final Pattern PROJECT_CHUNK_BANK = Pattern.compile(
            "(?i)console-([a-z0-9]+)-bank-vue");

    private FrontPathMapper() {
    }

    /**
     * 将前端模块内相对路径按方向映射到目标侧路径。
     *
     * @param moduleRelativePath 相对 lcpt-front 模块根的路径
     * @param toProduct          true=工程→产品，false=产品→工程
     */
    public static String mapModuleRelativePath(String moduleRelativePath, boolean toProduct) {
        if (moduleRelativePath == null || moduleRelativePath.isEmpty()) {
            return moduleRelativePath;
        }
        String normalized = normalize(moduleRelativePath);
        if (toProduct) {
            return mapProjectToProduct(normalized);
        }
        return mapProductToProject(normalized);
    }

    /**
     * 产品 → 工程：{@code console-{biz}-vue/} → {@code console-{biz}-bank-vue/}
     * （已是 bank-vue 则不变）。
     */
    static String mapProductToProject(String normalized) {
        Matcher matcher = CONSOLE_PKG.matcher(normalized);
        StringBuffer sb = new StringBuffer();
        boolean found = false;
        while (matcher.find()) {
            found = true;
            String bankMarker = matcher.group(3);
            String replacement = matcher.group(1) + matcher.group(2)
                    + (bankMarker != null ? bankMarker : "-bank")
                    + matcher.group(4);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        if (!found) {
            return normalized;
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 工程 → 产品：{@code console-{biz}-bank-vue/} → {@code console-{biz}-vue/}
     * （已是标准 vue 则不变）。
     */
    static String mapProjectToProduct(String normalized) {
        Matcher matcher = CONSOLE_PKG.matcher(normalized);
        StringBuffer sb = new StringBuffer();
        boolean found = false;
        while (matcher.find()) {
            found = true;
            String bankMarker = matcher.group(3);
            String replacement;
            if (bankMarker != null) {
                replacement = matcher.group(1) + matcher.group(2) + matcher.group(4);
            } else {
                replacement = matcher.group(0);
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        if (!found) {
            return normalized;
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 改写前端源文件内容中的 webpack 别名与 chunk 名。
     *
     * @param content   源文件文本
     * @param toProduct true=工程→产品，false=产品→工程
     */
    public static String rewriteFileContent(String content, boolean toProduct) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        if (toProduct) {
            return rewriteProjectToProductContent(content);
        }
        return rewriteProductToProjectContent(content);
    }

    /**
     * 是否需要对文件做内容改写（.js / .vue / .ts / .jsx / .tsx）。
     */
    public static boolean needsContentRewrite(Path file) {
        if (file == null || file.getFileName() == null) {
            return false;
        }
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".js")
                || name.endsWith(".vue")
                || name.endsWith(".ts")
                || name.endsWith(".jsx")
                || name.endsWith(".tsx");
    }

    /**
     * 复制前端文件：文本文件按方向改写别名后再写入，其余直接二进制复制。
     *
     * @return true 表示发生了内容改写
     */
    public static boolean copyFrontFile(Path sourceFile, Path targetFile, boolean toProduct)
            throws IOException {
        Files.createDirectories(targetFile.getParent());
        if (!needsContentRewrite(sourceFile)) {
            Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
            return false;
        }
        String original = new String(Files.readAllBytes(sourceFile), StandardCharsets.UTF_8);
        String rewritten = rewriteFileContent(original, toProduct);
        Files.write(targetFile, rewritten.getBytes(StandardCharsets.UTF_8));
        return !original.equals(rewritten);
    }

    /**
     * 路径是否落在前端业务包（标准 vue 或 bank-vue）下。
     */
    public static boolean isConsoleBizPackagePath(String path) {
        if (path == null) {
            return false;
        }
        return CONSOLE_PKG.matcher(normalize(path)).find();
    }

    static String rewriteProductToProjectContent(String content) {
        String result = PRODUCT_ALIAS_BANK_REF.matcher(content)
                .replaceAll("@Console$1BankVue/$2/bank/");
        result = PRODUCT_CHUNK_BANK.matcher(result)
                .replaceAll("console-$1-bank-vue/bank/");
        return result;
    }

    static String rewriteProjectToProductContent(String content) {
        String result = PROJECT_BANK_ALIAS.matcher(content)
                .replaceAll("@Console$1Vue/");
        result = PROJECT_CHUNK_BANK.matcher(result)
                .replaceAll("console-$1-vue");
        return result;
    }

    private static String normalize(String path) {
        return path.replace('\\', '/');
    }
}
