package net.jrz;

import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import net.jrz.pojo.User;
import net.jrz.util.RedisConstants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j(topic = "c.RedisTest")
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
            log.info("json is {}", json);
            stringRedisTemplate.opsForValue().set("user:3", json);

            String jsonUser = stringRedisTemplate.opsForValue().get("user:3");
            User user1 = mapper.readValue(jsonUser, User.class);
            System.out.println(user1);
        } catch (JsonProcessingException e) {
            e.printStackTrace(System.out);
            log.info("{} insertion failed", user);
        }
    }

    @Test
    void testJsonUtil() {
        Map<String, String> map = new HashMap<>() {{
            put("1", RedisConstants.TEST_USER_PREFIX + UUID.randomUUID().toString().replace("-", ""));
            put("2", RedisConstants.TEST_USER_PREFIX + UUID.randomUUID().toString().replace("-", ""));
        }};

        // 批量添加key-value对
        stringRedisTemplate.opsForValue().multiSet(map);
        for (String key : map.keySet()) {
            stringRedisTemplate.expire(key, RedisConstants.TEST_USER_TTL, TimeUnit.MINUTES);
        }

        // 创建对象
        User user = new User("jrz", 22);

        String json = JSONUtil.toJsonStr(user);
        log.info("After JsonUtil toJsonStr, the json is {}", json);
        stringRedisTemplate.opsForValue().set(
                RedisConstants.TEST_USER_PREFIX + 1,
                json,
                RedisConstants.TEST_USER_TTL,
                TimeUnit.MINUTES
        );

        String jsonUser = stringRedisTemplate.opsForValue().get(RedisConstants.TEST_USER_PREFIX + 1);
        User user1 = JSONUtil.toBean(jsonUser, User.class);
        log.debug("After get, the user1 is {}", user1);
    }

    @Test
    void testSetNx() throws InterruptedException {
        String locKey = RedisConstants.TEST_LOCK_PREFIX + "1";
        boolean isGetLock = tryLock(locKey);
        log.info("isGetLock is {}", isGetLock);
        unLock(locKey);
    }

    // 尝试获取互斥锁
    boolean tryLock(String lockKey) {
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", RedisConstants.TEST_LOCK_TTL, TimeUnit.SECONDS));
    }

    void unLock(String lockKey) {
        // 解锁，释放互斥锁
        stringRedisTemplate.delete(lockKey);
    }

    @Test
    void testList() {
        String listKey = "user:list:1";
        stringRedisTemplate.delete(listKey);

        // 1. 从左侧头部推入元素 - LPUSH
        stringRedisTemplate.opsForList().leftPush(listKey, "user1");
        stringRedisTemplate.opsForList().leftPush(listKey, "user2");
        stringRedisTemplate.opsForList().leftPushAll(listKey, "user3", "user4", "user5");
        // 2. 从右侧尾部推入元素 - RPUSH
        stringRedisTemplate.opsForList().rightPush(listKey, "user6");
        stringRedisTemplate.opsForList().rightPush(listKey, "user7");
        log.info("List长度为: {}", stringRedisTemplate.opsForList().size(listKey));
        // 3. 获取List中的所有元素 - LRANGE
        List<String> allElements = stringRedisTemplate.opsForList().range(listKey, 0, -1);
        log.info("获取所有元素: {}", allElements);
        // 4. 获取左边弹出元素
        String leftElement = stringRedisTemplate.opsForList().leftPop(listKey);
        log.info("从左侧弹出元素为: {}", leftElement);
        // 5. 获取右侧弹出元素
        String rightElement = stringRedisTemplate.opsForList().rightPop(listKey);
        log.info("从右侧弹出元素为: {}", rightElement);
        // 6. 根据索引获取元素
        String elementByIndex = stringRedisTemplate.opsForList().index(listKey, 1);
        log.info("索引获取元素: {}", elementByIndex);
        // 7. 过期时间
        stringRedisTemplate.expire(listKey, 10, TimeUnit.MINUTES);
    }

    // 不设置过期时间
    @Test
    void testExpireNull() {
        String testKey = "test:1:";
        String testVal = "This is my testVal";
        stringRedisTemplate.opsForValue().set(testKey, testVal, 10, TimeUnit.MINUTES);
        log.info("Expire time is {}", stringRedisTemplate.getExpire(testKey, TimeUnit.MINUTES));
        stringRedisTemplate.expire(testKey, 2, TimeUnit.MINUTES);
        log.info("Expire time is {}", stringRedisTemplate.getExpire(testKey, TimeUnit.MINUTES));
        // 更新为永远不过期
        stringRedisTemplate.persist(testKey);
        log.info("Expire time is {}", stringRedisTemplate.getExpire(testKey, TimeUnit.MILLISECONDS));
    }

    @Test
    void testTime() {
        // 1. LocalDateTime 基本操作
        LocalDateTime now = LocalDateTime.now();
        log.info("当前时间: {}", now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm:ss")));

        LocalDateTime specificDateTime = LocalDateTime.of(2024, 3, 15, 14, 30, 0);
        log.info("指定时间: {}", specificDateTime);

        // 2. LocalDate 基本操作
        LocalDate today = LocalDate.now();
        log.info("今天日期: {}", today);

        LocalDate specificDate = LocalDate.of(2024, 3, 15);
        log.info("指定日期: {}", specificDate);

        // 3. 时间增减操作 - 通过 plus/minus 方法
        LocalDateTime nextWeek = now.plusWeeks(1);
        log.info("一周后: {}", nextWeek);

        LocalDateTime nextMonth = now.plusMonths(1);
        log.info("一个月后: {}", nextMonth);

        LocalDateTime yesterday = now.minusDays(1);
        log.info("昨天: {}", yesterday);

        LocalDateTime nextYear = now.plusYears(1);
        log.info("一年后: {}", nextYear);

        // 4. 时间转换为 Epoch Second (秒级时间戳)
        long epochSeconds = now.toEpochSecond(ZoneOffset.of("+8"));
        log.info("当前时间的 Epoch Seconds: {}", epochSeconds);

        // 5. Epoch Second 转换为 LocalDateTime
        LocalDateTime fromEpoch = LocalDateTime.ofEpochSecond(epochSeconds, 0, ZoneOffset.of("+8"));
        log.info("从 Epoch Seconds 转换回的时间: {}", fromEpoch);

        // 6. 通过 Epoch Seconds 进行时间增减
        long oneHourLaterSeconds = epochSeconds + 3600; // 加1小时 (3600秒)
        LocalDateTime oneHourLater = LocalDateTime.ofEpochSecond(oneHourLaterSeconds, 0, ZoneOffset.of("+8"));
        log.info("通过 Epoch Seconds 加1小时后的时间: {}", oneHourLater);

        long oneDayLaterSeconds = epochSeconds + (24 * 3600); // 加1天 (86400秒)
        LocalDateTime oneDayLater = LocalDateTime.ofEpochSecond(oneDayLaterSeconds, 0, ZoneOffset.of("+8"));
        log.info("通过 Epoch Seconds 加1天后的时间: {}", oneDayLater);

        // 7. 在 Redis 中存储时间相关数据
        String timeKey = "demo:time:1";

        // 存储当前时间字符串
        String timeStr = now.toString();
        stringRedisTemplate.opsForValue().set(timeKey + ":datetime", timeStr, 10, TimeUnit.MINUTES);
        log.info("存储时间字符串: {}", timeStr);

        // 存储 Epoch Seconds
        stringRedisTemplate.opsForValue().set(timeKey + ":epoch", String.valueOf(epochSeconds), 10, TimeUnit.MINUTES);
        log.info("存储 Epoch Seconds: {}", epochSeconds);

        // 8. 从 Redis 读取并转换时间
        String savedEpochStr = stringRedisTemplate.opsForValue().get(timeKey + ":epoch");
        if (savedEpochStr != null) {
            long savedEpoch = Long.parseLong(savedEpochStr);
            LocalDateTime savedTime = LocalDateTime.ofEpochSecond(savedEpoch, 0, ZoneOffset.of("+8"));
            log.info("从 Redis 读取并转换的时间: {}", savedTime);
        }

        // 9. 计算两个时间之间的差值
        LocalDateTime futureTime = now.plusDays(7);
        long daysBetween = ChronoUnit.DAYS.between(now, futureTime);
        log.info("现在到 {} 相差 {} 天", futureTime, daysBetween);

        long hoursBetween = ChronoUnit.HOURS.between(now, futureTime);
        log.info("现在到 {} 相差 {} 小时", futureTime, hoursBetween);

        // 10. 时区转换示例
        ZonedDateTime zonedDateTime = now.atZone(ZoneOffset.of("+8"));
        log.info("东八区时间: {}", zonedDateTime);

        ZonedDateTime utcTime = zonedDateTime.withZoneSameInstant(ZoneOffset.UTC);
        log.info("UTC 时间: {}", utcTime);
    }

}
