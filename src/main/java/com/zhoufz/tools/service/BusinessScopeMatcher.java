package com.zhoufz.tools.service;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * 按业务子系统（基金/资管/信托等）匹配文件路径。
 *
 * @author zhoufz
 * @date 20260705
 */
public final class BusinessScopeMatcher {

    private BusinessScopeMatcher() {
    }

    public static boolean matches(Path file, TransplantScope scope, Set<TransplantBusiness> enabledBusinesses) {
        if (enabledBusinesses == null || enabledBusinesses.isEmpty()) {
            return true;
        }
        return resolveMatchedBusiness(file, scope, enabledBusinesses) != null;
    }

    /**
     * 返回路径匹配的第一个已启用业务；无匹配返回 null。
     */
    public static TransplantBusiness resolveMatchedBusiness(Path file, TransplantScope scope,
                                                            Set<TransplantBusiness> enabledBusinesses) {
        if (enabledBusinesses == null || enabledBusinesses.isEmpty()) {
            return null;
        }
        String normalized = normalize(file);
        for (TransplantBusiness business : enabledBusinesses) {
            if (matchesBusiness(normalized, scope, business)) {
                return business;
            }
        }
        return null;
    }

    static boolean matchesBusiness(String normalizedPath, TransplantScope scope, TransplantBusiness business) {
        switch (scope) {
            case ONLINE:
                return matchesOnline(normalizedPath, business);
            case FRONT:
                return matchesFront(normalizedPath, business);
            case COUNTER:
                return matchesCounter(normalizedPath, business);
            default:
                return false;
        }
    }

    private static boolean matchesOnline(String path, TransplantBusiness business) {
        switch (business) {
            case DXFUND:
                return containsAny(path, "/lcpt-dxfund/", "/lcpt-web-manager-dxfund/", "/lcpt/dxfund/");
            case DXASSET:
                return containsAny(path, "/lcpt-dxasset/", "/lcpt-web-manager-dxasset/", "/lcpt/dxasset/");
            case DXTRUST:
                return containsAny(path, "/lcpt-dxtrust/", "/lcpt-web-manager-dxtrust/", "/lcpt/dxtrust/");
            case FINA:
                return containsAny(path, "/lcpt-fina/", "/lcpt-web-manager-fina/", "/lcpt/fina/");
            case INSURE:
                return containsAny(path, "/lcpt-insure/", "/lcpt-web-manager-insure/", "/lcpt/insure/");
            case METAL:
                return containsAny(path, "/lcpt-metal/", "/lcpt-web-manager-metal/", "/lcpt/metal/");
            case PUB:
                return containsAny(path, "/lcpt-pub/", "/lcpt-pub-common/", "/lcpt-web-bizframe/",
                        "/lcpt-web-manager-pub/", "/lcpt/pub/");
            case HSTC:
                return containsAny(path, "/lcpt-hstc/", "/lcpt-web-manager-hstc/", "/lcpt/hstc/", "/web/hstc/");
            default:
                return false;
        }
    }

    private static boolean matchesFront(String path, TransplantBusiness business) {
        // 工程侧：console-{biz}-bank-vue；产品侧：console-{biz}-vue（views/bank、router/bank 等由上层再过滤）
        switch (business) {
            case DXFUND:
                return containsAny(path, "/console-dxfund-bank-vue/", "/console-dxfund-vue/",
                        "/reconfirm/dxfund/");
            case DXASSET:
                return containsAny(path, "/console-dxasset-bank-vue/", "/console-dxasset-vue/",
                        "/reconfirm/dxasset/");
            case DXTRUST:
                return containsAny(path, "/console-dxtrust-bank-vue/", "/console-dxtrust-vue/",
                        "/reconfirm/dxtrust/");
            case FINA:
                return containsAny(path, "/console-fina-bank-vue/", "/console-fina-vue/",
                        "/reconfirm/fina/");
            case INSURE:
                return containsAny(path, "/console-insure-bank-vue/", "/console-insure-vue/",
                        "/reconfirm/insure/");
            case METAL:
                return containsAny(path, "/console-metal-bank-vue/", "/console-metal-vue/",
                        "/reconfirm/metal/");
            case PUB:
                return containsAny(path, "/console-pub-bank-vue/", "/console-pub-vue/",
                        "/reconfirm/pub/");
            case HSTC:
                return containsAny(path, "/console-hstc-bank-vue/", "/console-hstc-vue/",
                        "/reconfirm/hstc/");
            default:
                return false;
        }
    }

    private static boolean matchesCounter(String path, TransplantBusiness business) {
        switch (business) {
            case DXFUND:
                return containsAny(path, "/dxfund-src/", "/dxfund/", "\\dxfund\\", "/dxfund\\");
            case DXASSET:
                return containsAny(path, "/dxasset/", "/dxassetcp/", "\\dxasset\\", "\\dxassetcp\\");
            case DXTRUST:
                return containsAny(path, "/dxtrust/", "\\dxtrust\\");
            case FINA:
                return containsAny(path, "/fina-src/", "/fina/", "\\fina\\");
            case INSURE:
                return containsAny(path, "/insure-src/", "/insure/", "\\insure\\");
            case METAL:
                return containsAny(path, "/metal-src/", "/metal/", "\\metal\\");
            case PUB:
                return containsAny(path, "/ifmpub/", "/counter/pub/", "\\ifmpub\\", "\\counter\\pub\\");
            case HSTC:
                return containsAny(path, "/hstc/", "/hstc-src/", "\\hstc\\");
            default:
                return false;
        }
    }

    private static boolean containsAny(String path, String... markers) {
        for (String marker : markers) {
            if (path.contains(marker.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(Path file) {
        return file.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
    }
}
