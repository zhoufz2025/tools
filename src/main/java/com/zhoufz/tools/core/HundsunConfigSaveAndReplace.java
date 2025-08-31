package com.zhoufz.tools.core;

import com.zhoufz.tools.util.FileUtil;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author zhoufz
 * 日期 2025/8/31
 */
public class HundsunConfigSaveAndReplace {
    public void saveFile(String sourcePath, String fileType, Map<String, String> params) {
        List<String> fileList = new ArrayList<>();
        String targetPath = sourcePath.substring(0, sourcePath.indexOf("Sources"));
        targetPath += "config/" + fileType + "/";
        String matchFactor = generateMatchFactor(fileType);
        searchFile(sourcePath, matchFactor, fileList);
        String fileName = "index.txt";
        generateFileIndex(targetPath, fileName,fileType, fileList);
    }


    public void replaceFile(String sourcePath, String fileType, Map<String, String> params) {
        sourcePath = FileUtil.appendSlash(sourcePath);
        String path = sourcePath + fileType + "/" + "index.txt";
        String readLine;
        File source, target;
        String[] fieldValue;
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(path)));
            while ((readLine = br.readLine()) != null) {
                if (readLine.trim().length() == 0) {
                    continue;
                }
                if (!supportReplace(readLine)){
                    continue;
                }
                fieldValue = readLine.split("\\|");


                if (fieldValue[0].contains("lcpt-web-manager") ){
                    target = new File(fieldValue[0]);
                    source = new File(sourcePath + fileType + "/" + fieldValue[1]);
                    Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }

                if(fieldValue[0].contains("lcpt-insure")){
                    // continue;
                }
                target = new File(fieldValue[0]);
                source = new File(sourcePath + fileType + "/" + fieldValue[1]);
                Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            br.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private String generateMatchFactor(String fileType) {
        if ("properties".equals(fileType)) {
            return fileType;
        } else if ("pom".equals(fileType)) {
            return "pom.xml";
        } else if ("log".equals(fileType)) {
            return "log4j2.xml";
        }
        return "";
    }

    public void searchFile(String path, String matchFactor, List<String> fileList) {
        File file = new File(path);
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            assert files != null;
            for (File value : files) {
                searchFile(value.getAbsolutePath(), matchFactor, fileList);
            }
        } else {
            String absolutePath = file.getAbsolutePath();
            if (absolutePath.endsWith(matchFactor) && !absolutePath.contains("target")) {
                fileList.add(absolutePath);
            }
        }
    }

    public void generateFileIndex(String targetPath,String fileName,String fileType, List<String> fileList) {
        DataOutputStream out = null;
        File source, target;
        String line;
        try {
            File file = new File(targetPath);
            if (!file.exists()) {
                file.mkdirs();
            }
            String fileExtension = fileExtension(fileType);
            out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(targetPath + fileName)));
            for (int i = 0; i < fileList.size(); i++) {
                line = fileList.get(i) + "|" + i + fileExtension + "\n";
                out.write(line.getBytes("UTF-8"));
                source = new File(fileList.get(i));
                target = new File(targetPath + i + fileExtension);
                Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            out.flush();
            out.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private String fileExtension(String fileType) {
        if ("properties".equals(fileType)) {
            return ".properties";
        } else if ("pom".equals(fileType) || "log".equals(fileType)) {
            return ".xml";
        }
        return ".txt";
    }

    public boolean supportReplace(String path) {
        if (path.contains("F:\\Sources\\app\\lcpt-server\\sale\\lcpt-web\\lcpt-web-manager-dxasset") ) {
            return true;
        }
        return false;
    }
}
