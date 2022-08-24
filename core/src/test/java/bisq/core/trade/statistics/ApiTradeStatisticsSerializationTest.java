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

import org.junit.Test;

import static bisq.core.trade.statistics.ApiTradeStatistics.RIPEMD160_HASH_LEN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


@Slf4j
public class ApiTradeStatisticsSerializationTest {

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


        try {
            File storageDir = Files.createTempDirectory("test-api-stats-storage-").toFile();
            CorruptedStorageFileHandler corruptedStorageFileHandler = new CorruptedStorageFileHandler();
            CorePersistenceProtoResolver protoResolver = new CorePersistenceProtoResolver(null, null);

            // Set up P2pDataStorage
            Config config = new Config();
            String postFix = "_TEST"; // "_" + config.baseCurrencyNetwork.name();
            Clock clock = Clock.systemDefaultZone();
            CoreNetworkProtoResolver coreNetworkProtoResolver = new CoreNetworkProtoResolver(clock);
            LocalhostNetworkNode networkNode = new LocalhostNetworkNode(10101, coreNetworkProtoResolver, null); // needs to be tor node?
            DefaultSeedNodeRepository seedNodeRepository = new DefaultSeedNodeRepository(config);
            PeerManager peerManager = new PeerManager(networkNode,
                    seedNodeRepository,
                    new ClockWatcher(),
                    new PersistenceManager<>(storageDir, protoResolver, corruptedStorageFileHandler),
                    5);
            Broadcaster broadcaster = new Broadcaster(networkNode, peerManager);

            // Set up ApiTradeStatisticsStorageService for P2pDataStorage.
            ApiTradeStatisticsStorageService apiTradeStatisticsStorageService = new ApiTradeStatisticsStorageService(storageDir,
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

            P2PDataStorage p2PDataStorage = new P2PDataStorage(networkNode,
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

            log.info("Persisting {} stats to {} ...", statsObjects.size(), storageDir.getAbsolutePath());
            long ts = System.currentTimeMillis();
            for (int i = 0; i < statsObjects.size(); i++) {
                var statsObject = statsObjects.get(i);
                p2PDataStorage.addPersistableNetworkPayload(statsObject,
                        networkNode.getNodeAddress(),
                        false,
                        false);
                if (i % 20_000 == 0) {
                    apiTradeStatisticsStorageService.persistNow();
                    Thread.sleep(500);
                }
            }
            apiTradeStatisticsStorageService.persistNow();
            Thread.sleep(2_000);
            p2PDataStorage.shutDown();

        } catch (IOException ex) {
            fail(ex.toString());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
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
        assertTrue(expected.equals(actual));
    }

    private List<ApiTradeStatistics> generateStats(int numStats) {
        Random random = new Random();
        List<ApiTradeStatistics> stats = new ArrayList<>();
        for (int i = 0; i < numStats; i++) {
            byte[] nextHash = new byte[RIPEMD160_HASH_LEN];
            random.nextBytes(nextHash);
            var tradeStatistics3Hash = nextHash;
            var isMakerApiUser = i % 2 == 0 ? false : true;
            var isTakerApiUser = isMakerApiUser ? i % 3 == 0 : true;
            stats.add(new ApiTradeStatistics(tradeStatistics3Hash, isMakerApiUser, isTakerApiUser));
        }
        return stats;
    }

    private List<protobuf.PersistableNetworkPayload> generatePersistableStatsProtos(int numStats) {
        return generateStats(numStats).stream()
                .map(s -> s.toProtoMessage())
                .collect(Collectors.toList());
    }
}
