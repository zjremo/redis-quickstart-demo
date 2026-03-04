package net.jrz;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import net.jrz.pojo.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

@Slf4j
@SpringBootTest
class RedisDemoApplicationTests {

    // 序列化器默认使用的是ObjectOutputStream，会把对象转为字节，所以存到redis中是乱码
    // 必须要改进序列化器
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // 类序列化器, 对象转为Json字符串，Json字符串还原为对象
    private static ObjectMapper mapper = new ObjectMapper();

    @Test
    void testString() {
        // 写入一条String数据
        redisTemplate.opsForValue().set("name", "zjr");
        Object name = redisTemplate.opsForValue().get("name");
        System.out.println("name = " + name);
    }

    @Test
    void testHash() {
        // 写入一条hash数据
        redisTemplate.opsForHash().put("user:2", "name", "jrz");
        redisTemplate.opsForHash().put("user:2", "age", 22);

        // test for write
        Object name = redisTemplate.opsForHash().get("user:2", "name");
        Object age = redisTemplate.opsForHash().get("user:2", "age");

        System.out.println("name: " + name);
        System.out.println("age: " + age);
    }

    @Test
    void testSerializer() {
        // 创建对象
        User user = new User("jrz", 23);

        // 手动序列化
        try {
            String json = mapper.writeValueAsString(user);
            stringRedisTemplate.opsForValue().set("user:3", json);

            String jsonUser = stringRedisTemplate.opsForValue().get("user:3");
            User user1 = mapper.readValue(jsonUser, User.class);
            System.out.println(user1);
        } catch (JsonProcessingException e) {
            e.printStackTrace(System.out);
            log.info("{} insertion failed", user);
        }

    }
}
