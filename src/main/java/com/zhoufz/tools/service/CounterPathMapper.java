package com.zhoufz.tools.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 低柜工程目录与产品目录的路径映射。
 * <p>
 * 产品侧在 {@code product.counter.root} 下按子模块分仓：
 * {@code ifmcounter}、{@code ifmcounter-dxfund}、{@code ifmcounter-dxasset} 等；
 * 工程侧银行目录下仅有一层 {@code ifmcounter}，内容已扁平化（无子模块前缀）。
 * <p>
 * 产品 → 工程：剥掉首段 {@code ifmcounter} / {@code ifmcounter-*}。<br>
 * 工程 → 产品：按路径业务标记还原到对应子模块（默认 {@code ifmcounter}）。
 * <p>
 * 示例：
 * <pre>
 * 产品: ifmcounter/ifm/com/.../bank/qhyh_dxfund/sso/SignInSingleServiceImpl.java
 * 工程: ifm/com/.../bank/qhyh_dxfund/sso/SignInSingleServiceImpl.java
 * </pre>
 *
 * @author zhoufz
 * @date 20260723
 */
public final class CounterPathMapper {

    /**
     * 产品低柜子模块目录名：ifmcounter 或 ifmcounter-{biz}。
     * group1=子模块名，group2=其后相对路径。
     */
    private static final Pattern PRODUCT_SUBMODULE = Pattern.compile(
            "(?i)^(ifmcounter(?:-[a-z0-9_]+)?)/(.*)$");

    private CounterPathMapper() {
    }

    /**
     * 将低柜模块内相对路径按方向映射到目标侧路径。
     *
     * @param moduleRelativePath 相对产品 ifmcounter 根 / 工程 ifmcounter 根的路径
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
     * 产品 → 工程：{@code ifmcounter/xxx}、{@code ifmcounter-dxfund/xxx} → {@code xxx}。
     */
    static String mapProductToProject(String normalized) {
        Matcher matcher = PRODUCT_SUBMODULE.matcher(normalized);
        if (matcher.matches()) {
            return matcher.group(2);
        }
        return normalized;
    }

    /**
     * 工程 → 产品：按业务路径前缀还原子模块；已带 {@code ifmcounter*} 前缀则不变。
     */
    static String mapProjectToProduct(String normalized) {
        if (PRODUCT_SUBMODULE.matcher(normalized).matches()) {
            return normalized;
        }
        return resolveProductSubmodule(normalized) + "/" + normalized;
    }

    /**
     * 工程→产品时，若首选子模块目录不存在则回退到 {@code ifmcounter}（部分银行内容合入基座仓）。
     */
    public static Path resolveTargetPath(Path moduleRoot, String mappedRelative, boolean toProduct) {
        Path target = moduleRoot.resolve(mappedRelative.replace('/', '\\'));
        if (!toProduct) {
            return target;
        }
        String normalized = normalize(mappedRelative);
        Matcher matcher = PRODUCT_SUBMODULE.matcher(normalized);
        if (!matcher.matches()) {
            return target;
        }
        String submodule = matcher.group(1);
        if ("ifmcounter".equalsIgnoreCase(submodule)) {
            return target;
        }
        Path submoduleRoot = moduleRoot.resolve(submodule);
        if (Files.isDirectory(submoduleRoot)) {
            return target;
        }
        String fallback = "ifmcounter/" + matcher.group(2);
        return moduleRoot.resolve(fallback.replace('/', '\\'));
    }

    /**
     * 根据工程侧相对路径推断产品子模块名。
     * <p>
     * 使用与 {@link BusinessScopeMatcher} 相近的路径标记；
     * 银行简称中的 {@code _dxfund} 等不会误判（需出现 {@code /dxfund/}、{@code /dxfund-src/} 等完整段）。
     */
    static String resolveProductSubmodule(String projectRelativeNormalized) {
        String path = projectRelativeNormalized.toLowerCase(Locale.ROOT);
        if (containsAny(path, "/dxfund-src/", "/dxfund/", "\\dxfund\\")) {
            return "ifmcounter-dxfund";
        }
        if (containsAny(path, "/dxasset-src/", "/dxasset/", "/dxassetcp/", "\\dxasset\\", "\\dxassetcp\\")) {
            return "ifmcounter-dxasset";
        }
        if (containsAny(path, "/dxtrust-src/", "/dxtrust/", "\\dxtrust\\")) {
            return "ifmcounter-dxtrust";
        }
        if (containsAny(path, "/insure-src/", "/insure/", "\\insure\\")) {
            return "ifmcounter-insure";
        }
        if (containsAny(path, "/metal-src/", "/metal/", "\\metal\\")) {
            return "ifmcounter-metal";
        }
        if (containsAny(path, "/gold-src/", "/gold/", "\\gold\\")) {
            return "ifmcounter-gold";
        }
        if (containsAny(path, "/fina-src/", "/fina/", "\\fina\\")) {
            return "ifmcounter";
        }
        if (containsAny(path, "/hstc-src/", "/hstc/", "\\hstc\\")) {
            return "ifmcounter";
        }
        return "ifmcounter";
    }

    private static boolean containsAny(String path, String... markers) {
        for (String marker : markers) {
            if (path.contains(marker.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String path) {
        return path.replace('\\', '/');
    }
}
