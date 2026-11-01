package com.zhoufz.tools.db;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * spsql 包模块（与 {@link DbInitCategory} 对齐，多系统可一起刷）。
 *
 * @author zhoufz
 */
public enum SpsqlModule {

    PUB("pub", "公共", "spsql-pub", "pub",
            new String[]{DbInitTarget.PUB_DATABASE}, null),

    DXFUND("dxfund", "基金代销", "spsql-dxfund", "dxfund",
            new String[]{DbInitTarget.PUB_DATABASE},
            new String[]{"dxfundtrans1", "dxfundtrans2"}),

    DXASSET("dxasset", "资管代销", "spsql-dxasset", "dxasset",
            new String[]{DbInitTarget.PUB_DATABASE},
            new String[]{"dxassettrans1", "dxassettrans2"}),

    DXTRUST("dxtrust", "信托代销", "spsql-dxtrust", "dxtrust",
            new String[]{DbInitTarget.PUB_DATABASE},
            new String[]{"dxtrusttrans1", "dxtrusttrans2"}),

    FINA("fina", "理财", "spsql-fina", "fina",
            new String[]{DbInitTarget.PUB_DATABASE},
            new String[]{"finatrans1", "finatrans2"});

    private final String code;

    private final String label;

    /** 相对 spsqlRoot 的目录名，如 spsql-fina */
    private final String dirName;

    /** 版本目录下 pub/{biz}、trans/{biz} 中的业务目录名 */
    private final String bizDir;

    private final String[] pubDatabases;

    private final String[] transDatabases;

    SpsqlModule(String code, String label, String dirName, String bizDir,
                String[] pubDatabases, String[] transDatabases) {
        this.code = code;
        this.label = label;
        this.dirName = dirName;
        this.bizDir = bizDir;
        this.pubDatabases = pubDatabases;
        this.transDatabases = transDatabases;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public String getDirName() {
        return dirName;
    }

    public String getBizDir() {
        return bizDir;
    }

    public String[] getPubDatabases() {
        return pubDatabases;
    }

    public String[] getTransDatabases() {
        return transDatabases == null ? new String[0] : transDatabases;
    }

    public static Set<SpsqlModule> all() {
        return EnumSet.allOf(SpsqlModule.class);
    }

    public static SpsqlModule fromCode(String code) {
        if (code == null) {
            return null;
        }
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        for (SpsqlModule module : values()) {
            if (module.code.equals(normalized)) {
                return module;
            }
        }
        return null;
    }

    public static Set<SpsqlModule> parseCsv(String csv) {
        if (csv == null || csv.trim().isEmpty()) {
            return all();
        }
        return parse(Arrays.asList(csv.split(",")));
    }

    public static Set<SpsqlModule> parse(Collection<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return all();
        }
        Set<SpsqlModule> result = EnumSet.noneOf(SpsqlModule.class);
        for (String code : codes) {
            if (code == null || code.trim().isEmpty()) {
                continue;
            }
            SpsqlModule module = fromCode(code);
            if (module == null) {
                throw new IllegalArgumentException("不支持的 spsql 模块: " + code
                        + "，可选: pub,dxfund,dxasset,dxtrust,fina");
            }
            result.add(module);
        }
        if (result.isEmpty()) {
            return all();
        }
        return result;
    }

    public static List<String> codesOf(Set<SpsqlModule> modules) {
        List<String> list = new ArrayList<>();
        for (SpsqlModule module : modules) {
            list.add(module.getCode());
        }
        return list;
    }
}
