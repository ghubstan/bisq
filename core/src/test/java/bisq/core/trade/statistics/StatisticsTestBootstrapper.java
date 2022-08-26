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
import bisq.common.file.FileUtil;
import bisq.common.persistence.PersistenceManager;

import java.time.Clock;

import java.net.ServerSocket;

import java.nio.file.Files;

import java.io.File;
import java.io.IOException;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * P2P storage bootstrapper for testing storage file read & write.
 *
 * TODO Rename, refactor, and move to different pkg when needed by other core test cases.
 */
@Slf4j
@Getter
class StatisticsTestBootstrapper {

    private final File storageDir;
    private final Config config;
    private final String storeFilenamePostFix = "_TEST"; // or = "_" + config.baseCurrencyNetwork.name();
    private final Clock clock;
    private final LocalhostNetworkNode networkNode;
    private final P2PDataStorage p2PDataStorage;
    private final ApiTradeStatisticsStorageService apiTradeStatisticsStorageService;
    private final TradeStatistics3StorageService tradeStatistics3StorageService;
    private final CorruptedStorageFileHandler corruptedStorageFileHandler;
    private final CoreNetworkProtoResolver coreNetworkProtoResolver;
    private final CorePersistenceProtoResolver corePersistenceProtoResolver;
    private final DefaultSeedNodeRepository seedNodeRepository;
    private final PeerManager peerManager;
    private final Broadcaster broadcaster;
    private final AppendOnlyDataStoreService appendOnlyDataStoreService;
    private final ProtectedDataStoreService protectedDataStoreService;
    private final ResourceDataStoreService resourceDataStoreService;
    private final RemovedPayloadsService removedPayloadsService;
    private final int maxSequenceNumberBeforePurge = 5;

    StatisticsTestBootstrapper() {
        this.config = new Config();
        this.clock = Clock.systemDefaultZone();
        this.storageDir = createStorageDir();
        this.corruptedStorageFileHandler = new CorruptedStorageFileHandler();
        this.coreNetworkProtoResolver = new CoreNetworkProtoResolver(clock);
        this.corePersistenceProtoResolver = new CorePersistenceProtoResolver(null, coreNetworkProtoResolver);
        this.networkNode = new LocalhostNetworkNode(getRandomPort(), coreNetworkProtoResolver, null);
        this.seedNodeRepository = new DefaultSeedNodeRepository(config);
        this.peerManager = new PeerManager(networkNode,
                seedNodeRepository,
                new ClockWatcher(),
                new PersistenceManager<>(storageDir, corePersistenceProtoResolver, corruptedStorageFileHandler),
                5);
        this.broadcaster = new Broadcaster(networkNode, peerManager);
        this.protectedDataStoreService = new ProtectedDataStoreService();
        this.resourceDataStoreService = new ResourceDataStoreService();
        this.removedPayloadsService = new RemovedPayloadsService(
                new PersistenceManager<>(storageDir, corePersistenceProtoResolver, corruptedStorageFileHandler));

        // TODO

        this.tradeStatistics3StorageService = new TradeStatistics3StorageService(storageDir,
                new PersistenceManager<>(storageDir, corePersistenceProtoResolver, corruptedStorageFileHandler));

        this.apiTradeStatisticsStorageService = new ApiTradeStatisticsStorageService(storageDir,
                new PersistenceManager<>(storageDir, corePersistenceProtoResolver, corruptedStorageFileHandler));

        this.appendOnlyDataStoreService = new AppendOnlyDataStoreService();
        appendOnlyDataStoreService.addService(tradeStatistics3StorageService);
        appendOnlyDataStoreService.addService(apiTradeStatisticsStorageService);

        this.p2PDataStorage = new P2PDataStorage(networkNode,
                broadcaster,
                appendOnlyDataStoreService,
                protectedDataStoreService,
                resourceDataStoreService,
                new PersistenceManager<>(storageDir, corePersistenceProtoResolver, corruptedStorageFileHandler),
                removedPayloadsService,
                clock,
                maxSequenceNumberBeforePurge);
    }

    public void init() {
        appendOnlyDataStoreService.readFromResources(storeFilenamePostFix, () -> {
        });
        PersistenceManager.onAllServicesInitialized();
        sleep(3_000); // Let service threads finish.  (TODO use param?)
    }

    // Create a persistence mgr using the StatisticsTestBootstrapper instance storageDir.
    public PersistenceManager createPersistenceManager() {
        return createPersistenceManager(storageDir);
    }

    // Create a persistence mgr using the given storageDir.
    public PersistenceManager createPersistenceManager(File storageDir) {
        return new PersistenceManager<>(storageDir, corePersistenceProtoResolver, corruptedStorageFileHandler);
    }


    public void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            // ignored
        }
    }

    public void shutdown() {
        log.info("Shutting down " + p2PDataStorage.getClass().getSimpleName());
        // TODO Does anything else need to be explicitly shut down?
        p2PDataStorage.shutDown();
        log.info("Shut down");

        try {
            log.info("Deleting storage directory {}", storageDir.getAbsolutePath());
            FileUtil.deleteDirectory(storageDir);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private File createStorageDir() {
        try {
            return Files.createTempDirectory("bisq-test-storage-").toFile();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private int getRandomPort() {
        try {
            return new ServerSocket(0).getLocalPort();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}
