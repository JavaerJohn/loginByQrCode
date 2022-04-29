package com.demo.service;

import com.demo.cache.CacheStore;
import com.demo.entity.User;
import com.demo.utils.CommonUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    @Autowired
    private CacheStore cacheStore;

    public User getCurrentUser(String userId) {
        String userKey = CommonUtil.buildUserKey(userId);
        return (User) cacheStore.get(userKey);
    }
}
