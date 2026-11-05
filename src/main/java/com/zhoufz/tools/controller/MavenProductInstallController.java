package com.zhoufz.tools.controller;

import com.zhoufz.tools.dto.Result;
import com.zhoufz.tools.maven.MavenProductInstallRequest;
import com.zhoufz.tools.maven.MavenProductInstallService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 产品包按工程包版本 install 到本地 Maven 仓。
 *
 * @author zhoufz
 */
@RestController
public class MavenProductInstallController {

    private final MavenProductInstallService service;

    public MavenProductInstallController(MavenProductInstallService service) {
        this.service = service;
    }

    @GetMapping("/hundsun/maven/product-install/defaults")
    public Result<Map<String, Object>> defaults() {
        return Result.success("ok", service.defaults());
    }

    @PostMapping("/hundsun/maven/product-install/preview")
    public Result<Map<String, Object>> preview(@RequestBody MavenProductInstallRequest request) {
        try {
            Map<String, Object> data = service.preview(request);
            return Result.success("预览完成：" + data.get("groupId") + ":"
                    + data.get("artifactId") + " → " + data.get("targetVersion"), data);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("预览失败：" + e.getMessage());
        }
    }

    @PostMapping("/hundsun/maven/product-install/execute")
    public Result<Map<String, Object>> execute(@RequestBody MavenProductInstallRequest request) {
        System.out.println("[product-install] >>> HTTP execute 收到请求 modulePath="
                + (request == null ? null : request.getModulePath())
                + " targetVersion=" + (request == null ? null : request.getTargetVersion()));
        try {
            Map<String, Object> data = service.execute(request);
            boolean ok = Boolean.TRUE.equals(data.get("success"));
            String total = timingTotal(data);
            String msg = ok
                    ? "install 成功，总耗时 " + total
                    : "install 失败或校验未通过，已耗时 " + total + "，详见日志";
            if (!ok) {
                Result<Map<String, Object>> error = Result.error(msg);
                error.setData(data);
                return error;
            }
            return Result.success(msg, data);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("执行失败：" + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static String timingTotal(Map<String, Object> data) {
        if (data == null) {
            return "-";
        }
        Object timings = data.get("timings");
        if (!(timings instanceof Map)) {
            return "-";
        }
        Object total = ((Map<String, Object>) timings).get("total");
        if (total != null && String.valueOf(total).trim().length() > 0) {
            return String.valueOf(total);
        }
        Object totalMs = ((Map<String, Object>) timings).get("totalMs");
        return totalMs == null ? "-" : totalMs + " ms";
    }
}
