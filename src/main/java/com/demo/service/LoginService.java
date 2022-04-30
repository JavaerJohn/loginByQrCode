package com.demo.service;

import com.alibaba.fastjson.JSONObject;
import com.demo.cache.CacheStore;
import com.demo.entity.LoginTicket;
import com.demo.entity.QrCodeStatusEnum;
import com.demo.entity.User;
import com.demo.utils.CommonUtil;
import com.demo.utils.HostHolder;
import com.demo.utils.LoginConstant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class LoginService {

    private final Lock lock = new ReentrantLock();
    private final ConcurrentHashMap<String, Condition> CONDITION_CONTAINER = new ConcurrentHashMap<>();

    @Autowired
    private CacheStore cacheStore;

    @Autowired
    private HostHolder hostHolder;

    @Autowired
    private UserService userService;

    public String createQrImg() {
        // uuid
        String uuid = CommonUtil.generateUUID();
        LoginTicket loginTicket = new LoginTicket();
        // 二维码最初为 WAITING 状态
        loginTicket.setStatus(QrCodeStatusEnum.WAITING.getStatus());

        // 存入 redis
        String ticketKey = CommonUtil.buildTicketKey(uuid);
        cacheStore.put(ticketKey, loginTicket, LoginConstant.WAIT_EXPIRED_SECONDS, TimeUnit.SECONDS);

        return uuid;
    }

    public JSONObject scanQrCodeImg(String uuid) {
        // 避免多个移动端同时扫描同一个二维码
        lock.lock();
        JSONObject data = new JSONObject();
        try {
            String ticketKey = CommonUtil.buildTicketKey(uuid);
            LoginTicket loginTicket = (LoginTicket) cacheStore.get(ticketKey);

            // redis 中 key 过期后也可能不会立即删除
            Long expired = cacheStore.getExpireForSeconds(ticketKey);
            boolean valid = loginTicket != null &&
                    QrCodeStatusEnum.parse(loginTicket.getStatus()) == QrCodeStatusEnum.WAITING &&
                    expired != null &&
                    expired >= 0;
            if (valid) {
                User user = hostHolder.getUser();
                if (user == null) {
                    throw new RuntimeException("用户未登录");
                }
                // 修改扫码状态
                loginTicket.setStatus(QrCodeStatusEnum.SCANNED.getStatus());
                Condition condition = CONDITION_CONTAINER.get(uuid);
                if (condition != null) {
                    condition.signal();
                    CONDITION_CONTAINER.remove(uuid);
                }
                loginTicket.setUserId(user.getUserId());
                cacheStore.put(ticketKey, loginTicket, expired, TimeUnit.SECONDS);

                // 生成一次性 token, 用于之后的确认请求
                String onceToken = CommonUtil.generateUUID();

//                String allowURI = LoginConstant.CONFIRM_URI
//                        + "?"
//                        + LoginConstant.ONCE_TOKEN_QUERY_NAME
//                        + "="
//                        + uuid;
//                cacheStore.put(CommonUtil.buildOnceTokenKey(onceToken), allowURI, LoginConstant.ONCE_TOKEN_EXPIRE_TIME, TimeUnit.SECONDS);
                cacheStore.put(CommonUtil.buildOnceTokenKey(onceToken), uuid, LoginConstant.ONCE_TOKEN_EXPIRE_TIME, TimeUnit.SECONDS);
                data.put("once_token", onceToken);
            }
            data.put("valid", valid);
            return data;
        } finally {
            lock.unlock();
        }
    }

    public boolean confirmLogin(String uuid) {
        String ticketKey = CommonUtil.buildTicketKey(uuid);
        LoginTicket loginTicket = (LoginTicket) cacheStore.get(ticketKey);
        boolean logged = true;
        Long expired = cacheStore.getExpireForSeconds(ticketKey);
        if (loginTicket == null || expired == null || expired == 0) {
            logged = false;
        } else {
            lock.lock();
            try {
                loginTicket.setStatus(QrCodeStatusEnum.CONFIRMED.getStatus());
                Condition condition = CONDITION_CONTAINER.get(uuid);
                if (condition != null) {
                    condition.signal();
                    CONDITION_CONTAINER.remove(uuid);
                }
                cacheStore.put(ticketKey, loginTicket, expired, TimeUnit.SECONDS);
            } finally {
                lock.unlock();
            }
        }
        return logged;
    }

    public JSONObject getQrCodeStatus(String uuid, int currentStatus) throws InterruptedException {
        lock.lock();
        try {
            JSONObject data = new JSONObject();
            String ticketKey = CommonUtil.buildTicketKey(uuid);
            LoginTicket loginTicket = (LoginTicket) cacheStore.get(ticketKey);

            QrCodeStatusEnum statusEnum = loginTicket == null || QrCodeStatusEnum.parse(loginTicket.getStatus()) == QrCodeStatusEnum.INVALID ?
                    QrCodeStatusEnum.INVALID : QrCodeStatusEnum.parse(loginTicket.getStatus());

            if (currentStatus == statusEnum.getStatus()) {
                Condition condition = CONDITION_CONTAINER.get(uuid);
                if (condition == null) {
                    condition = lock.newCondition();
                    CONDITION_CONTAINER.put(uuid, condition);
                }
                condition.await(LoginConstant.POLL_WAIT_TIME, TimeUnit.SECONDS);
            }
            // 用户扫码后向 PC 端返回头像信息
            if (statusEnum == QrCodeStatusEnum.SCANNED) {
                User user = userService.getCurrentUser(loginTicket.getUserId());
                data.put("avatar", user.getAvatar());
            }

            // 用户确认后为 PC 端生成 access_token
            if (statusEnum == QrCodeStatusEnum.CONFIRMED) {
                String accessToken = CommonUtil.generateUUID();
                cacheStore.put(CommonUtil.buildAccessTokenKey(accessToken), loginTicket.getUserId(), LoginConstant.ACCESS_TOKEN_EXPIRE_TIME, TimeUnit.SECONDS);
                data.put("access_token", accessToken);
            }

            data.put("status", statusEnum.getStatus());
            data.put("message", statusEnum.getMessage());
            return data;
        } finally {
            lock.unlock();
        }
    }
}
