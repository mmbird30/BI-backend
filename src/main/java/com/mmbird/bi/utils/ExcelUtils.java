package com.mmbird.bi.utils;


import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.support.ExcelTypeEnum;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.map.LinkedMap;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.ResourceUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Excel相关工具类
 */
@Slf4j
public class ExcelUtils {
    /**
     * Excel转csv
     * @param multipartFile
     */
    public static String ExcelToCsv(MultipartFile multipartFile){
//        File file = ResourceUtils.getFile("classpath:test_excel.xlsx");
//        List<Map<Integer, String>> list = EasyExcel.read(file)
//                .excelType(ExcelTypeEnum.XLSX)
//                .sheet()
//                .headRowNumber(0)
//                .doReadSync();
//        System.out.println(list);

        List<Map<Integer,String>> list = null;
        try{
            list = EasyExcel.read().file(multipartFile.getInputStream())
                    .excelType(ExcelTypeEnum.XLSX)
                    .sheet()
                    .headRowNumber(0)
                    .doReadSync();
        }catch (IOException e) {
            log.error("表格处理错误",e);
        }
        //如果上传的数据为空
        if(list == null){
            return " ";
        }
        //转换为csv
        StringBuilder stringBuilder = new StringBuilder();
        //读取表头（第一行）
        LinkedHashMap<Integer,String> headMap = (LinkedHashMap)list.get(0);
        List<String> headList = headMap.values().stream().filter(ObjectUtils::isNotEmpty).collect(Collectors.toList());
        stringBuilder.append(StringUtils.join(headList,",")).append("\n");
        //读取数据（读取完表头后，从数据第一行读取）
        for(int i = 1;i<list.size();i++){
            LinkedHashMap<Integer,String> dataMap = (LinkedHashMap)list.get(i);
            List<String> dataList = dataMap.values().stream().filter(ObjectUtils::isNotEmpty).collect(Collectors.toList());
            stringBuilder.append(StringUtils.join(dataList,",")).append("/n");
        }
        return stringBuilder.toString();

    }







}
