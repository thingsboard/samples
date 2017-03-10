package org.thingsboard.samples.spark;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.spark.SparkConf;
import org.apache.spark.streaming.Duration;
import org.apache.spark.streaming.api.java.JavaInputDStream;
import org.apache.spark.streaming.api.java.JavaStreamingContext;
import org.apache.spark.streaming.kafka010.ConsumerStrategies;
import org.apache.spark.streaming.kafka010.KafkaUtils;
import org.apache.spark.streaming.kafka010.LocationStrategies;
import org.eclipse.paho.client.mqttv3.*;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;


public class KafkaStreamingDemoMain {

    public static void main(String[] args) throws Exception {
        new StreamRunner().start();
    }

    @Slf4j
    private static class StreamRunner {

        private static final String AVERAGE_DEVICE_ACCESS_TOKEN = "h8mLQPqbYOot6F8kz1oj";
        private static final String KAFKA_BROKER_LIST = "localhost:9092";
        private static final String THINGSBOARD_MQTT_ENDPOINT = "tcp://localhost:1883";
        private static final int STREAM_WINDOW_MILLISECONDS = 10000; // 10 seconds
        private final MqttAsyncClient client;
        private Collection<String> topics = Arrays.asList("sensors-telemetry");

        StreamRunner() throws MqttException {
            client = new MqttAsyncClient(THINGSBOARD_MQTT_ENDPOINT, MqttAsyncClient.generateClientId());
        }

        void start() throws Exception {
            SparkConf conf = new SparkConf().setAppName("Kafka Streaming App").setMaster("local[2]");

            try (JavaStreamingContext ssc = new JavaStreamingContext(conf, new Duration(STREAM_WINDOW_MILLISECONDS))) {

                connectToThingsboard();

                JavaInputDStream<ConsumerRecord<String, String>> stream =
                        KafkaUtils.createDirectStream(
                                ssc,
                                LocationStrategies.PreferConsistent(),
                                ConsumerStrategies.<String, String>Subscribe(topics, getKafkaParams())
                        );

                stream.foreachRDD(rdd ->
                {
                    OptionalDouble averageTemp = rdd.mapToDouble(r ->
                            Double.valueOf(r.value())
                    ).collect().stream().mapToDouble(a -> a).average();

                    if (averageTemp.isPresent()) {
                        String mqttMsg = "{\"temperature\":" + averageTemp.getAsDouble() + "}";
                        publishTelemetryToThingsboard(mqttMsg);
                    }
                });

                ssc.start();
                ssc.awaitTermination();
            }
        }

        private static Map<String, Object> getKafkaParams() {
            Map<String, Object> kafkaParams = new HashMap<>();
            kafkaParams.put("bootstrap.servers", KAFKA_BROKER_LIST);
            kafkaParams.put("key.deserializer", StringDeserializer.class);
            kafkaParams.put("value.deserializer", StringDeserializer.class);
            kafkaParams.put("group.id", "DEFAULT_GROUP_ID");
            kafkaParams.put("auto.offset.reset", "latest");
            kafkaParams.put("enable.auto.commit", false);
            return kafkaParams;
        }

        private void connectToThingsboard() throws Exception {
            MqttConnectOptions options = new MqttConnectOptions();
            options.setUserName(AVERAGE_DEVICE_ACCESS_TOKEN);
            try {
                client.connect(options, null, new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken iMqttToken) {
                        log.info("Connected to Thingsboard!");
                    }

                    @Override
                    public void onFailure(IMqttToken iMqttToken, Throwable e) {
                        log.error("Failed to connect to Thingsboard!", e);
                    }
                }).waitForCompletion();
            } catch (MqttException e) {
                log.error("Failed to connect to the server", e);
            }
        }

        private void publishTelemetryToThingsboard(String mqttMsg) throws Exception {
            MqttMessage msg = new MqttMessage(mqttMsg.getBytes(StandardCharsets.UTF_8));
            client.publish("v1/devices/me/telemetry", msg, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    log.trace("Telemetry updated!");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    log.error("Telemetry update failed!", exception);
                }
            });
        }
    }
}