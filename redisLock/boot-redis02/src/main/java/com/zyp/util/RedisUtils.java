package com.zyp.util;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * create by
 *
 * @author zouyuanpeng
 * @date 2020/12/9 11:04
 */
public class RedisUtils {
    private static JedisPool jedisPool;

    static {
        JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
        jedisPoolConfig.setMaxTotal(20);
        jedisPoolConfig.setMaxIdle(10);

        jedisPool = new JedisPool(jedisPoolConfig,"127.0.0.1",6379,100000,"123456");
    }

    public static Jedis getJedis() throws Exception{
        if (null!=jedisPool){
            return jedisPool.getResource();
        }
        throw new Exception("Jedispool is not ok");
    }
}
