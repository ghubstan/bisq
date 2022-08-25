package bisq.core.trade.statistics;

import bisq.core.network.p2p.seed.DefaultSeedNodeRepository;
import bisq.core.proto.network.CoreNetworkProtoResolver;
import bisq.core.proto.persistable.CorePersistenceProtoResolver;

import bisq.network.p2p.network.LocalhostNetworkNode;
import bisq.network.p2p.peers.Broadcaster;
import bisq.network.p2p.peers.PeerManager;
import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService;
import bisq.network.p2p.storage.persistence.ProtectedDataStoreService;
import bisq.network.p2p.storage.persistence.RemovedPayloadsService;
import bisq.network.p2p.storage.persistence.ResourceDataStoreService;

import bisq.common.ClockWatcher;
import bisq.common.config.Config;
import bisq.common.file.CorruptedStorageFileHandler;
import bisq.common.persistence.PersistenceManager;

import java.time.Clock;

import java.nio.file.Files;

import java.io.File;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.junit.BeforeClass;
import org.junit.Test;

import static bisq.core.trade.statistics.ApiTradeStatistics.RIPEMD160_HASH_LEN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;


@Slf4j
public class ApiTradeStatisticsSerializationTest {

    private static Config config = new Config();
    private static String postFix = "_TEST"; // "_" + config.baseCurrencyNetwork.name();
    private static LocalhostNetworkNode networkNode;
    private static File storageDir;
    private static P2PDataStorage p2PDataStorage = null;
    private static ApiTradeStatisticsStorageService apiTradeStatisticsStorageService = null;
    private static CorruptedStorageFileHandler corruptedStorageFileHandler;
    private static CorePersistenceProtoResolver protoResolver = new CorePersistenceProtoResolver(null, null);

    @BeforeClass
    public static void setUpStorage() {
        try {
            storageDir = Files.createTempDirectory("test-api-stats-storage-").toFile();
            corruptedStorageFileHandler = new CorruptedStorageFileHandler();
            protoResolver = new CorePersistenceProtoResolver(null, null);
            Clock clock = Clock.systemDefaultZone();
            CoreNetworkProtoResolver coreNetworkProtoResolver = new CoreNetworkProtoResolver(clock);
            networkNode = new LocalhostNetworkNode(11111, coreNetworkProtoResolver, null);
            DefaultSeedNodeRepository seedNodeRepository = new DefaultSeedNodeRepository(config);
            PeerManager peerManager = new PeerManager(networkNode,
                    seedNodeRepository,
                    new ClockWatcher(),
                    new PersistenceManager<>(storageDir, protoResolver, corruptedStorageFileHandler),
                    5);
            Broadcaster broadcaster = new Broadcaster(networkNode, peerManager);
            // Set up ApiTradeStatisticsStorageService
            apiTradeStatisticsStorageService = new ApiTradeStatisticsStorageService(storageDir,
                    new PersistenceManager<>(storageDir, protoResolver, corruptedStorageFileHandler));
            AppendOnlyDataStoreService appendOnlyDataStoreService = new AppendOnlyDataStoreService();
            appendOnlyDataStoreService.addService(apiTradeStatisticsStorageService);
            appendOnlyDataStoreService.readFromResources(postFix, () -> {
            });
            ProtectedDataStoreService protectedDataStoreService = new ProtectedDataStoreService();
            ResourceDataStoreService resourceDataStoreService = new ResourceDataStoreService();
            RemovedPayloadsService removedPayloadsService = new RemovedPayloadsService(
                    new PersistenceManager<>(storageDir, protoResolver, corruptedStorageFileHandler));
            int maxSequenceNumberBeforePurge = 5;
            p2PDataStorage = new P2PDataStorage(networkNode,
                    broadcaster,
                    appendOnlyDataStoreService,
                    protectedDataStoreService,
                    resourceDataStoreService,
                    new PersistenceManager<>(storageDir, protoResolver, corruptedStorageFileHandler),
                    removedPayloadsService,
                    clock,
                    maxSequenceNumberBeforePurge);
            // p2PDataStorage.readFromResourcesSync(postFix);
            // p2PDataStorage.readPersistedSync();
            PersistenceManager.onAllServicesInitialized();
        } catch (IOException ex) {
            fail(ex.toString());
        }
    }

    @Test
    public void testSerialize() {
        ApiTradeStatistics apiTradeStatistics = new ApiTradeStatistics(new byte[20], true, false);
        checkProtoSize(apiTradeStatistics);

        apiTradeStatistics = new ApiTradeStatistics(new byte[20], false, true);
        checkProtoSize(apiTradeStatistics);

        apiTradeStatistics = new ApiTradeStatistics(new byte[20], true, true);
        checkProtoSize(apiTradeStatistics);
    }

    @Test
    public void testDeserialize() {
        ApiTradeStatistics expected = new ApiTradeStatistics(new byte[20], true, false);
        checkDeserialization(expected);

        expected = new ApiTradeStatistics(new byte[20], false, true);
        checkDeserialization(expected);

        expected = new ApiTradeStatistics(new byte[20], true, true);
        checkDeserialization(expected);
    }

    @Test
    public void testWriteToDisk() {
        var numStats = 1_000_000;
        List<ApiTradeStatistics> statsObjects = generateStats(numStats); // not protos

        log.info("Persisting {} stats to {} ...", statsObjects.size(), storageDir.getAbsolutePath());
        long ts = System.currentTimeMillis();
        for (int i = 0; i < statsObjects.size(); i++) {
            var statsObject = statsObjects.get(i);
            p2PDataStorage.addPersistableNetworkPayload(statsObject,
                    networkNode.getNodeAddress(),
                    false,
                    false);
            if (i != 0 && i % 10_000 == 0) {
                apiTradeStatisticsStorageService.persistNow();
                log.info("{} api stats are being persisted. Elapsed Time: {} ms.",
                        i,
                        System.currentTimeMillis() - ts);
                sleep(5_000);
            }
        }
        apiTradeStatisticsStorageService.persistNow();
        sleep(5_000);
        log.info("Persisted {} stats to {} in {} ms.",
                statsObjects.size(),
                storageDir.getAbsolutePath(),
                System.currentTimeMillis() - ts);
        p2PDataStorage.shutDown();

        // TODO Test reading store file

        /////////////////////////////////////////////////////////////////////////////////////
        // This is how to read the persisted ApiTradeStatistics proto field:  bytes (21 bytes).
        var persistenceManager = new PersistenceManager<>(storageDir, protoResolver, corruptedStorageFileHandler);
        var persistedEnvelope = persistenceManager.getPersisted("ApiTradeStatisticsStore");
        var statsMap = ((ApiTradeStatisticsStore) persistedEnvelope).getMap();
        assertEquals(numStats, statsMap.size());
        /////////////////////////////////////////////////////////////////////////////////////

        // TODO Test read persisted stats == original stats (proto v proto too)
        /////////////////////////////////////////////////////////////////////////////////////
        /////////////////////////////////////////////////////////////////////////////////////
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            // ignored
        }
    }

    @Test
    public void testDeserializeProtos() {
        var numStats = 1_000_000;
        var persistableStatsPayloads = generatePersistableStatsProtos(numStats);
        assertEquals(numStats, persistableStatsPayloads.size());
    }

    // Private

    private void checkProtoSize(ApiTradeStatistics apiTradeStatistics) {
        var proto = protobuf.PersistableNetworkPayload.newBuilder()
                .setApiTradeStatistics(apiTradeStatistics.getBuilder())
                .build();
        assertEquals(25, proto.toByteArray().length);
        assertEquals(25, proto.getSerializedSize());
    }

    private void checkDeserialization(ApiTradeStatistics expected) {
        protobuf.PersistableNetworkPayload networkPayloadProto = expected.toProtoMessage();
        protobuf.ApiTradeStatistics proto = networkPayloadProto.getApiTradeStatistics();
        byte[] protoBytes = proto.getBytes().toByteArray();
        ApiTradeStatistics actual = new ApiTradeStatistics(protoBytes);
        assertEquals(expected, actual);
    }

    private List<ApiTradeStatistics> generateStats(int numStats) {
        Random random = new Random();
        List<ApiTradeStatistics> stats = new ArrayList<>();
        for (int i = 0; i < numStats; i++) {
            byte[] nextHash = new byte[RIPEMD160_HASH_LEN];
            random.nextBytes(nextHash);
            var isMakerApiUser = i % 2 != 0;
            var isTakerApiUser = !isMakerApiUser || i % 3 == 0;
            stats.add(new ApiTradeStatistics(nextHash, isMakerApiUser, isTakerApiUser));
        }
        return stats;
    }

    private List<protobuf.PersistableNetworkPayload> generatePersistableStatsProtos(int numStats) {
        return generateStats(numStats).stream()
                .map(ApiTradeStatistics::toProtoMessage)
                .collect(Collectors.toList());
    }
}
