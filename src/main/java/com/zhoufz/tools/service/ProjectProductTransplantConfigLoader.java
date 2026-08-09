package com.zhoufz.tools.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;
import java.util.Properties;
import java.util.Set;

/**
 * 从 classpath:projectProduct.properties 加载移植配置。
 *
 * @author zhoufz
 * @date 20260705
 */
public class ProjectProductTransplantConfigLoader {

    private static final String CONFIG_FILE = "projectProduct.properties";

    public static ProjectProductTransplantConfig loadFromClasspath() throws IOException {
        Properties props = new Properties();
        try (InputStream in = ProjectProductTransplantConfigLoader.class.getClassLoader()
                .getResourceAsStream(CONFIG_FILE)) {
            if (in == null) {
                throw new IOException("配置文件不存在: classpath:" + CONFIG_FILE);
            }
            props.load(in);
        }
        return fromProperties(props);
    }

    public static ProjectProductTransplantConfig fromProperties(Properties props) {
        ProjectProductTransplantConfig config = new ProjectProductTransplantConfig()
                .setProjectRoot(getRequired(props, "project.root"))
                .setProjectBankDirName(getRequired(props, "project.bank.dir.name"))
                .setBankCode(getRequired(props, "bank.code"))
                .setProductBackendRoot(getRequired(props, "product.backend.root"))
                .setProductFrontRoot(getRequired(props, "product.front.root"))
                .setProductCounterRoot(getRequired(props, "product.counter.root"));
        config.setEnabledScopes(parseEnabledScopes(props));
        config.setEnabledBusinesses(parseEnabledBusinesses(props));
        return config;
    }

    private static Set<TransplantScope> parseEnabledScopes(Properties props) {
        EnumSet<TransplantScope> scopes = EnumSet.noneOf(TransplantScope.class);
        if (parseBoolean(props, "transplant.scope.online", true)) {
            scopes.add(TransplantScope.ONLINE);
        }
        if (parseBoolean(props, "transplant.scope.front", true)) {
            scopes.add(TransplantScope.FRONT);
        }
        if (parseBoolean(props, "transplant.scope.counter", true)) {
            scopes.add(TransplantScope.COUNTER);
        }
        if (scopes.isEmpty()) {
            throw new IllegalArgumentException("transplant.scope.* 至少一项为 true");
        }
        return scopes;
    }

    /**
     * 解析业务范围：优先 {@code transplant.business.enabled} 逗号列表；否则按逐项开关（默认 true）。
     */
    private static Set<TransplantBusiness> parseEnabledBusinesses(Properties props) {
        String enabledList = props.getProperty("transplant.business.enabled");
        if (enabledList != null && !enabledList.trim().isEmpty()) {
            EnumSet<TransplantBusiness> businesses = EnumSet.noneOf(TransplantBusiness.class);
            for (String item : enabledList.split(",")) {
                TransplantBusiness business = TransplantBusiness.fromConfigKey(item);
                if (business == null) {
                    throw new IllegalArgumentException("未知业务标识: " + item.trim()
                            + "，可选: dxfund,dxasset,dxtrust,fina,insure,metal,pub,hstc");
                }
                businesses.add(business);
            }
            if (businesses.isEmpty()) {
                throw new IllegalArgumentException("transplant.business.enabled 不能为空");
            }
            return businesses;
        }

        EnumSet<TransplantBusiness> businesses = EnumSet.noneOf(TransplantBusiness.class);
        for (TransplantBusiness business : TransplantBusiness.values()) {
            if (parseBoolean(props, "transplant.business." + business.getConfigKey(), true)) {
                businesses.add(business);
            }
        }
        if (businesses.isEmpty()) {
            throw new IllegalArgumentException("transplant.business.* 至少一项为 true");
        }
        return businesses;
    }

    private static boolean parseBoolean(Properties props, String key, boolean defaultValue) {
        String value = props.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value.trim()) || "1".equals(value.trim()) || "yes".equalsIgnoreCase(value.trim());
    }

    private static String getRequired(Properties props, String key) {
        String value = props.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("配置项缺失: " + key);
        }
        return value.trim();
    }
}
