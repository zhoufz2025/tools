package com.zhoufz.tools.util;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * @author zhoufz
 * 日期 2025/8/30
 */
public class ExcelUtil {

    public static Map<String, String> readExcel(String fileName) {
        try (InputStream is = FileUtil.class.getClassLoader().getResourceAsStream(fileName);
             Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            Map<String, String> map = new HashMap<>();
            for (Row row : sheet) {
                Cell key = row.getCell(0);
                Cell value = row.getCell(1);
                map.put(key.toString(), value.toString());
            }
            return map;
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


}
