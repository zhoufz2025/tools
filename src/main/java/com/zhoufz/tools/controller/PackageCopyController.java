package com.zhoufz.tools.controller;

import com.zhoufz.tools.copy.PackageCopyRequest;
import com.zhoufz.tools.copy.PackageCopyService;
import com.zhoufz.tools.dto.Result;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 大包→小包 A/B 相对路径复制。
 *
 * @author zhoufz
 */
@RestController
public class PackageCopyController {

    private final PackageCopyService packageCopyService;

    public PackageCopyController(PackageCopyService packageCopyService) {
        this.packageCopyService = packageCopyService;
    }

    @PostMapping("/hundsun/package/copy/preview")
    public Result<Map<String, Object>> preview(@RequestBody PackageCopyRequest request) {
        try {
            Map<String, Object> data = packageCopyService.preview(request);
            return Result.success(buildPreviewMessage(data), data);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("预览失败：" + e.getMessage());
        }
    }

    @PostMapping("/hundsun/package/copy/execute")
    public Result<Map<String, Object>> execute(@RequestBody PackageCopyRequest request) {
        try {
            Map<String, Object> data = packageCopyService.execute(request);
            int ok = asInt(data.get("ok"));
            int skipped = asInt(data.get("skipped"));
            int failed = asInt(data.get("failed"));
            String msg = "复制完成：成功 " + ok + "，跳过 " + skipped + "，失败 " + failed;
            if (failed > 0) {
                Result<Map<String, Object>> error = Result.error(msg + "，详见返回明细");
                error.setData(data);
                return error;
            }
            return Result.success(msg, data);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("复制失败：" + e.getMessage());
        }
    }

    private static String buildPreviewMessage(Map<String, Object> data) {
        return "可复制 " + asInt(data.get("okCount"))
                + "，风险 " + asInt(data.get("riskCount"))
                + "，错误 " + asInt(data.get("errorCount"));
    }

    private static int asInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }
}
