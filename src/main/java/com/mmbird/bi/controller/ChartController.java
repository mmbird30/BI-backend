package com.mmbird.bi.controller;

import cn.hutool.core.io.FileUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mmbird.bi.annotation.AuthCheck;
import com.mmbird.bi.common.BaseResponse;
import com.mmbird.bi.common.DeleteRequest;
import com.mmbird.bi.common.ErrorCode;
import com.mmbird.bi.common.ResultUtils;
import com.mmbird.bi.constant.CommonConstant;
import com.mmbird.bi.constant.FileConstant;
import com.mmbird.bi.constant.UserConstant;
import com.mmbird.bi.exception.BusinessException;
import com.mmbird.bi.exception.ThrowUtils;
import com.mmbird.bi.manager.AiManager;
import com.mmbird.bi.manager.RedisLimiterManager;
import com.mmbird.bi.model.dto.chart.*;
import com.mmbird.bi.model.dto.file.UploadFileRequest;
import com.mmbird.bi.model.entity.Chart;
import com.mmbird.bi.model.entity.User;
import com.mmbird.bi.model.enums.FileUploadBizEnum;
import com.mmbird.bi.model.vo.BiGenVO;
import com.mmbird.bi.service.ChartService;
import com.mmbird.bi.service.UserService;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import com.mmbird.bi.utils.ExcelUtils;
import com.mmbird.bi.utils.SqlUtils;
import com.qcloud.cos.COS;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xddf.usermodel.chart.ChartTypes;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import static com.mmbird.bi.utils.ExcelUtils.ExcelToCsv;

/**
 * 图表接口
 */
@RestController
@RequestMapping("/chart")
@Slf4j
public class ChartController {

    @Resource
    private ChartService chartService;


    @Resource
    private UserService userService;


    @Resource
    private AiManager aiManager;


    @Resource
    private RedisLimiterManager redisLimiterManager;
    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;

    // region 增删改查

    /**
     * 创建
     *
     * @param chartAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    public BaseResponse<Long> addChart(@RequestBody ChartAddRequest chartAddRequest, HttpServletRequest request) {
        if (chartAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartAddRequest, chart);
        User loginUser = userService.getLoginUser(request);
        chart.setUserId(loginUser.getId());
        boolean result = chartService.save(chart);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        long newChartId = chart.getId();
        return ResultUtils.success(newChartId);
    }

    /**
     * 删除
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteChart(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldChart.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean b = chartService.removeById(id);
        return ResultUtils.success(b);
    }

    /**
     * 更新（仅管理员）
     *
     * @param chartUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateChart(@RequestBody ChartUpdateRequest chartUpdateRequest) {
        if (chartUpdateRequest == null || chartUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartUpdateRequest, chart);
        long id = chartUpdateRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        boolean result = chartService.updateById(chart);
        return ResultUtils.success(result);
    }

    /**
     * 根据 id 获取
     *
     * @param id
     * @return
     */
    @GetMapping("/get")
    public BaseResponse<Chart> getChartVOById(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = chartService.getById(id);
        if (chart == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        return ResultUtils.success(chart);
    }

    /**
     * 分页获取列表（仅管理员）
     *
     * @param chartQueryRequest
     * @return
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Chart>> listChartByPage(@RequestBody ChartQueryRequest chartQueryRequest) {
        long current = chartQueryRequest.getCurrent();
        long size = chartQueryRequest.getPageSize();
        Page<Chart> chartPage = chartService.page(new Page<>(current, size),
                getQueryWrapper(chartQueryRequest));
        return ResultUtils.success(chartPage);
    }

    /**
     * 分页获取当前用户创建的资源列表
     *
     * @param chartQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/my/list/page")
    public BaseResponse<Page<Chart>> listMyChartByPage(@RequestBody ChartQueryRequest chartQueryRequest,
                                                       HttpServletRequest request) {
        if (chartQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        chartQueryRequest.setUserId(loginUser.getId());
        long current = chartQueryRequest.getCurrent();
        long size = chartQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Chart> chartPage = chartService.page(new Page<>(current, size),
                getQueryWrapper(chartQueryRequest));
        return ResultUtils.success(chartPage);
    }


    // endregion


    /**
     * 编辑（用户）
     *
     * @param chartEditRequest
     * @param request
     * @return
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editChart(@RequestBody ChartEditRequest chartEditRequest, HttpServletRequest request) {
        if (chartEditRequest == null || chartEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartEditRequest, chart);
        User loginUser = userService.getLoginUser(request);
        long id = chartEditRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        if (!oldChart.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean result = chartService.updateById(chart);
        return ResultUtils.success(result);
    }


    // region AI生成图表

    private static final String SYSTEM_MESSAGE = "你是一个数据分析师和前端开发专家，接下来我会按照以下固定格式给你提供内容：\n" +
            "```\n" +
            "分析需求：\n" +
            "【【【数据分析的需求或者目标】】】\n" +
            "原始数据：\n" +
            "【【【csv格式的原始数据，用,作为分隔符】】】\n" +
            "```\n" +
            "请根据这两部分内容，按照以下指定格式生成内容（此外不要输出任何多余的开头、结尾、注释）\n" +
            "1.前端 Echarts V5 的 option 配置对象js代码，合理地将数据进行可视化，不要生成任何多余的内容，比如注释\n" +
            "2.明确的数据分析结论、越详细越好，不要生成多余的注释\n" +
            "请严格按照下面格式输出js代码和数据分析结论，不要输出多余信息\n" +
            "【【【\n" +
            "{\n" +
            "  \"title\": {\n" +
            "    \"text\": \"网站用户增长情况\"\n" +
            "  },\n" +
            "  \"xAxis\": {\n" +
            "    \"type\": \"category\",\n" +
            "    \"data\": [\"1号\", \"2号\", \"3号\"]\n" +
            "  },\n" +
            "  \"yAxis\": {\n" +
            "    \"type\": \"value\"\n" +
            "  },\n" +
            "  \"series\": [{\n" +
            "    \"data\": [10, 20, 30],\n" +
            "    \"type\": \"line\"\n" +
            "  }]\n" +
            "}\n" +
            "【【【\n" +
            "根据数据分析可得，该网站用户数量逐日增长，时间越长，用户数量增长越多。\n" +
            "【【【";


    /**
     * 智能分析（同步）
     *
     * @param multipartFile
     * @param genChartByAiRequest
     * @param request
     * @return
     */
    @PostMapping("/gen")
    public BaseResponse<BiGenVO> genChartByAi(@RequestPart("file") MultipartFile multipartFile,
                                              GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {


        String name = genChartByAiRequest.getName();
        String goal = genChartByAiRequest.getGoal();
        String chartType = genChartByAiRequest.getChartType();

        //校验
        //如果分析目标为空，就抛出请求参数异常，并给出提示
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "目标为空");
        //如果名字不为空，但名字长度大于100，则抛出异常，并给出提示
        if (name != null && name.length() > 100) {
            ThrowUtils.throwIf(true, ErrorCode.PARAMS_ERROR, "名字长度不能超过100");
        } else if (name == null) {
            //如果名字为空，则默认为“未命名”
            name = "默认名字";
        }

        /**
         * 文件安全性校验
         *  文件的大小
         *  文件的后缀
         *  文件的内容（成本要高一些）
         *  文件的合规性（比如敏感内容，建议用第三方的审核功能） 扩展点：接入腾讯云的图片万象数据审核（COS 对象存储的审核功能）
         */
        //获取文件名和大小
        String OriginalFileName = multipartFile.getOriginalFilename();
        long fileSize = multipartFile.getSize();

        /**
         * 文件大小校验
         * 如果文件大小大于1M，则抛出异常，并给出提示
         */
        final long MAX_FILE_SIZE = 1024 * 1024L;
        ThrowUtils.throwIf(fileSize > MAX_FILE_SIZE, ErrorCode.PARAMS_ERROR, "文件大小不能超过1M");

        /**
         * 文件后缀校验
         * 使用hutool工具类的getSuffix方法获取文件后缀
         */
        //获取文件后缀
        String fileSuffix = FileUtil.getSuffix(OriginalFileName);
        final List<String> validFileSuffixList = Arrays.asList("xlsx", "xls");
        ThrowUtils.throwIf(!validFileSuffixList.contains(fileSuffix), ErrorCode.PARAMS_ERROR, "文件后缀不合法");

        //读取用户上传的Excel文件，进行一个处理
        User loginUser = userService.getLoginUser(request);

        //限流判断，每个用户一个限流器
        redisLimiterManager.doRateLimit("genChartByAi_" + loginUser.getId());
        /**
         * 用户输入
         * 分析网站的用户增长情况
         * 日期,用户数
         * 1号,10
         * 2号,20
         * 3号,30
         */
        StringBuilder userMessage = new StringBuilder();

        //拼接分析目标
        String userGoal = goal;
        if (StringUtils.isNotBlank(chartType)) {
            userGoal += "请用" + chartType + "展示";
        }
        //拼接分析目标
        userMessage.append(userGoal).append("/n");
        //拼接转化后的csv文件数据
        String csvData = ExcelUtils.ExcelToCsv(multipartFile);
        userMessage.append(csvData).append("/n");
        //将拼接好的用户输入传给AI，拿到AI生成结果
        String Result = aiManager.doRequest(SYSTEM_MESSAGE, userMessage.toString(), false, 0.1f);
        //处理AI生成结果
        /**
         * Choice(finishReason=stop, index=0, message=ChatMessage(role=assistant, content=【【【
         * {
         *   "title": {
         *     "text": "网站用户增长情况"
         *   },
         *   "xAxis": {
         *     "type": "category",
         *     "data": ["1号", "2号", "3号"]
         *   },
         *   "yAxis": {
         *     "type": "value"
         *   },
         *   "series": [{
         *     "data": [10, 20, 30],
         *     "type": "line"
         *   }]
         * };
         * 【【【
         * 根据数据分析可得，网站用户数量呈现线性增长趋势，从1号到3号，每天的用户增长数分别为10、20、30，显示出良好的增长势头。
         * 【【【, name=null, tool_calls=null, tool_call_id=null), delta=null)
         */
        //将AI生成结果切割成四份
        String[] splits = Result.split("【【【");
        //拆分后还要校验
        if (splits.length < 4) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI生成结果格式不正确");
        }
        //第一份是多余信息，第二份是代码，第三份是分析结果
        String genChart = splits[1].trim();
        String genResult = splits[2].trim();
        //将生成信息插入数据库
        Chart chart = new Chart();
        chart.setName(name);
        chart.setGoal(goal);
        chart.setChartData(csvData);
        chart.setChartType(chartType);
        chart.setGenChart(genChart);
        chart.setGenResult(genResult);
        chart.setUserId(loginUser.getId());
        boolean saveResult = chartService.save(chart);
        ThrowUtils.throwIf(!saveResult, ErrorCode.SYSTEM_ERROR, "图表保存失败");
        //将图表信息返回给前端
        BiGenVO biGenVO = new BiGenVO();
        biGenVO.setChartId(chart.getId());
        biGenVO.setGenChart(genChart);
        biGenVO.setGenResult(genResult);
        return ResultUtils.success(biGenVO);
    }


    /**
     * 智能分析（异步）
     *
     * @param multipartFile
     * @param genChartByAiRequest
     * @param request
     * @return
     */
    @PostMapping("/gen/async")
    public BaseResponse<BiGenVO> genChartByAiASync(@RequestPart("file") MultipartFile multipartFile,
                                                   GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {


        String name = genChartByAiRequest.getName();
        String goal = genChartByAiRequest.getGoal();
        String chartType = genChartByAiRequest.getChartType();

        //校验
        //如果分析目标为空，就抛出请求参数异常，并给出提示
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "目标为空");
        //如果名字不为空，但名字长度大于100，则抛出异常，并给出提示
        if (name != null && name.length() > 100) {
            ThrowUtils.throwIf(true, ErrorCode.PARAMS_ERROR, "名字长度不能超过100");
        } else if (name == null) {
            //如果名字为空，则默认为“未命名”
            name = "默认名字";
        }

        /**
         * 文件安全性校验
         *  文件的大小
         *  文件的后缀
         *  文件的内容（成本要高一些）
         *  文件的合规性（比如敏感内容，建议用第三方的审核功能） 扩展点：接入腾讯云的图片万象数据审核（COS 对象存储的审核功能）
         */
        //获取文件名和大小
        String OriginalFileName = multipartFile.getOriginalFilename();
        long fileSize = multipartFile.getSize();

        /**
         * 文件大小校验
         * 如果文件大小大于1M，则抛出异常，并给出提示
         */
        final long MAX_FILE_SIZE = 1024 * 1024L;
        ThrowUtils.throwIf(fileSize > MAX_FILE_SIZE, ErrorCode.PARAMS_ERROR, "文件大小不能超过1M");

        /**
         * 文件后缀校验
         * 使用hutool工具类的getSuffix方法获取文件后缀
         */
        //获取文件后缀
        String fileSuffix = FileUtil.getSuffix(OriginalFileName);
        final List<String> validFileSuffixList = Arrays.asList("xlsx", "xls");
        ThrowUtils.throwIf(!validFileSuffixList.contains(fileSuffix), ErrorCode.PARAMS_ERROR, "文件后缀不合法");

        //读取用户上传的Excel文件，进行一个处理
        User loginUser = userService.getLoginUser(request);

        //限流判断，每个用户一个限流器
        redisLimiterManager.doRateLimit("genChartByAi_" + loginUser.getId());
        /**
         * 用户输入
         * 分析网站的用户增长情况
         * 日期,用户数
         * 1号,10
         * 2号,20
         * 3号,30
         */
        StringBuilder userMessage = new StringBuilder();

        //拼接分析目标
        String userGoal = goal;
        if (StringUtils.isNotBlank(chartType)) {
            userGoal += "请用" + chartType + "展示";
        }
        //拼接分析目标
        userMessage.append(userGoal).append("/n");
        //拼接转化后的csv文件数据
        String csvData = ExcelUtils.ExcelToCsv(multipartFile);
        userMessage.append(csvData).append("/n");

        //将生成信息插入数据库
        Chart chart = new Chart();
        chart.setName(name);
        chart.setGoal(goal);
        chart.setChartData(csvData);
        chart.setChartType(chartType);
//先提交任务，接受到AI返回的数据再写入数据库
//        chart.setGenChart(genChart);
//        chart.setGenResult(genResult);
        // 设置任务状态为排队中
        chart.setStatus("wait");
        chart.setUserId(loginUser.getId());
        boolean saveResult = chartService.save(chart);
        ThrowUtils.throwIf(!saveResult, ErrorCode.SYSTEM_ERROR, "图表保存失败");

        CompletableFuture.runAsync(() -> {
            Chart updateChart = new Chart();
            updateChart.setId(chart.getId());
            // 设置任务状态为执行中
            updateChart.setStatus("running");
            boolean b = chartService.updateById(updateChart);
            // 如果任务状态更新失败
            if (!b) {
                handleChartUpdateError(chart.getId(), "更新任务执行中状态失败");
                return;
            }

            //如果任务状态更新成功，则可以执行AI生成任务，调用AI
            // 将拼接好的用户输入传给AI，拿到AI生成结果
            String Result = aiManager.doRequest(SYSTEM_MESSAGE, userMessage.toString(), false, 0.1f);
            //处理AI生成结果
            /**
             * Choice(finishReason=stop, index=0, message=ChatMessage(role=assistant, content=【【【
             * {
             *   "title": {
             *     "text": "网站用户增长情况"
             *   },
             *   "xAxis": {
             *     "type": "category",
             *     "data": ["1号", "2号", "3号"]
             *   },
             *   "yAxis": {
             *     "type": "value"
             *   },
             *   "series": [{
             *     "data": [10, 20, 30],
             *     "type": "line"
             *   }]
             * };
             * 【【【
             * 根据数据分析可得，网站用户数量呈现线性增长趋势，从1号到3号，每天的用户增长数分别为10、20、30，显示出良好的增长势头。
             * 【【【, name=null, tool_calls=null, tool_call_id=null), delta=null)
             */
            //将AI生成结果切割成四份
            String[] splits = Result.split("【【【");
            //拆分后还要校验
            if (splits.length < 4) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI生成结果格式不正确");
            }
            //第一份是多余信息，第二份是代码，第三份是分析结果
            String genChart = splits[1].trim();
            String genResult = splits[2].trim();

            // 调用AI成功后，将获取数据插入数据库，更新数据库记录
            updateChart.setId(chart.getId());
            updateChart.setGenChart(genChart);
            updateChart.setGenResult(genResult);
            // 设置任务状态为完成
            updateChart.setStatus("succeed");

            // 再判断一下更新数据库是否成功
            boolean updateResult = chartService.updateById(updateChart);
            if (!updateResult) {
                // TODO 更新任务状态，记录日志
                handleChartUpdateError(chart.getId(), "更新图表成功状态失败");
                return;
            }

        }, threadPoolExecutor);

        //将图表信息返回给前端
        BiGenVO biGenVO = new BiGenVO();
        biGenVO.setChartId(chart.getId());
//        biGenVO.setGenChart(genChart);
//        biGenVO.setGenResult(genResult);
        return ResultUtils.success(biGenVO);
    }

    // 异常工具类
    private void handleChartUpdateError(long chartId, String execMessage) {
        Chart updateChartResult = new Chart();
        updateChartResult.setId(chartId);
        // 设置任务状态为失败
        updateChartResult.setStatus("failed");
        // 设置执行失败信息
        updateChartResult.setExecMessage(execMessage);
        // 判断状态更新是否成功
        boolean updateResult = chartService.updateById(updateChartResult);
        if (!updateResult) {
            log.error("更新图表失败状态失败" + chartId + "," + execMessage);
        }
    }


    // endregion


    /**
     * 获取查询包装类
     *
     * @param chartQueryRequest
     * @return
     */
    private QueryWrapper<Chart> getQueryWrapper(ChartQueryRequest chartQueryRequest) {
        QueryWrapper<Chart> queryWrapper = new QueryWrapper<>();
        if (chartQueryRequest == null) {
            return queryWrapper;
        }
        Long id = chartQueryRequest.getId();
        String name = chartQueryRequest.getName();
        String goal = chartQueryRequest.getGoal();
        String chartType = chartQueryRequest.getChartType();
        Long userId = chartQueryRequest.getUserId();
        String sortField = chartQueryRequest.getSortField();
        String sortOrder = chartQueryRequest.getSortOrder();

        queryWrapper.eq(id != null && id > 0, "id", id);
        queryWrapper.like(StringUtils.isNotBlank(name), "name", name);
        queryWrapper.eq(StringUtils.isNotBlank(goal), "goal", goal);
        queryWrapper.eq(StringUtils.isNotBlank(chartType), "chartType", chartType);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq("isDelete", false);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }

}