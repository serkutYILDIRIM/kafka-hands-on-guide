package io.github.serkutyildirim.kafka;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "spring.kafka.listener.auto-startup=false",
    "spring.kafka.admin.auto-create=false"
})
class KafkaHandsOnGuideApplicationTests {

    @Test
    void contextLoads() {
    }

}
