package com.zhoufz.tools.service;

/**
 * 移植业务子系统范围。
 *
 * @author zhoufz
 * @date 20260705
 */
public enum TransplantBusiness {

    /** 基金代销 */
    DXFUND("基金", "dxfund"),

    /** 资管代销 */
    DXASSET("资管", "dxasset"),

    /** 信托代销 */
    DXTRUST("信托", "dxtrust"),

    /** 理财 */
    FINA("理财", "fina"),

    /** 银保 */
    INSURE("银保", "insure"),

    /** 贵金属 */
    METAL("贵金属", "metal"),

    /** 公共 */
    PUB("公共", "pub"),

    /** 组合投资 */
    HSTC("组合", "hstc");

    private final String label;
    private final String configKey;

    TransplantBusiness(String label, String configKey) {
        this.label = label;
        this.configKey = configKey;
    }

    public String getLabel() {
        return label;
    }

    public String getConfigKey() {
        return configKey;
    }

    public static TransplantBusiness fromConfigKey(String key) {
        if (key == null) {
            return null;
        }
        String normalized = key.trim().toLowerCase();
        for (TransplantBusiness business : values()) {
            if (business.configKey.equals(normalized)) {
                return business;
            }
        }
        return null;
    }
}
