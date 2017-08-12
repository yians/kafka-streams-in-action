package bbejeck.chapter_6;


import bbejeck.chapter_6.processor.MapValueProcessor;
import bbejeck.chapter_6.processor.KStreamPrinter;
import bbejeck.clients.producer.MockDataProducer;
import bbejeck.model.Purchase;
import bbejeck.model.PurchasePattern;
import bbejeck.model.RewardAccumulator;
import bbejeck.util.serde.StreamsSerdes;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.processor.TopologyBuilder;
import org.apache.kafka.streams.processor.WallclockTimestampExtractor;
import static org.apache.kafka.streams.processor.TopologyBuilder.AutoOffsetReset.*;

import java.util.Properties;

public class ZMartProcessorApp {


    public static void main(String[] args) throws Exception {
        MockDataProducer.producePurchaseData();


        StreamsConfig streamsConfig = new StreamsConfig(getProperties());
        Deserializer<String> stringDeserializer = Serdes.String().deserializer();
        Serializer<String> stringSerializer = Serdes.String().serializer();
        Serde<Purchase> purchaseSerde = StreamsSerdes.PurchaseSerde();
        Deserializer<Purchase> purchaseDeserializer = purchaseSerde.deserializer();
        Serializer<Purchase> purchaseSerializer = purchaseSerde.serializer();
        Serializer<PurchasePattern> patternSerializer = StreamsSerdes.PurchasePatternSerde().serializer();
        Serializer<RewardAccumulator> rewardsSerializer = StreamsSerdes.RewardAccumulatorSerde().serializer();

        TopologyBuilder builder = new TopologyBuilder();

        MapValueProcessor<String, Purchase, Purchase> maskingProcessor = new MapValueProcessor<>(p -> Purchase.builder(p).maskCreditCard().build());
        MapValueProcessor<String, Purchase, RewardAccumulator> rewardProcessor = new MapValueProcessor<>(purchase -> RewardAccumulator.builder(purchase).build());
        MapValueProcessor<String, Purchase, PurchasePattern> patternProcessor = new MapValueProcessor<>(purchase -> PurchasePattern.builder(purchase).build());

        builder.addSource("txn-source", stringDeserializer, purchaseDeserializer, "transactions")
                .addProcessor("masking-processor", () -> maskingProcessor, "txn-source")
                .addProcessor("rewards-processor", () -> rewardProcessor, "txn-source")
                .addProcessor("patterns-processor", () -> patternProcessor, "txn-source")
                .addSink("purchase-sink", "purchases", stringSerializer, purchaseSerializer, "masking-processor")
                .addSink("rewards-sink", "rewards", stringSerializer, rewardsSerializer, "rewards-processor")
                .addSink("patterns-sink", "patterns", stringSerializer, patternSerializer, "patterns-processor");


        builder.addProcessor("purchase-printer", new KStreamPrinter("purchase"), "masking-processor")
                .addProcessor("rewards-printer", new KStreamPrinter("rewards"), "rewards-processor")
                .addProcessor("patterns-printer", new KStreamPrinter("pattens"), "patterns-processor");

        KafkaStreams kafkaStreams = new KafkaStreams(builder, streamsConfig);
        System.out.println("ZMart Processor App Started");
        kafkaStreams.start();
        Thread.sleep(35000);
        System.out.println("Shutting down the ZMart Processor App now");
        kafkaStreams.close();
        MockDataProducer.shutdown();
    }

    private static Properties getProperties() {
        Properties props = new Properties();
        props.put(StreamsConfig.CLIENT_ID_CONFIG, "zmart-processor-client");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "zmart-processor-group");
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "zmart-processor-appid");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(StreamsConfig.REPLICATION_FACTOR_CONFIG, 1);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.put(StreamsConfig.DEFAULT_TIMESTAMP_EXTRACTOR_CLASS_CONFIG, WallclockTimestampExtractor.class);
        return props;
    }
}