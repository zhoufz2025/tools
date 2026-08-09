package com.zhoufz.tools.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * 联机工程目录与产品目录的路径映射。
 * <p>
 * 工程侧银行 Java 常放在 {@code *-bootstrap-adapter}，产品侧合并进 {@code *-bootstrap}；
 * {@code src/main/resources/bank/{bankCode}/} 配置两边均在 {@code *-bootstrap} 下。
 * <p>
 * 适用子系统（含但不限于）：lcpt-dxfund、lcpt-fina、lcpt-insure、lcpt-dxasset、lcpt-dxtrust 等
 * 凡模块名为 {@code *-bootstrap-adapter} 且路径含 {@code bank/{bankCode}} 的均按此规则映射。
 * lcpt-pub 的 {@code lcpt-pub-online-adapter}/{@code lcpt-pub-batch-adapter}、
 * lcpt-web {@code *-bank} 模块路径两边一致，不做 rewrite。
 *
 * @author zhoufz
 * @date 20260705
 */
public final class OnlinePathMapper {

    private OnlinePathMapper() {
    }

    /**
     * 路径中是否包含指定银行简称（bank/{code} 或 resources/bank/{code}）。
     */
    public static boolean containsBankCode(Path file, String bankCode) {
        if (bankCode == null || bankCode.trim().isEmpty()) {
            return false;
        }
        String normalized = normalize(file.toString());
        String bank = bankCode.toLowerCase(Locale.ROOT);
        return normalized.contains("/bank/" + bank + "/")
                || normalized.contains("/bank/" + bank + ".")
                || normalized.endsWith("/bank/" + bank)
                || normalized.contains("/resources/bank/" + bank + "/");
    }

    /**
     * 联机银行个性化路径：必须含银行简称，且位于 bank 包或 resources/bank 配置下。
     */
    public static boolean isOnlineBankPersonalizedPath(Path file, String bankCode) {
        if (!containsBankCode(file, bankCode)) {
            return false;
        }
        String normalized = normalize(file.toString());
        return normalized.contains("/bank/")
                || normalized.contains("/resources/bank/");
    }

    /**
     * 将模块内相对路径按方向映射到目标侧路径。
     *
     * @param moduleRelativePath 相对 lcpt-server 模块根的路径
     * @param toProduct          true=工程→产品，false=产品→工程
     * @param bankCode           银行简称
     */
    public static String mapModuleRelativePath(String moduleRelativePath, boolean toProduct, String bankCode) {
        if (moduleRelativePath == null || bankCode == null) {
            return moduleRelativePath;
        }
        String normalized = normalize(moduleRelativePath);
        if (!containsBankCodeInNormalized(normalized, bankCode)) {
            return moduleRelativePath;
        }

        if (toProduct) {
            return mapProjectToProduct(normalized);
        }
        return mapProductToProject(normalized, bankCode);
    }

    /**
     * 产品→工程时，若 adapter 模块不存在则回退到 bootstrap（部分银行工程 Java 直接在 bootstrap 下）。
     */
    public static Path resolveTargetPath(Path moduleRoot, String mappedRelative) {
        Path target = moduleRoot.resolve(mappedRelative.replace('/', '\\'));
        if (Files.exists(target.getParent())) {
            return target;
        }
        String fallback = mappedRelative.replace("-bootstrap-adapter/", "-bootstrap/");
        if (!fallback.equals(mappedRelative)) {
            Path fallbackTarget = moduleRoot.resolve(fallback.replace('/', '\\'));
            if (Files.exists(fallbackTarget.getParent())) {
                return fallbackTarget;
            }
        }
        return target;
    }

    private static String mapProjectToProduct(String normalized) {
        if (normalized.contains("-bootstrap-adapter/") && isBankContentPath(normalized)) {
            return normalized.replace("-bootstrap-adapter/", "-bootstrap/");
        }
        return normalized;
    }

    private static String mapProductToProject(String normalized, String bankCode) {
        if (!isJavaBankPath(normalized, bankCode)) {
            return normalized;
        }
        if (normalized.contains("-bootstrap/") && !normalized.contains("-bootstrap-adapter/")) {
            return normalized.replace("-bootstrap/", "-bootstrap-adapter/");
        }
        return normalized;
    }

    private static boolean isBankContentPath(String normalized) {
        return normalized.contains("/src/main/java/") || normalized.contains("/src/main/resources/bank/");
    }

    private static boolean isJavaBankPath(String normalized, String bankCode) {
        if (!normalized.contains("/src/main/java/")) {
            return false;
        }
        String bank = bankCode.toLowerCase(Locale.ROOT);
        return normalized.contains("/bank/" + bank + "/")
                || normalized.contains("/bank/" + bank + ".");
    }

    private static boolean containsBankCodeInNormalized(String normalized, String bankCode) {
        String bank = bankCode.toLowerCase(Locale.ROOT);
        return normalized.contains("/bank/" + bank + "/")
                || normalized.contains("/bank/" + bank + ".")
                || normalized.endsWith("/bank/" + bank)
                || normalized.contains("/resources/bank/" + bank + "/");
    }

    private static String normalize(String path) {
        return path.replace('\\', '/');
    }
}
