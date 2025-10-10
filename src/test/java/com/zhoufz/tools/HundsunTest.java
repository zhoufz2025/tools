package com.zhoufz.tools;

import com.zhoufz.tools.service.HundsunServiceImpl;
import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * @author zhoufz
 * 日期 2025/8/30
 */


public class HundsunTest {

    @Test
    public void gitReplaceJavaFile() {
        // git checkout hotfix/IFMS6.0V202405.08.037
    
        HundsunServiceImpl service = new HundsunServiceImpl();
        service.gitReplaceSqlFile("gitReplaceFile.txt");
    }
    
    @Test
    public void svnReplaceJavaFile() {
        HundsunServiceImpl service = new HundsunServiceImpl();
        service.svnReplaceSqlFile("svnReplaceFile.txt");
    }

    @Test
    public void getGitSourceCode() {
        HundsunServiceImpl service = new HundsunServiceImpl();
        String basePath = "F:\\提交\\Sources\\app";
        service.getGitSourceCode(basePath);
    }

    @Test
    public void getGitHuiSourceCode() {
        HundsunServiceImpl service = new HundsunServiceImpl();
        String basePath = "/Users/zhoufz/hundsun/lcpt60/git/Sources/";
        service.getGitHuiSourceCode(basePath);
    }

    @Test
    public void getGitCounterSourceCode() {
        HundsunServiceImpl service = new HundsunServiceImpl();
        String basePath = "/Users/zhoufz/hundsun/lcpt60/git/Sources/";
        service.getGitCounterSourceCode(basePath);
    }
    
    @Test
    public void test() {
        try {
            int date1 = 20241227;
            int date2 = 20250108;
            // 将int格式的日期转换为Date对象
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
            Date dateObj1 = sdf.parse(String.valueOf(date1));
            Date dateObj2 = sdf.parse(String.valueOf(date2));
        
            // 获取日历实例
            Calendar cal1 = Calendar.getInstance();
            Calendar cal2 = Calendar.getInstance();
        
            cal1.setTime(dateObj1);
            cal2.setTime(dateObj2);
        
            // 获取年份和周数
            int week1 = cal1.get(Calendar.WEEK_OF_YEAR);
            System.out.println(week1);
            int week2 = cal2.get(Calendar.WEEK_OF_YEAR);
            System.out.println(week2);
            // 判断是否在同一年的同一周
            System.out.println(week1==week2);
        } catch (Exception e) {
            // 如果日期格式不正确，返回false
            System.out.println(false);
        }
        
    }
    
    @Test
    public void test2() {
        System.out.println("1".split(",")[0]);
    
    }
    
    
    
    
}
