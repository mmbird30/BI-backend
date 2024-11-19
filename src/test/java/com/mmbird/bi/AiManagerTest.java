package com.mmbird.bi;

import com.mmbird.bi.manager.AiManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;


@SpringBootTest
class AiManagerTest {

    @Resource
    private AiManager aiManager;

    @Test
    void doRequest() {


        String answer = aiManager.doRequest("你是一个数据分析师和前端开发专家，接下来我会按照以下固定格式给你提供内容：\n" +
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
                "};\n" +
                "【【【\n" +
                "根据数据分析可得，该网站用户数量逐日增长，时间越长，用户数量增长越多。\n" +
                "【【【", "分析网站的用户增长情况\n" +
                "日期,用户数\n" +
                "1号,10\n" +
                "2号,20\n" +
                "3号,30\n", false, 0.1f);

        System.out.println(answer);
    }
}