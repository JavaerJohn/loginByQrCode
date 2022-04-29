package com.demo.controller.interceptor;

import com.demo.cache.CacheStore;
import com.demo.utils.CommonUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class ConfirmInterceptor implements HandlerInterceptor {

    @Autowired
    private CacheStore cacheStore;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {

        String onceToken = request.getHeader("once_token");
        if (StringUtils.isEmpty(onceToken)) {
            return false;
        }
        if (StringUtils.isNoneEmpty(onceToken)) {
            String onceTokenKey = CommonUtil.buildOnceTokenKey(onceToken);
//            String allowedUri = (String) cacheStore.get(onceTokenKey);
//            String requestUri = request.getRequestURI();
//            requestUri = requestUri
//                    + "?uuid="
//                    + request.getParameter("uuid");
//            if (!StringUtils.equals(requestUri, allowedUri)) {
//                throw new RuntimeException("一次性 token 与请求的 uri 不对应");
//            }
            String uuidFromCache = (String) cacheStore.get(onceTokenKey);
            String uuidFromRequest = request.getParameter("uuid");
            if (!StringUtils.equals(uuidFromCache, uuidFromRequest)) {
                throw new RuntimeException("非法的一次性 token");
            }
            // 一次性 token 检查完成后将其删除
            cacheStore.delete(onceTokenKey);
        }
        return true;
    }
}
