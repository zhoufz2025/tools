package com.zhoufz.tools.util;

import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * @author zhoufz
 * 日期 2025/8/30
 */
public class FileUtil {

    /**
     * 读取resource下文件
     * @return
     */
    public static List<String> readFile() {
        // 通过类加载器获取资源
        try (InputStream is = FileUtil.class.getClassLoader().getResourceAsStream("replaceFile.txt");
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            List<String> list = new ArrayList<>();
            while ((line = reader.readLine()) != null) {
                if (line.trim().length() == 0) {
                    continue;
                }
                if (line.contains("/") && !"/".equals(File.separator)) {
                    line=line.replace("/", "\\");
                }
                if (line.contains("\\") && !"\\".equals(File.separator)) {
                    line=line.replace("\\", "/");
                }
                list.add(line);

            }
            return list;

        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static List<String> readFile(String filePath) {
        // 通过类加载器获取资源
        try (
             BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(Paths.get(filePath))))) {
            String line;
            List<String> list = new ArrayList<>();
            while ((line = reader.readLine()) != null) {
                if (line.trim().length() == 0) {
                    continue;
                }
                if (line.contains("/") && !"/".equals(File.separator)) {
                    line=line.replace("/", "\\");
                }
                if (line.contains("\\") && !"\\".equals(File.separator)) {
                    line=line.replace("\\", "/");
                }
                list.add(line);
            }
            return list;

        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void replace(File target, File source) throws IOException {
        if (target.exists()) {
            Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } else {
            if (!target.getParentFile().exists()) {
                mkdir(target.getParentFile().toPath());
            }
            Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void replaceSql(File target, File source, String taskId) throws IOException {
        if (StringUtils.isEmpty(taskId)){
            replace(target,source);
        }else{
            if (!target.getParentFile().exists()) {
                mkdir(target.getParentFile().toPath());
            }
            // 文件追加
            OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(target, true), "UTF-8");
            InputStreamReader reader = new InputStreamReader(new FileInputStream(source), "UTF-8");
            boolean taskLineFlag = false,writeFlag = false;
            try {
                BufferedReader br = new BufferedReader(reader);
                String readLine;

                while ((readLine = br.readLine()) != null) {
                    if (readLine.contains(taskId)) {
                        taskLineFlag = true;
                    }
                    if (taskLineFlag) {
                        writeFlag = true;
                        writer.write(readLine+"\n");
                    }
                    if (readLine.contains(taskId) && readLine.contains("end")) {
                        taskLineFlag = false;
                    }
                    System.out.println(readLine);
                }
                writer.flush();
                br.close();

            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                writer.close();
                reader.close();
            }
            if (!writeFlag){
                Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

        }

    }

    public static void mkdir(Path toPath) {
        if (toPath.getParent().toFile().exists()){
            try {
                Files.createDirectory(toPath);
                return;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        mkdir(toPath.getParent());
    }

    public static String appendSlash(String path) {
        if (!path.endsWith(File.separator)) {
            path += File.separator;
        }
        return path;
    }


}
