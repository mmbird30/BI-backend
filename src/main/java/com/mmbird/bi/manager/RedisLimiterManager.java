package com.mmbird.bi.manager;


import com.mmbird.bi.common.ErrorCode;
import com.mmbird.bi.exception.BusinessException;
import com.mmbird.bi.exception.ThrowUtils;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 专门提供RedisLimiter限流基础服务（其他项目通用）
 */

@Service
public class RedisLimiterManager {

    @Resource
    private RedissonClient redissonClient;

    /**
     * 限流操作
     *
     * @param key 区分不同的限流器，比如不同的用户id应该分别统计
     */
    public void doRateLimit(String key){
        //创建一个名称为user_limiter的限流器，每秒最多访问2次
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
        //限流器的统计规则：每秒两个请求；连续地请求，最多只能有一个请求被通过
        rateLimiter.trySetRate(RateType.OVERALL, 2,1,RateIntervalUnit.SECONDS);
        //获取到令牌后执行业务逻辑‘
        boolean canOp = rateLimiter.tryAcquire();
        if(!canOp){
            throw new BusinessException(ErrorCode.TOO_MANY_REQUEST_ERROR);
        }
    }
}
