# Kafka Examples

This document provides practical examples for common Kafka operations using the Hands-On Guide project.

## Table of Contents

- [Sending Messages](#sending-messages)
- [Producer Patterns](#producer-patterns)
- [Consumer Patterns](#consumer-patterns)
- [Error Handling](#error-handling)
- [Partitioning](#partitioning)
- [Transactions](#transactions)

## Sending Messages

### Basic Transaction Message

```bash
curl -X POST http://localhost:8090/api/demo/send-simple \
  -H "Content-Type: application/json" \
  -d '{
    "sourceId": "ACC001",
    "targetId": "ACC002",
    "amount": 100.50,
    "currency": "USD",
    "transactionType": "TRANSFER",
    "description": "Monthly payment"
  }'
```

### Notification Message

```bash
curl -X POST http://localhost:8090/api/demo/send-notification \
  -H "Content-Type: application/json" \
  -d '{
    "recipientId": "USER123",
    "recipientEmail": "user@example.com",
    "title": "Account Alert",
    "content": "Your transaction has been processed",
    "notificationType": "INFO",
    "channel": "EMAIL"
  }'
```

## Producer Patterns

### 1. Simple Producer (Fire-and-Forget)

**Use Case**: Non-critical messages where occasional loss is acceptable

```bash
curl -X POST http://localhost:8090/api/demo/send-simple \
  -H "Content-Type: application/json" \
  -d '{"sourceId":"ACC100","targetId":"ACC200","amount":50.00,"currency":"USD"}'
```

**Characteristics:**
- ✅ Fastest performance
- ✅ No waiting for acknowledgment
- ⚠️ No delivery guarantee
- ⚠️ Message loss possible

### 2. Reliable Producer (Synchronous)

**Use Case**: Critical messages requiring confirmation

```bash
curl -X POST http://localhost:8090/api/demo/send-reliable \
  -H "Content-Type: application/json" \
  -d '{"sourceId":"ACC100","targetId":"ACC200","amount":1000.00,"currency":"USD"}'
```

**Characteristics:**
- ✅ Delivery guarantee
- ✅ Waits for acknowledgment
- ✅ Error detection
- ⚠️ Slower than async

### 3. Async Producer (Non-blocking with Callbacks)

**Use Case**: High-throughput scenarios with error handling

```bash
curl -X POST http://localhost:8090/api/demo/send-async \
  -H "Content-Type: application/json" \
  -d '{"sourceId":"ACC100","targetId":"ACC200","amount":250.00,"currency":"EUR"}'
```

**Characteristics:**
- ✅ Non-blocking
- ✅ High throughput
- ✅ Callback for success/failure
- ✅ Good balance of performance and reliability

### 4. Demonstrate All Patterns

Send the same message using all producer patterns:

```bash
curl -X POST http://localhost:8090/api/demo/demonstrate-all \
  -H "Content-Type: application/json" \
  -d '{"sourceId":"ACC999","targetId":"ACC888","amount":99.99,"currency":"USD"}'
```

## Consumer Patterns

### 1. Simple Consumer

**Location**: `SimpleConsumer.java`

**Behavior:**
- Consumes from `demo-messages` topic
- Processes one message at a time
- Automatic offset management

**View Logs:**
```
[SimpleConsumer] Received message: ID=..., Source=ACC001, Target=ACC002, Amount=100.50
```

### 2. Manual Acknowledgment Consumer

**Location**: `ManualAckConsumer.java`

**Behavior:**
- Consumes from `demo-transactions` topic
- Manual offset commit after successful processing
- Better error handling control

**Send Message:**
```bash
# Messages sent to demo-transactions will be consumed by ManualAckConsumer
```

### 3. Batch Consumer

**Location**: `BatchConsumer.java`

**Behavior:**
- Consumes from `demo-notifications` topic
- Processes multiple messages at once
- Improved throughput for bulk operations

**Send Multiple Notifications:**
```bash
# Send notification 1
curl -X POST http://localhost:8090/api/demo/send-notification \
  -H "Content-Type: application/json" \
  -d '{"recipientId":"U1","recipientEmail":"u1@example.com","title":"Alert 1","content":"Message 1"}'

# Send notification 2
curl -X POST http://localhost:8090/api/demo/send-notification \
  -H "Content-Type: application/json" \
  -d '{"recipientId":"U2","recipientEmail":"u2@example.com","title":"Alert 2","content":"Message 2"}'

# Send notification 3
curl -X POST http://localhost:8090/api/demo/send-notification \
  -H "Content-Type: application/json" \
  -d '{"recipientId":"U3","recipientEmail":"u3@example.com","title":"Alert 3","content":"Message 3"}'
```

**View Batch Processing:**
```
[BatchConsumer] Received batch of 3 messages
[BatchConsumer] Processing message 1 from partition 0 at offset 10
[BatchConsumer] Processing message 2 from partition 1 at offset 15
[BatchConsumer] Processing message 3 from partition 2 at offset 8
[BatchConsumer] Batch acknowledged successfully
```

### 4. Error Handling Consumer

**Location**: `ErrorHandlingConsumer.java`

**Behavior:**
- Retry mechanism with exponential backoff
- Dead Letter Queue (DLQ) for failed messages
- Graceful error handling

### 5. Grouped Consumers

**Location**: `GroupedConsumer.java`

**Behavior:**
- Multiple consumers in same group (load balancing)
- Multiple consumers in different groups (pub-sub)
- Partition rebalancing demonstration

## Error Handling

### Validation Error Example

```bash
# Missing required field (targetId)
curl -X POST http://localhost:8090/api/demo/send-simple \
  -H "Content-Type: application/json" \
  -d '{"sourceId":"ACC001","amount":100.50,"currency":"USD"}'
```

**Response:**
```json
{
  "success": false,
  "error": "Transaction validation failed"
}
```

### Invalid Amount Example

```bash
# Negative amount
curl -X POST http://localhost:8090/api/demo/send-simple \
  -H "Content-Type: application/json" \
  -d '{"sourceId":"ACC001","targetId":"ACC002","amount":-50.00,"currency":"USD"}'
```

### Invalid Email Example

```bash
# Invalid email format
curl -X POST http://localhost:8090/api/demo/send-notification \
  -H "Content-Type: application/json" \
  -d '{"recipientId":"U1","recipientEmail":"invalid-email","title":"Test","content":"Test"}'
```

## Partitioning

### Messages with Same Key (Same Partition)

All messages with the same key go to the same partition (ordering guaranteed):

```bash
# Message 1
curl -X POST http://localhost:8090/api/demo/send-simple \
  -H "Content-Type: application/json" \
  -d '{"sourceId":"ACC001","targetId":"ACC002","amount":100.00,"currency":"USD"}'

# Message 2 (same sourceId = same partition)
curl -X POST http://localhost:8090/api/demo/send-simple \
  -H "Content-Type: application/json" \
  -d '{"sourceId":"ACC001","targetId":"ACC003","amount":200.00,"currency":"USD"}'

# Message 3 (same sourceId = same partition)
curl -X POST http://localhost:8090/api/demo/send-simple \
  -H "Content-Type: application/json" \
  -d '{"sourceId":"ACC001","targetId":"ACC004","amount":300.00,"currency":"USD"}'
```

**Result in Kafka UI:**
- All three messages will be in the **same partition**
- **Order is guaranteed** within that partition
- Consumer will process them **in order**

### Messages with Different Keys (Different Partitions)

```bash
# Message 1
curl -X POST http://localhost:8090/api/demo/send-simple \
  -H "Content-Type: application/json" \
  -d '{"sourceId":"ACC001","targetId":"ACC002","amount":100.00,"currency":"USD"}'

# Message 2 (different sourceId)
curl -X POST http://localhost:8090/api/demo/send-simple \
  -H "Content-Type: application/json" \
  -d '{"sourceId":"ACC999","targetId":"ACC888","amount":200.00,"currency":"USD"}'
```

**Result:**
- Messages will likely be in **different partitions**
- Can be processed **in parallel** by multiple consumers
- No ordering guarantee **across** partitions

## Transactions

### TODO: Transaction Examples

```bash
# Send multiple messages in a single transaction
# TODO: Implement this endpoint in DemoController

curl -X POST http://localhost:8090/api/demo/send-transaction \
  -H "Content-Type: application/json" \
  -d '{
    "transactions": [
      {"sourceId":"ACC001","targetId":"ACC002","amount":100.00,"currency":"USD"},
      {"sourceId":"ACC002","targetId":"ACC003","amount":100.00,"currency":"USD"}
    ]
  }'
```

**Expected Behavior:**
- All messages committed together
- If one fails, all are rolled back
- Exactly-once semantics

## Monitoring Examples

### Check Application Health

```bash
curl http://localhost:8090/api/demo/health
```

### View Topics in Kafka UI

1. Open http://localhost:8080
2. Click "Topics"
3. Explore:
   - `demo-messages` (3 partitions)
   - `demo-transactions` (5 partitions)
   - `demo-notifications` (3 partitions)
   - `demo-dlq` (1 partition)

### View Consumer Groups

1. Open http://localhost:8080
2. Click "Consumers"
3. See consumer groups:
   - `simple-consumer-group`
   - `manual-ack-consumer-group`
   - `batch-consumer-group`
   - `error-handling-consumer-group`
   - `grouped-consumer-group-a`
   - `grouped-consumer-group-b`

### Check Consumer Lag

1. Go to "Consumers" in Kafka UI
2. Click on a consumer group
3. View lag per partition
4. Lag = 0 means consumer is up to date

## Advanced Examples

### Load Testing

Send multiple messages in quick succession:

```bash
for i in {1..100}; do
  curl -X POST http://localhost:8090/api/demo/send-async \
    -H "Content-Type: application/json" \
    -d "{\"sourceId\":\"ACC$i\",\"targetId\":\"ACC999\",\"amount\":$i.00,\"currency\":\"USD\"}"
done
```

### Different Currencies

```bash
# USD
curl -X POST http://localhost:8090/api/demo/send-simple \
  -H "Content-Type: application/json" \
  -d '{"sourceId":"ACC001","targetId":"ACC002","amount":100.00,"currency":"USD"}'

# EUR
curl -X POST http://localhost:8090/api/demo/send-simple \
  -H "Content-Type: application/json" \
  -d '{"sourceId":"ACC001","targetId":"ACC002","amount":100.00,"currency":"EUR"}'

# TRY
curl -X POST http://localhost:8090/api/demo/send-simple \
  -H "Content-Type: application/json" \
  -d '{"sourceId":"ACC001","targetId":"ACC002","amount":100.00,"currency":"TRY"}'
```

---

💡 **Tip**: Check application logs after each example to see detailed processing information!
