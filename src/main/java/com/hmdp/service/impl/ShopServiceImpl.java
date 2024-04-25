package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        // 解决缓存穿透
        // Shop shop = queryWithPassThrough(id);
        //  互斥锁解决缓存击穿
        Shop shop = queryWithMytes(id);
        if (shop == null){
            return Result.fail("店铺不存在！");
        }
        // 返回
        return Result.ok(shop);
    }


    // 新增锁
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key,"1",10,TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    // 释放锁
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

    // 存入空对象来解决缓存穿透问题函数
    public Shop queryWithPassThrough(Long id){
        // 使用redis缓存查询商户
        // 1. 使用商户id查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY+id);
        // 2. 缓存存在，返回商户信息
        if(!StrUtil.isBlank(shopJson)){
            Shop shop = JSONUtil.toBean(shopJson,Shop.class);
            return shop;
        }
        // 3. 缓存命中了空值，即缓存不存在,shopJson不等于null，就一定是空字符串
        if(shopJson != null){
            return null;
        }
        // 3. 缓存不存在，进入数据库查找商户信息
        Shop shop = getById(id);
        // 4. 数据库信息不存在，返回404
        if(shop == null){
            // 解决缓存穿透问题,把空对象写入redis
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 5. 数据库信息存在，写入缓存，返回商户信息
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id, JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL,TimeUnit.MINUTES);
        // 7. 返回
        return shop;
    }

    public Shop queryWithMytes(Long id){
        // 使用redis缓存查询商户
        // 1. 使用商户id查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY+id);
        // 2. 缓存存在，返回商户信息
        if(!StrUtil.isBlank(shopJson)){
            Shop shop = JSONUtil.toBean(shopJson,Shop.class);
            return shop;
        }
        // 3. 缓存命中了空值，即缓存不存在,shopJson不等于null，就一定是空字符串
        if(shopJson != null){
            return null;
        }
        // 3. 实现缓存重建
        // 3.1 获取互斥锁
        String lockKey = "lock:shop:"+id;
        Shop shop = null;
        try {
            boolean isLock = tryLock(lockKey);
            // 3.2 判断是否获取成功
            if(!isLock){
                // 3.3 失败，休眠并且重试
                Thread.sleep(50);
                return queryWithMytes(id);
            }
            // 3. 缓存不存在，进入数据库查找商户信息
            shop = getById(id);
            // 模拟重建的延时
            Thread.sleep(200);
            // 4. 数据库信息不存在，返回404
            if(shop == null){
                // 解决缓存穿透问题,把空对象写入redis
                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            // 5. 数据库信息存在，写入缓存，返回商户信息
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id, JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL,TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 6. 释放互斥锁
            unlock(lockKey);
        }
        // 7. 返回
        return shop;
    }
    // 实现数据库和缓存的双写一致
}
