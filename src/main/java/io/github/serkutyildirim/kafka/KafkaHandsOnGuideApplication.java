package io.github.serkutyildirim.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main Spring Boot application class for Kafka Hands-On Guide.
 *
 * This application demonstrates various Kafka patterns including:
 * - Different producer implementations (simple, reliable, async, transactional, partitioned)
 * - Different consumer implementations (simple, manual-ack, batch, error-handling, grouped)
 * - Message serialization/deserialization
 * - Error handling and retry mechanisms
 * - Transaction support
 *
 * @author Serkut Yıldırım
 */
@SpringBootApplication
public class KafkaHandsOnGuideApplication {

    private static final Logger logger = LoggerFactory.getLogger(KafkaHandsOnGuideApplication.class);

    public static void main(String[] args) {
        logger.info("Starting Kafka Hands-On Guide Application...");
        SpringApplication.run(KafkaHandsOnGuideApplication.class, args);

        logger.info("Kafka Hands-On Guide Started!");
    }

}
