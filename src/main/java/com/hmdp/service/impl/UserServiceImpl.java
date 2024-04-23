package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexPatterns;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)){
        // 2. 手机号不符合，返回错误信息
            return Result.fail("手机号格式错误");
        }
        // 3. 手机号符合，生成验证码
        String code = RandomUtil.randomNumbers(6);
        // 4. 保存验证码到session
        // session.setAttribute("code",code);
        // 4. 保存验证码到redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
        // 5. 发送验证码
        log.info("短信发送成功,验证码为{}",code);
        // 6. 返回
        return Result.ok();
    }
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1. 校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)){
        // 2. 不符合，报错
            return Result.fail("电话号码错误，请重新输入");
        }
        // 3. 校验验证码
        String code = loginForm.getCode();
        // 3. 从redis里面获取验证码并校验
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+phone);
        // 3. 从session里面获取验证码并校验
        // Object cachecode = session.getAttribute("code");
        if(cacheCode == null || !code.equals(cacheCode)){
        // 4. 不符合，报错
            return Result.fail("输入验证码错误！");
        }
        // 3. 从redis里面获取验证码并校验
        // 5. 查找用户是否存在
        User user = query().eq("phone",phone).one();
        // 6. 不存在就创建用户
        if (user == null){
            user = createUserWithPhone(phone);
        }
        // 7. 将用户信息保存在session中
//        session.setAttribute("user",user);
//        return Result.ok();
        // 7. 将用户信息保存在redis中
        // 首先随机生成token，作为key
        String token = UUID.randomUUID().toString(true);
        // 其次将user对象转为Hash存储
        Map<String,Object> userMap = BeanUtil.beanToMap(user,new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString())
                );
//        Map<String,Object> userMap = BeanUtil.beanToMap(user);
        String tokenKey = LOGIN_USER_KEY+token;
        stringRedisTemplate.opsForHash().putAll(tokenKey,userMap);
        stringRedisTemplate.expire(tokenKey,LOGIN_USER_TTL,TimeUnit.MINUTES);
        return Result.ok(token);
    }
    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
