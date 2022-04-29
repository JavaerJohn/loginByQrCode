# 扫码登录

目前手机扫描二维码登录已成为一种主流的登录方式，尤其是在 PC 网页端。最近学习了一下扫码登录的原理，感觉蛮有趣的，所以借鉴了网上的一些示例，实现了一个简单的扫码登录的 demo，以此记录一下学习过程。

## 原理解析

### 流程简述

1. PC 端打开二维码登录页面 login.html;
2. login.html 调用后端接口 createQrCodeImg，该接口生成一个随机的 uuid，uuid 可看做是本页面的唯一标识，同时该接口还会创建一个 LoginTicket 对象，该对象中封装了如下信息：
 
   - uuid：页面的唯一标识；
   - userId：用户 id；
   - status：扫码状态，0 表示等待扫码，1 表示等待确认，2 表示已确认。 

3. 将上述 uuid 作为 key、LoginTicket 对象作为 value 存储在 Redis 服务器中（或其他数据库），设置其过期时间为 5 分钟，表示 5 分钟后二维码失效。
4. 生成二维码图片，二维码中封装的信息为：http://localhost:8080/login/scan/uuid=1be1cf4a5ceb4d73a8f2104ffe5fba0c。
5. PC 端显示二维码； 
6. PC 端页面不断轮询（多久轮询一次自行设置）检查扫码的进度，即 LoginTicket 对象的状态。如果为 0 或为 1，继续轮询。如果为 2，停止轮询（已确认登录）。
7. 手机端扫描二维码；
8. 手机端（携带用户的 token，该 token 为手机端 token）访问二维码中的目标网址，手机端服务器首先根据 token 检查当前用户是否已经登录，如果已登录则将 LoginTicket 对象的 status 更新为 1；
9. 手机端服务器询问用户是否确认登录；
10. 用户选择确认登录，手机端服务器将 LoginTicket 对象的 status 更新为 2，并将 userId 设置为当前用户的 id；
11. PC 端检测到用户已确认登录，之后 PC 端服务器为用户生成 token（此 token 为 PC 端的 token），并将 token 返回给前端；
12. 前端获取到 token 后就可以执行其他操作。

### 流程图
总体流程如下：
![扫码登录流程图](./QrCodeLogin.jpg)

## 实现

### 环境准备
1. JDK 1.8；
2. maven 3.3.6；
3. Springboot 2.xx；
4. Redis。

### 实体对象
LoginTicket 类定义如下：
```Java
@Data
public class LoginTicket {

   private String userId;

   private String uuid;

   private int status;
}
```
User 类简单封装用户的 id 和 name：
```Java
@Data
public class User {

   private String userId;

   private String userName;
}
```
### 登录接口

1. 获取二维码
```Java
@RequestMapping(path = "/getQrCodeImg", method = RequestMethod.GET)
public String createQrCodeImg(Model model) {
   
   String uuid = loginService.createQrImg();
   String QrCode = Base64.encodeBase64String(QrCodeUtil.generatePng(loginURL + uuid, 300, 300));

   model.addAttribute("uuid", uuid);
   model.addAttribute("QrCode", QrCode);
   return "login";
}

// Service 层 createQrImg 方法的代码如下
public String createQrImg() {
   String uuid = CommonUtil.generateUUID();
   LoginTicket loginTicket = new LoginTicket();
   loginTicket.setUuid(uuid);
   loginTicket.setStatus(0);

   String redisKey = CommonUtil.getTicketKey(loginTicket.getUuid());
   redisTemplate.opsForValue().set(redisKey, loginTicket, WAIT_EXPIRED_SECONDS, TimeUnit.SECONDS);
   return uuid;
}
```
访问 "localhost:8080/login/getQrCodeImg" 后，后端生成 uuid 和 LoginTicket 对象，并以 uuid 为 key，LoginTicket 对象为 value 存入到 Redis 中（设置其过期时间为 5 分钟）。然后使用开源工具类 Hutool 里的 QrCodeUtil 生成二维码图片。

2. 扫描二维码
```Java
@RequestMapping(path = "/scan/{uuid}/{userId}", method = RequestMethod.GET)
public String scanQrCodeImg(Model model, @PathVariable("uuid") String uuid, @PathVariable("userId") String userId) {
   boolean scanned = loginService.scanQrCodeImg(uuid, userId);
   model.addAttribute("scanned", scanned);
   model.addAttribute("uuid", uuid);
   model.addAttribute("userId", userId);
   return "scan";
}

// Service 层 scanQrCodeImg 方法的代码如下
public boolean scanQrCodeImg(String uuid, String userId) {
   String ticketKey = CommonUtil.getTicketKey(uuid);
   String userKey = CommonUtil.getUserKey(userId);
   LoginTicket loginTicket = (LoginTicket) redisTemplate.opsForValue().get(ticketKey);
   User user = (User) redisTemplate.opsForValue().get(userKey);
   if (user == null || loginTicket == null) {
      return false;
   } else {
      loginTicket.setStatus(1);
      redisTemplate.opsForValue().set(ticketKey, loginTicket, redisTemplate.getExpire(ticketKey, TimeUnit.SECONDS), TimeUnit.SECONDS);
   }
   return true;
}
```
二维码中封装的信息是一个 URL，当手机端扫描二维码时，其实也是访问该 URL 所代表的的网址。访问时请求中会携带手机端用户的 token 和 uuid，其中 token 用来确认用户的身份。上述代码中，我们简化手机端的操作，直接传入 userId，利用 userId 代替 token 来识别用户，scanQrCodeImg 方法根据 userId 查询用户是否已经登录，如果 Redis 中存在该用户的信息，则表示用户已经登录。如果用户未登录或二维码已经过期，则扫码失败，否则将 LoginTicket 对象的状态设置为 1，表示正在扫码。

3. 确认登录
```Java
@RequestMapping(path = "/confirm/{uuid}/{userId}", method = RequestMethod.GET)
@ResponseBody
public Response confirmLogin(@PathVariable("uuid") String uuid, @PathVariable("userId") String userId) {
   JSONObject result = loginService.confirmLogin(uuid, userId);
   boolean logged = result.getBoolean("logged");
   String msg = logged ? "登录成功!" : "二维码已过期!";
   return Response.createResponse(msg, logged);
}

// Service 层 confirmLogin 方法的代码如下
public JSONObject confirmLogin(String uuid, String userId) {
   JSONObject loginResult = new JSONObject();
   String redisKey = CommonUtil.getTicketKey(uuid);
   LoginTicket loginTicket = (LoginTicket) redisTemplate.opsForValue().get(redisKey);
   boolean logged = true;
   if (loginTicket == null) {
      logged = false;
   } else {
      loginTicket.setStatus(2);
      loginTicket.setUserId(userId);
      redisTemplate.opsForValue().set(redisKey, loginTicket, LOGIN_EXPIRED_SECONDS, TimeUnit.SECONDS);
   }
   loginResult.put("logged", logged);
   return loginResult;
}
```
同扫码请求一样，此处也使用 userId 代替用户手机端的 token 来识别用户的身份。当发送确认请求时，服务器首先检查二维码是否过期（按理来说扫码后再确认，二维码应该不会过期，另外此处也没有判断用户是否登录，因为流程走到这里时，用户在手机端肯定是有登录的），如果过期则确认失败，将 logged 置为 false。否则将 LoginTicket 对象的状态设置为 2，并将 userId 置为当前用户的 id（或许 userId 在 scan 在扫码请求就应该设置为用户 id？）。

4. 轮询
```Java
@RequestMapping(path = "/getQrCodeState/{uuid}", method = RequestMethod.GET)
@ResponseBody
public Response getQrCodeState(@PathVariable("uuid") String uuid) throws InterruptedException {
   JSONObject data = new JSONObject();
   String redisKey = CommonUtil.getTicketKey(uuid);
   LoginTicket loginTicket = (LoginTicket) redisTemplate.opsForValue().get(redisKey);
   if (loginTicket == null) {
      data.put("status", -1);
      return Response.createResponse("二维码已过期!", data);
   }

   int status = loginTicket.getStatus();
   data.put("status", status);
   if (status == 2) {
      String userId = loginTicket.getUserId();
      User user = userService.getLoggedUser(userId);
      if (user != null) {
            String token = TokenUtil.buildToken(userId, user.getUserName());
            // data.put("userId", userId);
            // data.put("userName", user.getUserName());
            data.put("token", token);
            return Response.createResponse(null, data);
      }
      return Response.createErrorResponse("无用户信息!");
   }
   Thread.sleep(2000);
   String msg = status == 0 ? null : "已扫描, 等待确认";
   return Response.createResponse(msg, data);
}
```
轮询的逻辑其实就是根据 uuid 检查 LoginTicket 对象的状态，如果 LoginTicket 对象为空，表示二维码已经过期；如果 status 为 0，表示等待扫码；如果 status 为 1，表示已扫码，等待确认；如果 status 为 2，表示已确认登录。当检测到用户确认登录后，服务端为用户生成 token（此 token 用于 PC 端服务器识别用户身份），然后将 token 返回给前端。上述代码中，在生成 token 之前，调用了 UserService 中的 getLoggedUser 方法来查询用户的信息，注意在此 demo 中，为了简化操作，凡是需要获取用户信息的地方我们都使用该方法去获取，前面手机端服务器（其实也是在 PC 端模拟）根据 token （为了简化，实际上为 userId）查询用户信息时也调用了该方法。还有一点需要注意，最后一步的 token 也可以使用 cookie 来代替，这样也许会更加简单，这里为了学习 JWT，所以生成了 token（使用 token 访问时，token 是怎样保存的，苦恼+10086）。getLoggedUser 方法的代码如下：
```Java
public User getLoggedUser(String userId) {
   String redisKey = CommonUtil.getUserKey(userId);
   return (User) redisTemplate.opsForValue().get(redisKey);
}
```
前端的几个 .html 文件的代码，大家直接看源码吧，源码我会放在文末。

## 效果演示
执行程序前，我们需要在 Redis 中存储当前用户的信息，用来表示手机端用户已登录，存储时 key 为 user:userId，value 为 User 对象，演示前，我们在 Redis 中存储了 userId 为 "1" 的用户 "Join同学"。
![扫码登录演示图](./loginByQrCode.gif)

<!-- 1. 启动项目，打开浏览器，访问 localhost:8080/index，显示 '首页'；
2. 点击 '登录'，进入二维码登录界面；
3. 使用开发者工具获取生成的 uuid；
4. 模仿手机端，在另外一个页面中访问 localhost:8080/login/scan/uuid/userId，注意 uuid 为步骤 3 中获取的 uuid，userId 所对应的 User 需要存储在 Redis 中（表示手机端用户已登录，Redis 中的 key 为 userId，value 为 User 对象）。
5. 弹出确认登录窗口；
6. 此时首页上显示等待确认；
7. 点解确认登录
8. 登陆成功；
9. 首页上显示登录用户的信息。 -->

## 待改进
1. 检查扫码状态时采用了轮询的方式，或许可以采用 Websocket；
2. demo 中为了简化操作，并没有手机端服务器，而是使用 PC 端服务器模拟，扫码、确认等请求以及手机端 token 的验证都应该是手机端服务器来处理（手机端 token 使用了 userId 来简化）；
3. 关于 token 的操作有许多待改进的地方（虽然本 demo 中 token 不是重点），比如用户跳转时 token 如何保存；
4. 前端界面刚开始学习，所以代码不规范，这里就不放了。

### **欢迎批评指正，源码见**
