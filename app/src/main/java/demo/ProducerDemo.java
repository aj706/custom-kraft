package demo;

import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;

public class ProducerDemo {
    public static void main(String[] args) {
        /* 1. Topic and broker list */
        String topic   = args.length > 0 ? args[0] : "demo";
        String brokers = (args.length > 1)
                ? args[1]
                : System.getenv().getOrDefault(
                        "BROKERS",
                        "localhost:19092,localhost:19096,localhost:19097");

        System.out.printf("Producer starting | topic=%s  brokers=%s%n", topic, brokers);

        /* 2. Producer properties */
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 60_000);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 0);

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            for (int i = 0; i < 10; i++) {
                String msg = "message-" + i;
                ProducerRecord<String, String> rec = new ProducerRecord<>(topic, Integer.toString(i), msg);

                producer.send(rec, (RecordMetadata md, Exception ex) -> {
                    if (ex == null) {
                        System.out.printf("SUCCESS  %s  partition=%d offset=%d%n", msg, md.partition(), md.offset());
                    } else {
                        System.err.printf("FAILURE  %s  %s%n", msg, ex.getMessage());
                        ex.printStackTrace(System.err);
                    }
                });
                producer.flush();
            }
            System.out.println("Finished producing 10 messages.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
