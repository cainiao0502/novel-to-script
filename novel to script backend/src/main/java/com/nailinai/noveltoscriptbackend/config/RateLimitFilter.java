package com.nailinai.noveltoscriptbackend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class RateLimitFilter extends OncePerRequestFilter {

    private static final String LUA_SCRIPT = """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])
            local windowStart = now - window * 1000

            redis.call('ZREMRANGEBYSCORE', key, 0, windowStart)
            local count = redis.call('ZCARD', key)

            if count >= limit then
                local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
                local retryAfter = 0
                if #oldest > 0 then
                    retryAfter = math.ceil((tonumber(oldest[2]) - windowStart) / 1000)
                end
                return {0, count, retryAfter}
            end

            redis.call('ZADD', key, now, now .. '-' .. math.random(100000))
            redis.call('EXPIRE', key, window)
            return {1, limit - count - 1, 0}
            """;

    private static final String[] LLM_PATHS = {
            "/generate",
            "/regenerate",
            "/rewrite-dialogue",
            "/analyze-emotions"
    };

    private final StringRedisTemplate stringRedisTemplate;
    private final RateLimitProperties properties;
    private final DefaultRedisScript<List> redisScript;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(StringRedisTemplate stringRedisTemplate,
                           RateLimitProperties properties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
        this.objectMapper = new ObjectMapper();

        this.redisScript = new DefaultRedisScript<>();
        this.redisScript.setScriptText(LUA_SCRIPT);
        this.redisScript.setResultType(List.class);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = extractClientIp(request);
        String key = "rate_limit:llm:" + clientIp;
        long now = System.currentTimeMillis();

        @SuppressWarnings("unchecked")
        List<Long> result = stringRedisTemplate.execute(
                redisScript,
                Collections.singletonList(key),
                String.valueOf(now),
                String.valueOf(properties.getWindowSeconds()),
                String.valueOf(properties.getMaxRequests())
        );

        if (result == null || result.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        long allowed = result.get(0);
        long remaining = result.get(1);
        long retryAfter = result.get(2);

        response.setHeader("X-RateLimit-Limit", String.valueOf(properties.getMaxRequests()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, remaining)));

        if (allowed == 0) {
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                    "error", "rate_limit_exceeded",
                    "message", "请求过于频繁，请 " + retryAfter + " 秒后再试",
                    "retryAfterSeconds", retryAfter
            )));
            return;
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if (!"POST".equals(method)) {
            return true;
        }

        for (String llmPath : LLM_PATHS) {
            if (path.endsWith(llmPath)) {
                return false;
            }
        }
        return true;
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
