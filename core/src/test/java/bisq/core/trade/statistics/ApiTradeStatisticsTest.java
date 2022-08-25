package bisq.core.trade.statistics;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static bisq.core.trade.statistics.ApiTradeStatistics.RIPEMD160_HASH_LEN;
import static bisq.core.trade.statistics.ApiTradeStatistics.SERIALIZED_BYTE_ARRAY_LEN;
import static org.junit.jupiter.api.Assertions.assertEquals;


@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ApiTradeStatisticsTest {

    private static final int NUM_TEST_STATS = 1_000_000;
    private static final int PERSISTABLE_NETWORK_PAYLOAD_SIZE = 25;
    private static List<ApiTradeStatistics> apiTradeStatisticsList;

    @BeforeAll
    public static void genTestStatistics() {
        apiTradeStatisticsList = genApiTradeStatistics(NUM_TEST_STATS);
    }

    @Order(1)
    @Test
    public void testApiTradeStatisticsSerialization() {
        for (ApiTradeStatistics apiTradeStatistics : apiTradeStatisticsList) {
            checkDeserialization(apiTradeStatistics);
        }
    }

    @Order(2)
    @Test
    public void testPersistableNetworkPayloadProtosDeserialization() {
        var persistableNetworkPayloadProtos = apiTradeStatisticsList.stream()
                .map(ApiTradeStatistics::toProtoMessage)
                .collect(Collectors.toList());
        assertEquals(NUM_TEST_STATS, persistableNetworkPayloadProtos.size());
        for (int i = 0; i < NUM_TEST_STATS; i++) {
            protobuf.PersistableNetworkPayload persistableNetworkPayloadProto = persistableNetworkPayloadProtos.get(i);
            ApiTradeStatistics expectedApiTradeStatistics = apiTradeStatisticsList.get(i);
            checkDeserialization(expectedApiTradeStatistics, persistableNetworkPayloadProto);
        }
    }

    // Private

    private void checkDeserialization(ApiTradeStatistics expectedApiTradeStatistics) {
        protobuf.PersistableNetworkPayload persistableNetworkPayloadProto = expectedApiTradeStatistics.toProtoMessage();
        checkDeserialization(expectedApiTradeStatistics, persistableNetworkPayloadProto);
    }

    private void checkDeserialization(ApiTradeStatistics expectedApiTradeStatistics,
                                      protobuf.PersistableNetworkPayload persistableNetworkPayloadProto) {
        assertEquals(PERSISTABLE_NETWORK_PAYLOAD_SIZE, persistableNetworkPayloadProto.getSerializedSize());
        assertEquals(PERSISTABLE_NETWORK_PAYLOAD_SIZE, persistableNetworkPayloadProto.toByteArray().length);

        protobuf.ApiTradeStatistics apiTradeStatisticsProto = persistableNetworkPayloadProto.getApiTradeStatistics();
        byte[] encodedApiStatisticsBytes = apiTradeStatisticsProto.getBytes().toByteArray();
        assertEquals(SERIALIZED_BYTE_ARRAY_LEN, encodedApiStatisticsBytes.length);
        
        ApiTradeStatistics actualApiTradeStatistics = new ApiTradeStatistics(encodedApiStatisticsBytes);
        assertEquals(expectedApiTradeStatistics, actualApiTradeStatistics);
    }

    private static List<ApiTradeStatistics> genApiTradeStatistics(int numStats) {
        Random random = new Random();
        List<ApiTradeStatistics> stats = new ArrayList<>();
        for (int i = 0; i < numStats; i++) {
            byte[] nextHash = new byte[RIPEMD160_HASH_LEN];
            random.nextBytes(nextHash);
            var isMakerApiUser = i % 2 != 0;
            // Must be true if !isMakerApiUser.
            var isTakerApiUser = !isMakerApiUser || i % 3 == 0;
            stats.add(new ApiTradeStatistics(nextHash, isMakerApiUser, isTakerApiUser));
        }
        return stats;
    }
}
