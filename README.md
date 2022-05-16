# Java 语言实现简易版扫码登录

## 📖 原理解析

详见 [Java 语言实现简易版扫码登录](https://www.cnblogs.com/johnlearning/p/16205875.html)。

## 🔨 环境准备

- JDK 1.8：项目使用 Java 语言编写。

- Maven：依赖管理。

- Redis：Redis 既作为数据库存储用户的身份信息，也作为缓存存储二维码信息、token 信息等。

## 💻 主要依赖

- SpringBoot：项目基本环境。

- Hutool：开源工具类，其中的 QrCodeUtil 可用于生成二维码图片。
    
- Thymeleaf：模板引擎，用于页面渲染。

## 🚀 项目运行

**1. 数据准备**

由于项目中并没有实现真实的手机端扫码的功能，因此使用 Postman 模仿手机端向服务端发送请求。首先需要确保服务端存储着用户的信息，即在 Test 类中执行如下代码：

```java
@Test
void insertUser() {
   User user = new User();
   user.setUserId("1");
   user.setUserName("John同学");
   user.setAvatar("/avatar.jpg");
   cacheStore.put("user:1", user);
}
```

手机端发送请求时需要携带手机端 token，这里我们为 useId 为 "1" 的用户生成一个 token（手机端 token）：

```java
@Test
void loginByPhone() {
   String accessToken = CommonUtil.generateUUID();
   System.out.println(accessToken);
   cacheStore.put(CommonUtil.buildAccessTokenKey(accessToken), "1");
}
```

手机端 token（accessToken）为 "aae466837d0246d486f644a3bcfaa9e1"（随机值），之后发送 "扫码" 请求时需要携带这个 token。

**2. 扫码登录流程展示**

启动项目，访问 `localhost:8080/index`：

![](https://johnlearning.oss-cn-beijing.aliyuncs.com/blog/demo/loginByQrCode/index.jpg)

点击登录，并在开发者工具中找到二维码 id（uuid）：

![](https://johnlearning.oss-cn-beijing.aliyuncs.com/blog/demo/loginByQrCode/uuid.jpg)

打开 Postman，发送 `localhost:8080/login/scan` 请求，Query 参数中携带 uuid，Header 中携带手机端 token：

![](https://johnlearning.oss-cn-beijing.aliyuncs.com/blog/demo/loginByQrCode/扫码请求.jpg)

上述请求返回 "扫码成功" 的响应，同时还返回了一次性 token。此时 PC 端显示出扫码用户的头像：

![](https://johnlearning.oss-cn-beijing.aliyuncs.com/blog/demo/loginByQrCode/待确认.jpg)

在 Postman 中发送 `localhost:8080/login/confirm` 请求，Query 参数中携带 uuid，Header 中携带一次性 token：

![](https://johnlearning.oss-cn-beijing.aliyuncs.com/blog/demo/loginByQrCode/确认请求.jpg)

"确认登录" 请求发送完成后，PC 端随即获取到 PC 端 token，并成功查询用户信息：

![](https://johnlearning.oss-cn-beijing.aliyuncs.com/blog/demo/loginByQrCode/登录成功.jpg)
