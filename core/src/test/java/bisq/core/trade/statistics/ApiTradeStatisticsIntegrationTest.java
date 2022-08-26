package bisq.core.trade.statistics;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static bisq.core.trade.statistics.ApiTradeStatisticsTest.genApiTradeStatistics;
import static bisq.core.trade.statistics.ApiTradeStatisticsTest.verifyNoDuplicateTradeStatistics3Hashes;
import static bisq.network.p2p.storage.P2PDataStorage.ByteArray;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Persisted store file sizes:
 * <p>
 * 10,000       ApiTradeStatistics:     245K
 * 100,000      ApiTradeStatistics:     2.4M        ~ 5 minutes
 * ~ 300,00  ~ 7.3M
 * 395000    ~ 9.5M
 * 449999    ~ 11M
 * 529999    ~ 13M
 * 599999    ~ 15M
 * 669999    ~ 16M
 * 724999    ~ 18M
 * 739999    ~ 18M
 * 879999    ~ 22M
 * 1,000,000    ApiTradeStatistics:     ~ 25M
 * Persisting 1,000,000 took 75324709 ms (20.92 hours, with artificial waits for file writes)
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ApiTradeStatisticsIntegrationTest {

    // TODO Generate TradeStatistics3 data instead, generate ApiTradeStatistics from them,
    //  addOneApiTradeStatisticsPayload both, read both, and see if join works.

    private static final int NUM_TEST_STATS = 10_000;
    private static List<ApiTradeStatistics> apiTradeStatisticsList;
    private static StatisticsTestBootstrapper app;  // For brevity, name it 'app'.

    @BeforeAll
    public static void bootstrap() {
        apiTradeStatisticsList = genApiTradeStatistics(NUM_TEST_STATS);
        verifyNoDuplicateTradeStatistics3Hashes(apiTradeStatisticsList);

        app = new StatisticsTestBootstrapper();
        app.init();
    }

    @Order(1)
    @Test
    public void testWriteToDisk() {
        log.info("Persisting {} {} to {} ...",
                apiTradeStatisticsList.size(),
                ApiTradeStatistics.class.getSimpleName(),
                app.getStorageDir().getAbsolutePath());

        long ts = System.currentTimeMillis();
        for (int listIdx = 0; listIdx < apiTradeStatisticsList.size(); listIdx++) {
            ApiTradeStatistics statsObject = apiTradeStatisticsList.get(listIdx);
            addOneApiTradeStatisticsPayload(statsObject);

            if ((listIdx != 0) && (listIdx % 5_000 == 0)) {
                persistApiTradeStatisticsPayloads();
                app.sleep(2_000); // Let disk write finish.
                log.info("Persisted {} {}...", listIdx - 1, ApiTradeStatistics.class.getSimpleName());
            }
        }
        persistApiTradeStatisticsPayloads();
        app.sleep(2_000); // Let disk write finish.
        log.info("Persisted {} {} payloads to {} in ~ {} ms.",
                apiTradeStatisticsList.size(),
                app.getStorageDir().getAbsolutePath(),
                ApiTradeStatistics.class.getSimpleName(),
                System.currentTimeMillis() - ts);
    }
    
    @Order(2)
    @Test
    public void testReadApiTradeStatisticsStoreFile() {
        var persistenceManager = app.createPersistenceManager();
        var persistedEnvelope = persistenceManager.getPersisted("ApiTradeStatisticsStore");

        var persistableNetworkPayloadByByteArrayMap = ((ApiTradeStatisticsStore) persistedEnvelope).getMap();
        assertEquals(NUM_TEST_STATS, persistableNetworkPayloadByByteArrayMap.size(), "Incorrect persisted payload count.");

        persistableNetworkPayloadByByteArrayMap.forEach((k, v) -> {
            assertTrue(apiTradeStatisticsList.contains(v), "Did not find persisted object in generated list.");
        });

        for (ApiTradeStatistics expectedApiTradeStatistics : apiTradeStatisticsList) {
            ByteArray expectedByteArray = new ByteArray(expectedApiTradeStatistics.getTradeStatistics3Hash());
            var actualApiTradeStatistics = persistableNetworkPayloadByByteArrayMap.get(expectedByteArray);
            assertNotNull(actualApiTradeStatistics, "Did not find generated list entry in persisted payloads.");
            assertEquals(expectedApiTradeStatistics, actualApiTradeStatistics, "Generated stats != persisted stats.");
            ByteArray actualByteArray = new ByteArray(actualApiTradeStatistics.getHash());
            assertEquals(expectedByteArray, actualByteArray, "ByteArrays do not match.");
        }
    }

    @AfterAll
    public static void shutdown() {
        app.shutdown();
    }

    private void addOneApiTradeStatisticsPayload(ApiTradeStatistics apiTradeStatistics) {
        app.getP2PDataStorage().addPersistableNetworkPayload(apiTradeStatistics,
                app.getNetworkNode().getNodeAddress(),
                false,
                false);
    }

    private void persistApiTradeStatisticsPayloads() {
        app.getApiTradeStatisticsStorageService().persistNow();
    }
}
