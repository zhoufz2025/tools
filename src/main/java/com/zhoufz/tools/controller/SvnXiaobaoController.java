package com.zhoufz.tools.controller;

import com.zhoufz.tools.dto.Result;
import com.zhoufz.tools.svn.SvnXiaobaoService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * SVN 小包拉包 API。
 *
 * @author zhoufz
 */
@RestController
public class SvnXiaobaoController {

    private final SvnXiaobaoService svnXiaobaoService;

    public SvnXiaobaoController(SvnXiaobaoService svnXiaobaoService) {
        this.svnXiaobaoService = svnXiaobaoService;
    }

    /** 默认本地根、可选业务/前端清单 */
    @RequestMapping("/hundsun/svn/xiaobao/presets")
    public Result<Map<String, Object>> presets() {
        return Result.success("ok", svnXiaobaoService.presets());
    }

    /**
     * 按完整分支名列出可拉的 spsql 脚本版本（同短名系列，如 202405.05.*）。
     */
    @RequestMapping("/hundsun/svn/xiaobao/spsqlVersions")
    public Result<Map<String, Object>> spsqlVersions(@RequestParam("branch") String branch) {
        try {
            Map<String, Object> data = svnXiaobaoService.listSpsqlVersions(branch);
            @SuppressWarnings("unchecked")
            List<String> versions = (List<String>) data.get("versions");
            int size = versions == null ? 0 : versions.size();
            return Result.success("短名 " + data.get("shortName") + "，共 " + size + " 个脚本版本", data);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("列出脚本版本失败：" + e.getMessage());
        }
    }

    /** 预览将 checkout 的 URL → 本地路径 */
    @RequestMapping("/hundsun/svn/xiaobao/preview")
    public Result<List<Map<String, String>>> preview(
            @RequestParam("branch") String branch,
            @RequestParam(value = "localRoot", required = false) String localRoot,
            @RequestParam(value = "modules", required = false) String modules,
            @RequestParam(value = "spsqlVersions", required = false) String spsqlVersions,
            @RequestParam(value = "frontModules", required = false) String frontModules,
            @RequestParam(value = "includeCounter", required = false, defaultValue = "false") boolean includeCounter) {
        try {
            List<Map<String, String>> rows = svnXiaobaoService.preview(
                    branch,
                    localRoot,
                    SvnXiaobaoService.splitCsv(modules),
                    SvnXiaobaoService.splitCsv(spsqlVersions),
                    SvnXiaobaoService.splitCsv(frontModules),
                    includeCounter);
            String shortName = svnXiaobaoService.shortName(branch);
            return Result.success("短名 " + shortName + "，共 " + rows.size() + " 项", rows);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("预览失败：" + e.getMessage());
        }
    }

    /**
     * 执行 checkout；本地已是同 URL 工作副本则 update。
     * 耗时可能较长，前端超时已放宽。
     */
    @RequestMapping("/hundsun/svn/xiaobao/checkout")
    public Result<Map<String, Object>> checkout(
            @RequestParam("branch") String branch,
            @RequestParam(value = "localRoot", required = false) String localRoot,
            @RequestParam(value = "modules", required = false) String modules,
            @RequestParam(value = "spsqlVersions", required = false) String spsqlVersions,
            @RequestParam(value = "frontModules", required = false) String frontModules,
            @RequestParam(value = "includeCounter", required = false, defaultValue = "false") boolean includeCounter) {
        try {
            Map<String, Object> data = svnXiaobaoService.checkout(
                    branch,
                    localRoot,
                    SvnXiaobaoService.splitCsv(modules),
                    SvnXiaobaoService.splitCsv(spsqlVersions),
                    SvnXiaobaoService.splitCsv(frontModules),
                    includeCounter);
            int ok = (Integer) data.get("ok");
            int fail = (Integer) data.get("fail");
            String msg = "拉包完成：成功 " + ok + "，失败 " + fail + "（短名 " + data.get("shortName") + "）";
            if (fail > 0) {
                Result<Map<String, Object>> error = Result.error(msg + "，详见控制台与返回明细");
                error.setData(data);
                return error;
            }
            return Result.success(msg, data);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("拉包失败：" + e.getMessage());
        }
    }

    /** 扫描本地已拉的工作副本 */
    @RequestMapping("/hundsun/svn/xiaobao/local")
    public Result<Map<String, Object>> local(
            @RequestParam(value = "branch", required = false) String branch,
            @RequestParam(value = "localRoot", required = false) String localRoot) {
        try {
            Map<String, Object> data = svnXiaobaoService.scanLocal(branch, localRoot);
            @SuppressWarnings("unchecked")
            List<Map<String, String>> items = (List<Map<String, String>>) data.get("items");
            int size = items == null ? 0 : items.size();
            return Result.success("扫描到 " + size + " 个工作副本", data);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("扫描失败：" + e.getMessage());
        }
    }
}
