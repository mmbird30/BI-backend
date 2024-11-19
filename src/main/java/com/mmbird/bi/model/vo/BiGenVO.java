package com.mmbird.bi.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * BI生成视图
 *
 *
 */
@Data
public class BiGenVO  {

    /**
     * id
     */
    private Long chartId;

    /**
     * 生成图表
     */
    private String genChart;

    /**
     * 生成的分析结论
     */
    private String genResult;


}