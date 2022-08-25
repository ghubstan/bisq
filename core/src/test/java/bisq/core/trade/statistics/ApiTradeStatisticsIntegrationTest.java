package bisq.core.trade.statistics;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static bisq.core.trade.statistics.ApiTradeStatisticsTest.genApiTradeStatistics;
import static bisq.core.trade.statistics.ApiTradeStatisticsTest.verifyNoDuplicateTradeStatistics3Hashes;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Persisted store file sizes:
 * <p>
 * 10,000   ApiTradeStatistics:  245K
 *
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
        log.info("Persisting {} stats to {} ...",
                apiTradeStatisticsList.size(),
                app.getStorageDir().getAbsolutePath());

        long ts = System.currentTimeMillis();
        for (int i = 0; i < apiTradeStatisticsList.size(); i++) {
            ApiTradeStatistics statsObject = apiTradeStatisticsList.get(i);
            addOneApiTradeStatisticsPayload(statsObject);
        }
        log.info("Added {} {} payloads to in-memory storage in {} ms.",
                apiTradeStatisticsList.size(),
                ApiTradeStatistics.class.getSimpleName(),
                System.currentTimeMillis() - ts);

        persistApiTradeStatisticsPayloads();
        app.sleep(2_000); // Let disk write finish.

        app.shutdown();

        /////////////////////////////////////////////////////////////////////////////////////
        // TODO Test reading store file
        var persistenceManager = app.createPersistenceManager();
        var persistedEnvelope = persistenceManager.getPersisted("ApiTradeStatisticsStore");
        var statsMap = ((ApiTradeStatisticsStore) persistedEnvelope).getMap();
        assertEquals(NUM_TEST_STATS, statsMap.size());

        /////////////////////////////////////////////////////////////////////////////////////
        // TODO Test read persisted stats == original stats (proto v proto too)
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
