package com.zhoufz.tools.db;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 可单独刷新的数据库种类
 *
 * @author zhoufz
 */
public enum DbInitCategory {

    /** 公共库 lcptpub（sql-pub） */
    PUB("pub", "公共", DbInitTarget.PUB_DATABASE),

    /** 基金代销：pub 刷 lcptpub，trans 刷 dxfundtrans1/2 */
    DXFUND("dxfund", "基金代销", DbInitTarget.PUB_DATABASE + ",dxfundtrans1,dxfundtrans2"),

    /** 资管代销：pub 刷 lcptpub，trans 刷 dxassettrans1/2 */
    DXASSET("dxasset", "资管代销", DbInitTarget.PUB_DATABASE + ",dxassettrans1,dxassettrans2"),

    /** 信托代销：pub 刷 lcptpub，trans 刷 dxtrusttrans1/2 */
    DXTRUST("dxtrust", "信托代销", DbInitTarget.PUB_DATABASE + ",dxtrusttrans1,dxtrusttrans2"),

    /** 理财：pub 刷 lcptpub，trans 刷 finatrans1/2 */
    FINA("fina", "理财", DbInitTarget.PUB_DATABASE + ",finatrans1,finatrans2");

    private final String code;

    private final String label;

    private final String databaseNames;

    DbInitCategory(String code, String label, String databaseNames) {
        this.code = code;
        this.label = label;
        this.databaseNames = databaseNames;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public String getDatabaseNames() {
        return databaseNames;
    }

    public static Set<DbInitCategory> all() {
        return EnumSet.allOf(DbInitCategory.class);
    }

    public static Set<DbInitCategory> parse(Collection<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return all();
        }
        Set<DbInitCategory> result = EnumSet.noneOf(DbInitCategory.class);
        for (String code : codes) {
            if (code == null || code.trim().isEmpty()) {
                continue;
            }
            String normalized = code.trim().toLowerCase(Locale.ROOT);
            DbInitCategory category = fromCode(normalized);
            if (category == null) {
                throw new IllegalArgumentException("不支持的数据库种类: " + code
                        + "，可选值: pub,dxfund,dxasset,dxtrust,fina");
            }
            result.add(category);
        }
        if (result.isEmpty()) {
            return all();
        }
        return result;
    }

    public static Set<DbInitCategory> parseCsv(String csv) {
        if (csv == null || csv.trim().isEmpty()) {
            return all();
        }
        return parse(Arrays.asList(csv.split(",")));
    }

    public static DbInitCategory fromCode(String code) {
        for (DbInitCategory category : values()) {
            if (category.code.equals(code)) {
                return category;
            }
        }
        return null;
    }
}
