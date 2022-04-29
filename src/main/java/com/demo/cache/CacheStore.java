package com.demo.cache;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class CacheStore {

    @Autowired
    RedisTemplate<String, Object> redisTemplate;

    public void put(String key, Object value, Long expired, TimeUnit timeUnit) {
        redisTemplate.opsForValue().set(key, value, expired, timeUnit);
    }

    public void put(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public Long getExpireForSeconds(String key) {
        return redisTemplate.getExpire(key, TimeUnit.SECONDS);
    }

    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public void delete(String key) {
        redisTemplate.delete(key);
    }
}
