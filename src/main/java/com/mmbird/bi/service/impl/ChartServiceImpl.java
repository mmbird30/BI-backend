package com.mmbird.bi.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mmbird.bi.model.entity.Chart;
import com.mmbird.bi.service.ChartService;
import com.mmbird.bi.mapper.ChartMapper;
import org.springframework.stereotype.Service;

/**
* @author 25709
* @description 针对表【chart(图表信息表)】的数据库操作Service实现
* @createDate 2024-09-02 22:38:42
*/
@Service
public class ChartServiceImpl extends ServiceImpl<ChartMapper, Chart>
    implements ChartService{

}




