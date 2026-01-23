# Getting Started with Kafka Hands-On Guide

This guide will walk you through setting up and running your first Kafka application.

## Step 1: Start Kafka Infrastructure

```bash
docker-compose up -d
```

**What happens:**
- Zookeeper starts on port 2181
- Kafka broker starts on ports 9092 (internal) and 9093 (external)
- Kafka UI starts on port 8080

**Wait 30 seconds** for all services to initialize.

### Verify Installation

```bash
docker-compose ps
```

**Expected output:** 3 running containers
- `kafka-zookeeper`
- `kafka-broker`
- `kafka-ui`

## Step 2: Access Kafka UI

Open your browser and navigate to: **http://localhost:8080**

You should see:
- ✅ 1 broker online
- ✅ 0 topics (topics will be created when the application starts)

## Step 3: Build the Application

```bash
mvn clean install
```

This will:
- Download dependencies
- Compile Java code
- Run tests
- Package the application

## Step 4: Run the Application

```bash
mvn spring-boot:run
```

**Watch the logs for:**
```
Creating topic: demo-messages
Creating topic: demo-transactions
Creating topic: demo-notifications
Creating topic: demo-dlq
Kafka Hands-On Guide Started!
```

The application is now running on **http://localhost:8090**

## Step 5: Send Your First Message

Open a new terminal and execute:

```bash
curl -X POST http://localhost:8090/api/demo/send-simple \
  -H "Content-Type: application/json" \
  -d '{
    "sourceId": "USER001",
    "targetId": "USER002",
    "amount": 100.50,
    "currency": "USD"
  }'
```

**Expected response:**
```json
{
  "success": true,
  "messageId": "550e8400-e29b-41d4-a716-446655440000",
  "producer": "SimpleProducer"
}
```

## Step 6: Verify in Kafka UI

1. Go to **http://localhost:8080**
2. Click **"Topics"** in the left menu
3. Click **"demo-messages"**
4. Click **"Messages"** tab
5. **See your message!** 🎉

You should see:
- Message key: `USER001`
- Message value: Full JSON of your transaction
- Partition: 0, 1, or 2 (randomly assigned)

## Step 7: Check Consumer Logs

Go back to your application console.

You should see log output from the consumer:
```
[SimpleConsumer] Received message: ID=550e8400-..., Source=USER001, Target=USER002, Amount=100.50
[SimpleConsumer] Message processed successfully
```

## 🎉 Congratulations!

You've successfully:
- ✅ Started a Kafka cluster
- ✅ Created topics
- ✅ Sent a message via REST API
- ✅ Observed the message in Kafka UI
- ✅ Consumed the message in your application

## Troubleshooting

### ❌ "Connection refused" error

**Problem:** Docker containers are not running

**Solution:**
```bash
docker-compose ps          # Check container status
docker-compose logs kafka  # Check Kafka logs
```

### ❌ "Topic not found" error

**Problem:** Topics were not created on startup

**Solution:**
1. Check application logs for errors
2. Ensure Kafka is fully started (wait 30 seconds after `docker-compose up`)
3. Restart the application

### ❌ Consumer not receiving messages

**Problem:** Consumer group offset is ahead

**Solution:**
1. Go to Kafka UI
2. Navigate to "Consumers" → "simple-consumer-group"
3. Check offset position
4. Reset offset to "Earliest" if needed

### ❌ Port already in use

**Problem:** Another service is using ports 8080, 8090, or 9093

**Solution:**
- Stop the conflicting service, OR
- Change ports in `docker-compose.yml` and `application.yml`

## Next Steps

### Explore Different Producers

**Reliable Producer** (waits for acknowledgment):
```bash
curl -X POST http://localhost:8090/api/demo/send-reliable \
  -H "Content-Type: application/json" \
  -d '{"sourceId":"ACC001","targetId":"ACC002","amount":250.00,"currency":"EUR"}'
```

**Async Producer** (non-blocking):
```bash
curl -X POST http://localhost:8090/api/demo/send-async \
  -H "Content-Type: application/json" \
  -d '{"sourceId":"ACC003","targetId":"ACC004","amount":500.00,"currency":"USD"}'
```

### Send a Notification

```bash
curl -X POST http://localhost:8090/api/demo/send-notification \
  -H "Content-Type: application/json" \
  -d '{
    "recipientId": "USER123",
    "recipientEmail": "user@example.com",
    "title": "Welcome!",
    "content": "Thanks for joining our platform."
  }'
```

### Monitor in Kafka UI

- **Topics**: View all topics and their configurations
- **Messages**: Browse messages in real-time
- **Consumers**: Monitor consumer groups and lag
- **Brokers**: Check broker health and metrics

## Learn More

- 📖 [README.md](README.md) - Project overview
- 📚 [docs/01-kafka-basics.md](docs/01-kafka-basics.md) - Kafka fundamentals
- 💡 [EXAMPLES.md](EXAMPLES.md) - More code examples
- ❓ [FAQ.md](FAQ.md) - Common questions

## Clean Up

When you're done experimenting:

```bash
# Stop the application
Ctrl+C

# Stop Kafka infrastructure
docker-compose down

# Remove volumes (delete all data)
docker-compose down -v
```

---

🚀 Ready to dive deeper? Continue with [docs/01-kafka-basics.md](docs/01-kafka-basics.md)
