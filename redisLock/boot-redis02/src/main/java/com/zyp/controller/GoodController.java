package com.zyp.controller;

import com.zyp.util.RedisUtils;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import redis.clients.jedis.Jedis;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * create by
 *
 * @author zouyuanpeng
 * @date 2020/12/6 21:17
 */
@RestController
public class GoodController {

    private static final String REDIS_LOCK = "redis_lock";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private Redisson redisson;

    @Value("${server.port}")
    private String serverPort;

    @GetMapping("/buyGoods")
    public String buyGoods() throws Exception {
        return redisLockLua();
    }

    //推荐（不适合集群）
    private String redisLockLua() throws Exception {
        //1、先加锁，在获取资源，操作资源，最后释放锁
        for (; ; ) {
            //尝试获取锁，设置键的时候带上过期时间，保证操作的原子性
            Boolean flag = stringRedisTemplate.opsForValue()
                    .setIfAbsent(REDIS_LOCK, UUID.randomUUID().toString() + "---" + Thread.currentThread(),
                            10L, TimeUnit.SECONDS);
            String value = stringRedisTemplate.opsForValue().get(REDIS_LOCK);
            //设置过期时间
            //stringRedisTemplate.expire(REDIS_LOCK,10L,TimeUnit.SECONDS);
            //获取锁
            if (flag) {
                try {
                    String resultStr;
                    String result = stringRedisTemplate.opsForValue().get("goods:001");
                    int goodNumber = result == null ? 0 : Integer.parseInt(result);
                    if (goodNumber > 0) {
                        int realNumber = goodNumber - 1;
                        stringRedisTemplate.opsForValue().set("goods:001", realNumber + "");
                        resultStr = "购买成功，此时还剩余：" + realNumber + "件" + "\t 服务器端口：" + serverPort;
                        System.out.println(resultStr);
                        return resultStr;
                    } else {
                        resultStr = "商品已售罄，请下次购买" + "\t 服务器端口：" + serverPort;
                        System.out.println(resultStr);
                        return resultStr;
                    }
                } finally {
                    Jedis jedis = RedisUtils.getJedis();
                    //Lua脚本保证删除键的原子操作
                    String script = "if redis.call('get',KEYS[1]) == ARGV[1] then " +
                            "    return redis.call('del',KEYS[1]) " +
                            "else " +
                            "    return 0 " +
                            "end";
                    try{
                        //执行这段Lua脚本，释放锁
                        Object eval = jedis.eval(script, Collections.singletonList(REDIS_LOCK), Collections.singletonList(value));
                        if("1".equals(eval.toString())){
                            System.out.println("-------del REDIS_LOCK key success");
                        }else {
                            System.out.println("-------del REDIS_LOCK key error");
                        }
                    }finally {
                        if(jedis!=null){
                            jedis.close();
                        }
                    }
                }
            } else {
                //未获取锁
                TimeUnit.MILLISECONDS.sleep(500);
            }
        }
    }

    //不是很推荐
    private String redisLockTransaction() throws InterruptedException {
        //1、先加锁，在获取资源，操作资源，最后释放锁
        for (; ; ) {
            //尝试获取锁，设置键的时候带上过期时间，保证操作的原子性
            Boolean flag = stringRedisTemplate.opsForValue()
                    .setIfAbsent(REDIS_LOCK, UUID.randomUUID().toString() + "---" + Thread.currentThread(),
                            10L, TimeUnit.SECONDS);
            String value = stringRedisTemplate.opsForValue().get(REDIS_LOCK);
            //设置过期时间
            //stringRedisTemplate.expire(REDIS_LOCK,10L,TimeUnit.SECONDS);
            //获取锁
            if (flag) {
                try {
                    String resultStr;
                    String result = stringRedisTemplate.opsForValue().get("goods:001");
                    int goodNumber = result == null ? 0 : Integer.parseInt(result);
                    if (goodNumber > 0) {
                        int realNumber = goodNumber - 1;
                        stringRedisTemplate.opsForValue().set("goods:001", realNumber + "");
                        resultStr = "购买成功，此时还剩余：" + realNumber + "件" + "\t 服务器端口：" + serverPort;
                        System.out.println(resultStr);
                        return resultStr;
                    } else {
                        resultStr = "商品已售罄，请下次购买" + "\t 服务器端口：" + serverPort;
                        System.out.println(resultStr);
                        return resultStr;
                    }
                } finally {
                    //释放锁，添加删除条件，原子操作
                    if (value.equalsIgnoreCase(stringRedisTemplate.opsForValue().get(REDIS_LOCK))) {
                        stringRedisTemplate.delete(REDIS_LOCK);
                    }
                    while (true) {
                        //加事务，乐观锁
                        stringRedisTemplate.watch(REDIS_LOCK);
                        if (value.equalsIgnoreCase(stringRedisTemplate.opsForValue().get(REDIS_LOCK))) {
                            stringRedisTemplate.setEnableTransactionSupport(true);
                            //开始事务
                            stringRedisTemplate.multi();
                            stringRedisTemplate.delete(REDIS_LOCK);
                            List<Object> exec = stringRedisTemplate.exec();
                            if (StringUtils.isEmpty(exec)) {
                                //如果等于null，就是没有删除掉，删除失败，再回去while循环，重新执行删除
                                continue;
                            }
                            //如果删除成功，释放监控器，并且break跳出当前循环
                            //可以不加，执行exec、discard都会释放监控
                            //严谨
                            stringRedisTemplate.unwatch();
                            break;
                        }
                    }
                }
            } else {
                //未获取锁
                TimeUnit.MILLISECONDS.sleep(500);
            }
        }
    }

    //推荐（集群环境下）
    private String redissonLock() {
        //1、先加锁，在获取资源，操作资源，最后释放锁
        for (; ; ) {
            RLock lock = redisson.getLock(REDIS_LOCK);
            lock.lock();
            try {
                String resultStr;
                String result = stringRedisTemplate.opsForValue().get("goods:001");
                int goodNumber = result == null ? 0 : Integer.parseInt(result);
                if (goodNumber > 0) {
                    int realNumber = goodNumber - 1;
                    stringRedisTemplate.opsForValue().set("goods:001", realNumber + "");
                    resultStr = "购买成功，此时还剩余：" + realNumber + "件" + "\t 服务器端口：" + serverPort;
                    System.out.println(resultStr);
                    return resultStr;
                } else {
                    resultStr = "商品已售罄，请下次购买" + "\t 服务器端口：" + serverPort;
                    System.out.println(resultStr);
                    return resultStr;
                }
            } finally {
                //还持有锁的状态，并且是当前线程持有的锁在释放锁
                if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        }
    }

    //不推荐
    private String redisLock() throws InterruptedException {
        //1、先加锁，在获取资源，操作资源，最后释放锁
        for (; ; ) {
            //尝试获取锁，设置键的时候带上过期时间，保证操作的原子性
            Boolean flag = stringRedisTemplate.opsForValue()
                    .setIfAbsent(REDIS_LOCK, UUID.randomUUID().toString() + "---" + Thread.currentThread(),
                            10L, TimeUnit.SECONDS);
            String value = stringRedisTemplate.opsForValue().get(REDIS_LOCK);
            //设置过期时间
            //stringRedisTemplate.expire(REDIS_LOCK,10L,TimeUnit.SECONDS);
            //获取锁
            if (flag) {
                try {
                    String resultStr;
                    String result = stringRedisTemplate.opsForValue().get("goods:001");
                    int goodNumber = result == null ? 0 : Integer.parseInt(result);
                    if (goodNumber > 0) {
                        int realNumber = goodNumber - 1;
                        stringRedisTemplate.opsForValue().set("goods:001", realNumber + "");
                        resultStr = "购买成功，此时还剩余：" + realNumber + "件" + "\t 服务器端口：" + serverPort;
                        System.out.println(resultStr);
                        return resultStr;
                    } else {
                        resultStr = "商品已售罄，请下次购买" + "\t 服务器端口：" + serverPort;
                        System.out.println(resultStr);
                        return resultStr;
                    }
                } finally {
                    //释放锁，添加删除条件，防止误删锁，非原子操作，会出问题
                    if (value.equalsIgnoreCase(stringRedisTemplate.opsForValue().get(REDIS_LOCK))) {
                        stringRedisTemplate.delete(REDIS_LOCK);
                    }
                }
            } else {
                //未获取锁
                TimeUnit.MILLISECONDS.sleep(500);
            }
        }
    }
}