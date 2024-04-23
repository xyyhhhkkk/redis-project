package com.hmdp.service.impl;

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

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

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
        // 使用redis缓存查询商户
        // 1. 使用商户id查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY+id);
        // 2. 缓存存在，返回商户信息
        if(!StrUtil.isBlank(shopJson)){
            Shop shop = JSONUtil.toBean(shopJson,Shop.class);
            return Result.ok(shop);
        }
        // 3. 缓存不存在，进入数据库查找商户信息
        Shop shop = getById(id);
        // 4. 数据库信息不存在，返回404
        if(shop == null){
            Result.fail("商户信息不存在");
        }
        // 5. 数据库信息存在，写入缓存，返回商户信息
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id, JSONUtil.toJsonStr(shop));
        return Result.ok(shop);
    }
}
