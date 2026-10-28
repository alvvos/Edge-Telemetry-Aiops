import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import com.backend.TelemetryConsumer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TelemetryIntegrationTest {

    private static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.1"));
    private static final ElasticsearchContainer elastic = new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:8.10.2")
            .withEnv("discovery.type", "single-node")
            .withEnv("xpack.security.enabled", "false");

    @BeforeAll
    static void startContainers() {
        kafka.start();
        elastic.start();
        System.setProperty("KAFKA_URL", kafka.getBootstrapServers());
        System.setProperty("ELASTIC_URL", "http://" + elastic.getHttpHostAddress());
    }

    @Test
    void testEndToEndTelemetryFlow() throws Exception {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        
        KafkaProducer<String, String> producer = new KafkaProducer<>(props);

        String payloadAIOps = """
            {
                "cpu_percent": 85.5,
                "ram_percent": 60.2,
                "gpu_temp_c": 75.0,
                "aiops": {
                    "cpu_predicted": 40.0,
                    "cpu_lower_bound": 35.0,
                    "cpu_upper_bound": 45.0,
                    "is_anomaly": true
                }
            }
            """;

        producer.send(new ProducerRecord<>("aiops_metrics", "windows-host", payloadAIOps)).get();
        producer.close();

        TelemetryConsumer consumer = new TelemetryConsumer();
        new Thread(consumer).start();
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + elastic.getHttpHostAddress() + "/aiops_metrics/_search"))
                .header("Content-Type", "application/json")
                .GET()
                .build();

        boolean documentFound = false;
        
        for (int i = 0; i < 10; i++) {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200 && response.body().contains("\"is_anomaly\":true")) {
                documentFound = true;
                break;
            }
            Thread.sleep(1000);
        }

        assertTrue(documentFound, "El documento AIOps no llegó a Elasticsearch en el tiempo esperado.");
    }

    @AfterAll
    static void stopContainers() {
        kafka.stop();
        elastic.stop();
    }
}