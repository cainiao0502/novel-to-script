package com.nailinai.noveltoscriptbackend.config;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.session.SaSession;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的 Sa-Token 持久化实现。
 * 重启后端不丢登录态，支持活跃过期（6小时滑动）。
 */
@Component
public class RedisSaTokenDao implements SaTokenDao {

    private final RedisTemplate<String, Object> redis;

    public RedisSaTokenDao(RedisTemplate<String, Object> redis) {
        this.redis = redis;
    }

    @Override
    public String get(String key) {
        Object v = redis.opsForValue().get(key);
        return v instanceof String s ? s : null;
    }

    @Override
    public void set(String key, String value, long timeout) {
        if (timeout > 0) {
            redis.opsForValue().set(key, value, timeout, TimeUnit.SECONDS);
        } else {
            redis.opsForValue().set(key, value);
        }
    }

    @Override
    public void update(String key, String value) {
        long ttl = getTimeout(key);
        redis.opsForValue().set(key, value);
        if (ttl > 0) {
            redis.expire(key, ttl, TimeUnit.SECONDS);
        }
    }

    @Override
    public void delete(String key) {
        redis.delete(key);
    }

    @Override
    public long getTimeout(String key) {
        Long ttl = redis.getExpire(key, TimeUnit.SECONDS);
        return ttl != null ? ttl : -2L;
    }

    @Override
    public void updateTimeout(String key, long timeout) {
        if (timeout > 0) {
            redis.expire(key, timeout, TimeUnit.SECONDS);
        } else {
            redis.persist(key);
        }
    }

    @Override
    public Object getObject(String key) {
        return redis.opsForValue().get(key);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getObject(String key, Class<T> clazz) {
        Object v = redis.opsForValue().get(key);
        if (v == null) return null;
        if (clazz.isInstance(v)) return (T) v;
        return null;
    }

    @Override
    public void setObject(String key, Object object, long timeout) {
        if (timeout > 0) {
            redis.opsForValue().set(key, object, timeout, TimeUnit.SECONDS);
        } else {
            redis.opsForValue().set(key, object);
        }
    }

    @Override
    public void updateObject(String key, Object object) {
        long ttl = getObjectTimeout(key);
        redis.opsForValue().set(key, object);
        if (ttl > 0) {
            redis.expire(key, ttl, TimeUnit.SECONDS);
        }
    }

    @Override
    public void deleteObject(String key) {
        redis.delete(key);
    }

    @Override
    public long getObjectTimeout(String key) {
        return getTimeout(key);
    }

    @Override
    public void updateObjectTimeout(String key, long timeout) {
        updateTimeout(key, timeout);
    }

    @Override
    public SaSession getSession(String sessionId) {
        return (SaSession) redis.opsForValue().get(sessionId);
    }

    @Override
    public void setSession(SaSession session, long timeout) {
        if (timeout > 0) {
            redis.opsForValue().set(session.getId(), session, timeout, TimeUnit.SECONDS);
        } else {
            redis.opsForValue().set(session.getId(), session);
        }
    }

    @Override
    public void updateSession(SaSession session) {
        long ttl = getSessionTimeout(session.getId());
        redis.opsForValue().set(session.getId(), session);
        if (ttl > 0) {
            redis.expire(session.getId(), ttl, TimeUnit.SECONDS);
        }
    }

    @Override
    public void deleteSession(String sessionId) {
        redis.delete(sessionId);
    }

    @Override
    public long getSessionTimeout(String sessionId) {
        return getTimeout(sessionId);
    }

    @Override
    public void updateSessionTimeout(String sessionId, long timeout) {
        updateTimeout(sessionId, timeout);
    }

    @Override
    public java.util.List<String> searchData(String prefix, String keyword, int start, int size, boolean sortType) {
        return java.util.Collections.emptyList();
    }
}
