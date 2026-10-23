package com.zhoufz.tools.controller;

import com.zhoufz.tools.dto.Result;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 本机目录浏览（仅供前端路径选择器使用，不改业务逻辑）。
 *
 * @author zhoufz
 */
@RestController
@RequestMapping("/hundsun/fs")
public class LocalFsController {

    @GetMapping("/roots")
    public Result<List<Map<String, String>>> roots() {
        List<Map<String, String>> list = new ArrayList<>();
        addRoot(list, "用户目录", System.getProperty("user.home"));
        addRoot(list, "恒生根", "/Users/zhoufz/hundsun");
        addRoot(list, "Sources", "/Users/zhoufz/hundsun/lcpt60/git/Sources");
        addRoot(list, "工具台", "/Users/zhoufz/hundsun/tools");
        addRoot(list, "系统根", "/");
        return Result.success("常用根目录", list);
    }

    /**
     * 列出目录下的子目录；{@code files=true} 时同时返回普通文件。
     *
     * @param path  绝对路径；空则用用户目录
     * @param files 是否包含文件
     */
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @RequestParam(value = "path", required = false) String path,
            @RequestParam(value = "files", required = false, defaultValue = "false") boolean files) {
        try {
            Path current = resolvePath(path);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("path", current.toString());
            data.put("exists", Files.exists(current));
            data.put("isDirectory", Files.isDirectory(current));
            Path parent = current.getParent();
            data.put("parent", parent == null ? null : parent.toString());

            List<Map<String, Object>> children = new ArrayList<>();
            if (Files.isDirectory(current)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(current)) {
                    for (Path child : stream) {
                        boolean directory = Files.isDirectory(child);
                        if (!directory && !files) {
                            continue;
                        }
                        if (!directory && !Files.isRegularFile(child)) {
                            continue;
                        }
                        String name = child.getFileName() == null
                                ? child.toString()
                                : child.getFileName().toString();
                        if (name.startsWith(".")) {
                            continue;
                        }
                        Map<String, Object> item = new HashMap<>();
                        item.put("name", name);
                        item.put("path", child.toAbsolutePath().normalize().toString());
                        item.put("isDirectory", directory);
                        children.add(item);
                    }
                }
                children.sort(Comparator
                        .comparing((Map<String, Object> m) -> Boolean.TRUE.equals(m.get("isDirectory")) ? 0 : 1)
                        .thenComparing(m -> String.valueOf(m.get("name")), String.CASE_INSENSITIVE_ORDER));
            }
            data.put("children", children);
            return Result.success("目录列表", data);
        } catch (IOException e) {
            return Result.error("读取目录失败: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    private Path resolvePath(String path) {
        Path current;
        if (StringUtils.hasText(path)) {
            current = Paths.get(path.trim()).toAbsolutePath().normalize();
        } else {
            current = Paths.get(System.getProperty("user.home")).toAbsolutePath().normalize();
        }
        return current;
    }

    private void addRoot(List<Map<String, String>> list, String label, String path) {
        if (!StringUtils.hasText(path)) {
            return;
        }
        Path p = Paths.get(path).toAbsolutePath().normalize();
        if (!Files.isDirectory(p)) {
            return;
        }
        Map<String, String> item = new LinkedHashMap<>();
        item.put("label", label);
        item.put("path", p.toString());
        list.add(item);
    }
}
