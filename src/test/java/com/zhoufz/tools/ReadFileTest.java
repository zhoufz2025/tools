package com.zhoufz.tools;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * @author zhoufz
 * 日期 2025/8/30
 */

public class ReadFileTest {

    @Test
    public void readFile() {
        String filePath = "F:\\git-tools\\tools\\src\\main\\resources\\OUTER_SKIRT_ACCOUTNTSYS_1786094659046_202606170000000022.txt";
        String readLine;
        System.out.println(File.separator);
        String[] fieldValue = null;
        try (BufferedReader bufferedReader =
                     new BufferedReader(new InputStreamReader(new FileInputStream(filePath)))){
            while ((readLine = bufferedReader.readLine()) != null) {
                // System.out.println(readLine);
                fieldValue = readLine.split(String.valueOf((char) 0x03));
                // for (String s : fieldValue) {
                //     System.out.println(s);
                // }
                System.out.println(fieldValue.length);
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

}
