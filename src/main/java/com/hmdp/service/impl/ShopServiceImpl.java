package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
        // Shop shop = queryWithMytes(id);
        // 逻辑过期解决缓存击穿
        Shop shop = queryWithlogicalExpire(id);
        if (shop == null){
            return Result.fail("店铺不存在！");
        }
        // 返回
        return Result.ok(shop);
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public Shop queryWithlogicalExpire(Long id){
        // 使用redis缓存查询商户
        // 1. 使用商户id查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY+id);
        // 2. 缓存存在，返回商户信息
        if(StrUtil.isBlank(shopJson)){
            return null;
        }
        // 3. 缓存不存在，进入数据库查找商户信息
        // 4. 命中，需要把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson,RedisData.class);
        JSONObject data = (JSONObject)  redisData.getData();
        Shop shop = JSONUtil.toBean(data,Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 5. 判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            // 5.1 未过期，直接返回店铺信息
            return shop;
        }
        // 5.2 已过期，需要缓存重建

        // 6. 缓存重建
        // 6.1 获取互斥锁
        String lockKey = LOCK_SHOP_KEY+id;
        boolean isLock = tryLock(lockKey);
        // 6.2 判断是否获取锁成功
        if(isLock){
            // 6.3 成功，开启独立线程，实现缓存重建
            // 注意：获取锁成功之后应该再次检测redis缓存是否过期，需要doublecheck。
            // doublecheck如果存在则无需重建缓存
            if(expireTime.isAfter(LocalDateTime.now())){
                // 5.1 未过期，直接返回店铺信息
                return shop;
            }
            CACHE_REBUILD_EXECUTOR.submit(() ->{
                try {
                    // 重建缓存
                    this.saveShop2Redis(id,20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放锁
                    unlock(lockKey);
                }
            });

        }
        // 6.4 返回过期的店铺信息
        return shop;
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

    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
        // 1. 查询店铺数据
        Shop shop = getById(id);
        Thread.sleep(200);
        // 2. 封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        // 3.写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id, JSONUtil.toJsonStr(redisData));

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
