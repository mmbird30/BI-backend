package com.mmbird.bi.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mmbird.bi.annotation.AuthCheck;
import com.mmbird.bi.common.BaseResponse;
import com.mmbird.bi.common.DeleteRequest;
import com.mmbird.bi.common.ErrorCode;
import com.mmbird.bi.common.ResultUtils;
import com.mmbird.bi.config.ThreadPoolExecutorConfig;
import com.mmbird.bi.constant.CommonConstant;
import com.mmbird.bi.constant.UserConstant;
import com.mmbird.bi.exception.BusinessException;
import com.mmbird.bi.exception.ThrowUtils;
import com.mmbird.bi.manager.AiManager;
import com.mmbird.bi.manager.RedisLimiterManager;
import com.mmbird.bi.model.dto.chart.*;
import com.mmbird.bi.model.entity.Chart;
import com.mmbird.bi.model.entity.User;
import com.mmbird.bi.model.vo.BiGenVO;
import com.mmbird.bi.service.ChartService;
import com.mmbird.bi.service.UserService;
import com.mmbird.bi.utils.ExcelUtils;
import com.mmbird.bi.utils.SqlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 队列测试接口
 */
@RestController
@RequestMapping("/Queue")
@Slf4j
@Profile({"dev","local"})
public class QueueController {

    @Resource
    //自动注入一个线程池实例
    private ThreadPoolExecutor threadPoolExecutor;

    /**
     * 添加任务到线程池
     *
     * @param name
     */
    @GetMapping("/add")
    //接受一个参数name，然后将任务添加到线程池中
    public void add(String name) {
        // 使用CompletableFuture运行一个异步任务
        CompletableFuture.runAsync(() -> {
            //打印一条日志信息，包括任务名称、执行线程名称
            log.info("任务执行中：" + name + ",执行线程：" + Thread.currentThread().getName());
            try {
                //模拟任务执行
                Thread.sleep(600000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, threadPoolExecutor);
    }


    /**
     * 获取线程池状态信息
     *
     * @return
     */
    @GetMapping("/get")
    public String get() {
        Map<String, Object> map = new HashMap<>();
        //获取任务队列长度
        int size = threadPoolExecutor.getQueue().size();
        map.put("队列长度：", size);
        //获取线程池已接收的任务总数
        long taskCount = threadPoolExecutor.getTaskCount();
        map.put("任务总数", taskCount);
        //获取线程已完成的任务数
        long completedTaskCount = threadPoolExecutor.getCompletedTaskCount();
        map.put("已完成的任务数：", completedTaskCount);
        //获取正在执行任务的线程数
        int activeCount = threadPoolExecutor.getActiveCount();
        map.put("正在执行任务的线程数：", activeCount);
        return JSONUtil.toJsonStr(map);
    }
}