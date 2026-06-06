package com.nailinai.noveltoscriptbackend.service.auth;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nailinai.noveltoscriptbackend.api.dto.auth.LoginRequest;
import com.nailinai.noveltoscriptbackend.api.dto.auth.LoginResponse;
import com.nailinai.noveltoscriptbackend.api.dto.auth.RegisterRequest;
import com.nailinai.noveltoscriptbackend.domain.entity.UserEntity;
import com.nailinai.noveltoscriptbackend.persistence.mapper.UserMapper;
import com.nailinai.noveltoscriptbackend.utils.NicknameGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    private static final String LOGIN_FAIL_COUNT_KEY_PREFIX = "login_fail_count:";
    private static final int LOGIN_FAIL_MAX_COUNT = 5;
    private static final long LOGIN_LOCK_MINUTES = 30;

    private static final String FAIL_COUNT_LUA =
            "local currentCount = tonumber(redis.call('GET', KEYS[1]) or '0')\n" +
            "if currentCount >= tonumber(ARGV[1]) then\n" +
            "    return -1\n" +
            "end\n" +
            "local newCount = redis.call('INCR', KEYS[1])\n" +
            "if newCount == 1 then\n" +
            "    redis.call('EXPIRE', KEYS[1], ARGV[2])\n" +
            "end\n" +
            "return newCount";

    private final DefaultRedisScript<Long> failCountScript =
            new DefaultRedisScript<>(FAIL_COUNT_LUA, Long.class);

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private VerifyCodeService verifyCodeService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterRequest request) {
        // 1. 校验验证码
        verifyCodeService.verifyCode(request.getMobile(), request.getVerifyCode(), 1);

        // 2. 手机号唯一性
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getMobile, request.getMobile()));
        if (count > 0) {
            throw new RuntimeException("该手机号已注册");
        }

        // 3. BCrypt 加密密码
        String encodedPassword = PASSWORD_ENCODER.encode(request.getPassword());

        // 4. 构建用户
        UserEntity user = new UserEntity();
        user.setMobile(request.getMobile());
        user.setPassword(encodedPassword);
        user.setNickname(NicknameGenerator.generate());
        user.setStatus(1);

        // 5. 入库
        userMapper.insert(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse login(LoginRequest request) {
        // 1. 查用户
        UserEntity user = userMapper.selectOne(
                new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getMobile, request.getMobile()));
        if (user == null) {
            throw new RuntimeException("该手机号未注册");
        }

        // 2. 状态校验
        if (user.getStatus() == null || user.getStatus() == 0) {
            throw new RuntimeException("账号已被禁用");
        }

        // 3. 身份验证
        if (request.getType() == 1) {
            // 密码登录（含防暴力破解）
            if (request.getPassword() == null || request.getPassword().isEmpty()) {
                throw new RuntimeException("密码不能为空");
            }

            String failCountKey = LOGIN_FAIL_COUNT_KEY_PREFIX + request.getMobile();
            String failCountStr = redisTemplate.opsForValue().get(failCountKey);
            if (failCountStr != null && Integer.parseInt(failCountStr) >= LOGIN_FAIL_MAX_COUNT) {
                throw new RuntimeException("密码错误次数过多，请 " + LOGIN_LOCK_MINUTES + " 分钟后再试");
            }

            if (!PASSWORD_ENCODER.matches(request.getPassword(), user.getPassword())) {
                Long newCount = redisTemplate.execute(
                        failCountScript,
                        List.of(failCountKey),
                        String.valueOf(LOGIN_FAIL_MAX_COUNT),
                        String.valueOf(LOGIN_LOCK_MINUTES * 60)
                );
                int remaining = LOGIN_FAIL_MAX_COUNT - (newCount == null ? 0 : newCount.intValue());
                if (remaining <= 0) {
                    throw new RuntimeException("密码错误次数过多，请 " + LOGIN_LOCK_MINUTES + " 分钟后再试");
                }
                throw new RuntimeException("密码错误，还剩 " + remaining + " 次机会");
            }

            redisTemplate.delete(failCountKey);

        } else if (request.getType() == 2) {
            // 验证码登录
            if (request.getVerifyCode() == null || request.getVerifyCode().isEmpty()) {
                throw new RuntimeException("验证码不能为空");
            }
            verifyCodeService.verifyCode(request.getMobile(), request.getVerifyCode(), 2);
        } else {
            throw new RuntimeException("不支持的登录类型");
        }

        // 4. Sa-Token 登录
        StpUtil.login(user.getId());
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();

        // 5. 返回
        LoginResponse.UserInfo userInfo = LoginResponse.UserInfo.builder()
                .id(user.getId())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .build();

        return LoginResponse.builder()
                .token(tokenInfo.getTokenValue())
                .userInfo(userInfo)
                .build();
    }

    @Override
    public void logout() {
        StpUtil.checkLogin();
        StpUtil.logout();
    }
}
