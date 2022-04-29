package com.demo.controller;

import com.demo.entity.Response;
import com.demo.entity.User;
import com.demo.service.UserService;
import com.demo.utils.HostHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
@Controller

public class UserController {

    @Autowired
    UserService userService;

    @Autowired
    private HostHolder hostHolder;

    @RequestMapping(path = "/getUser", method = RequestMethod.GET)
    @ResponseBody
    public Response getUser() {
        User user = hostHolder.getUser();
        if (user == null) {
            return Response.createErrorResponse("用户未登录");
        }
        return Response.createResponse(null, user);
    }
}
