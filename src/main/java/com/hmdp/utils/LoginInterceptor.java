package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class LoginInterceptor implements HandlerInterceptor {
    private StringRedisTemplate stringRedisTemplate;

    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
/*        // 1. 获取session
        HttpSession session  = request.getSession();
        // 2. 获取session中的用户
        User user = (User) session.getAttribute("user");
        // 3. 如果session中存在用户，则放行
        if (user == null) {
            return false;
        }
        UserHolder.saveUser(user);
        return true;*/

        // TODO 1. 获取请求头中的token
        String token = request.getHeader("authorization");
        if(StrUtil.isBlank(token)){
            response.setStatus(401);
            return false;
        }
        // TODO 2. 基于token获取redis中的用户
        Map<Object,Object> userMap = stringRedisTemplate.opsForHash()
                .entries(RedisConstants.LOGIN_USER_KEY + token);
        // TODO 3. 判断用户是否存在
        if (userMap.isEmpty()){
            response.setStatus(401);
            return false;
        }
        // TODO 4. 将查询到的Hash数据转为User对象
        User user = BeanUtil.fillBeanWithMap(userMap,new User(),false);
        // TODO 5. 保存用户信息到ThreadLocal
        UserHolder.saveUser(user);
        // TODO 6. 刷新token有效期
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token,RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
