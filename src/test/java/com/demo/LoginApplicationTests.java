package com.demo;

import com.demo.cache.CacheStore;
import com.demo.entity.User;
import com.demo.utils.CommonUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class LoginApplicationTests {

    @Test
    void contextLoads() {
    }

    @Autowired
    private CacheStore cacheStore;

    @Test
    void insertUser() {
        User user = new User();
        user.setUserId("1");
        user.setUserName("John同学");
        user.setAvatar("/avatar.jpg");
        cacheStore.put("user:1", user);
    }

    @Test
    void loginByPhone() {
        String accessToken = CommonUtil.generateUUID();
        System.out.println(accessToken);
        cacheStore.put(CommonUtil.buildAccessTokenKey(accessToken), "1");
    }
}
