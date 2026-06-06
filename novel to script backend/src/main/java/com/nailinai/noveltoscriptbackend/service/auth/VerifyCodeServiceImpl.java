package com.nailinai.noveltoscriptbackend.service.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Service
public class VerifyCodeServiceImpl implements VerifyCodeService {

    private static final String RATE_LIMIT_PREFIX = "verify_code:rate_limit:";
    private static final String CODE_PREFIX = "verify_code:";
    private static final String DAILY_LIMIT_PREFIX = "verify_code:daily:";
    private static final long RATE_LIMIT_TTL = 60;
    private static final long CODE_TTL = 300;
    private static final long DAILY_LIMIT = 10;

    private static final String VERIFY_LUA =
            "local storedCode = redis.call('GET', KEYS[1])\n" +
            "if storedCode == false then\n" +
            "    return 0\n" +
            "end\n" +
            "if storedCode == ARGV[1] then\n" +
            "    redis.call('DEL', KEYS[1])\n" +
            "    return 1\n" +
            "end\n" +
            "return 0";

    private final DefaultRedisScript<Long> verifyScript =
            new DefaultRedisScript<>(VERIFY_LUA, Long.class);

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public void sendCode(String mobile, Integer type) {
        String scene = VerifyCodeService.getScene(type);

        // 1. 频率限制（60s）
        String rateLimitKey = RATE_LIMIT_PREFIX + scene + ":" + mobile;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(rateLimitKey))) {
            throw new RuntimeException("验证码发送过于频繁，请稍后再试");
        }

        // 2. 每日上限（10 条）
        String dailyLimitKey = DAILY_LIMIT_PREFIX + scene + ":" + mobile + ":" + LocalDate.now();
        Long dailyCount = redisTemplate.opsForValue().increment(dailyLimitKey);
        if (dailyCount != null && dailyCount == 1) {
            long secondsUntilMidnight = Duration.between(
                    LocalDateTime.now(),
                    LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.MIDNIGHT)
            ).getSeconds();
            redisTemplate.expire(dailyLimitKey, secondsUntilMidnight, TimeUnit.SECONDS);
        }
        if (dailyCount != null && dailyCount > DAILY_LIMIT) {
            redisTemplate.opsForValue().decrement(dailyLimitKey);
            throw new RuntimeException("该手机号今日验证码发送次数已达上限");
        }

        // 3. 生成 6 位验证码
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(100000, 999999));

        // 4. 存入频率标记
        redisTemplate.opsForValue().set(rateLimitKey, "1", RATE_LIMIT_TTL, TimeUnit.SECONDS);

        // 5. 存入验证码（5min TTL）
        String codeKey = CODE_PREFIX + scene + ":" + mobile;
        redisTemplate.opsForValue().set(codeKey, code, CODE_TTL, TimeUnit.SECONDS);

        // 6. 模拟发送短信
        System.out.println("【验证码】手机号: " + mobile + ", 场景: " + scene + ", 验证码: " + code);
    }

    @Override
    public void verifyCode(String mobile, String code, Integer type) {
        String scene = VerifyCodeService.getScene(type);
        String codeKey = CODE_PREFIX + scene + ":" + mobile;

        Long result = redisTemplate.execute(verifyScript, List.of(codeKey), code);

        if (result == null || result == 0) {
            if (Boolean.FALSE.equals(redisTemplate.hasKey(codeKey))) {
                throw new RuntimeException("验证码已过期，请重新发送");
            }
            throw new RuntimeException("验证码错误");
        }
    }

    @Override
    public void deleteCode(String mobile, Integer type) {
        String scene = VerifyCodeService.getScene(type);
        redisTemplate.delete(CODE_PREFIX + scene + ":" + mobile);
    }
}
