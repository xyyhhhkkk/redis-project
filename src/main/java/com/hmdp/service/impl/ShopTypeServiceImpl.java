package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryList() {
        // 1. 在缓存里查找商户列表，注意，这里查的是商户类型列表，而不是每个类型点进去的商铺信息。
        String shopType = stringRedisTemplate.opsForValue().get(CACHE_TYPE_KEY);
        // 2. 如果存在redis缓存里，返回列表
        if (shopType!=null){
            return Result.ok(JSONUtil.toList(shopType,ShopType.class));
        }
        // 3. 如果不存在redis缓存里，去数据库里查找
        List<ShopType> typeList = query().orderByAsc("sort").list();
        String toJsonstr = JSONUtil.toJsonStr(typeList);
        // 4. 数据库里找不到，返回404
        // 5. 数据库里找到，存入redis缓存里
        stringRedisTemplate.opsForValue().set(CACHE_TYPE_KEY,toJsonstr);

        return Result.ok(typeList);
    }
}
