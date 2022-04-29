package com.demo.controller.interceptor;

import com.demo.cache.CacheStore;
import com.demo.entity.User;
import com.demo.service.UserService;
import com.demo.utils.CommonUtil;
import com.demo.utils.HostHolder;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Autowired
    private HostHolder hostHolder;

    @Autowired
    private CacheStore cacheStore;

    @Autowired
    private UserService userService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        String accessToken = request.getHeader("access_token");
        // access_token 存在
        if (StringUtils.isNotEmpty(accessToken)) {
            String userId = (String) cacheStore.get(CommonUtil.buildAccessTokenKey(accessToken));
            User user = userService.getCurrentUser(userId);
            hostHolder.setUser(user);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        hostHolder.clear();
    }
}
