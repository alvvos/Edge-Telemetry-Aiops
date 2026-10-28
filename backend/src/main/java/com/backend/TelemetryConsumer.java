package com.backend;

import java.io.StringReader;
import java.io.IOException;
import java.net.URI;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

public class TelemetryConsumer implements Runnable {

    private final String kafkaUrl;
    private final String elasticUrl;

    public TelemetryConsumer() {
        String envKafka = System.getenv("KAFKA_URL");
        this.kafkaUrl = (envKafka != null && !envKafka.isBlank()) 
                ? envKafka 
                : System.getProperty("KAFKA_URL", "localhost:9092");

        String envElastic = System.getenv("ELASTIC_URL");
        this.elasticUrl = (envElastic != null && !envElastic.isBlank()) 
                ? envElastic 
                : System.getProperty("ELASTIC_URL", "http://localhost:9200");
    }

    @Override
    public void run() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, this.kafkaUrl);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "backend-consumer-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        URI elasticUri = URI.create(this.elasticUrl.startsWith("http") ? this.elasticUrl : "http://" + this.elasticUrl);
        String esHost = elasticUri.getHost();
        int esPort = elasticUri.getPort() != -1 ? elasticUri.getPort() : 9200;

        RestClient restClient = RestClient.builder(new HttpHost(esHost, esPort)).build();
        ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        ElasticsearchClient esClient = new ElasticsearchClient(transport);
        
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList("aiops_metrics"));
            System.out.println("Consumidor Kafka conectado ");

            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
                for (ConsumerRecord<String, String> record : records) {
                    try {
                        esClient.index(i -> i
                                .index("telemetry-metrics")
                                .withJson(new StringReader(record.value()))
                        );
                        System.out.printf("Caja recibida y almacenada [Offset %d]: %s%n", record.offset(), record.value());
                    } catch (IOException e) {
                        System.err.println("El camión de reparto se ha estrellado " + e.getMessage());
                    }
                }
            }
        }
    }
}