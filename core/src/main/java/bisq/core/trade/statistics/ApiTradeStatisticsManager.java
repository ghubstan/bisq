/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.trade.statistics;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService;

import bisq.common.config.Config;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import javax.inject.Named;

import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;

import java.io.File;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class ApiTradeStatisticsManager {

    private final P2PService p2PService;
    private final ApiTradeStatisticsStorageService apiTradeStatisticsStorageService;
    private final TradeStatistics3StorageService tradeStatistics3StorageService;
    private final TradeStatisticsManager tradeStatisticsManager;
    private final File storageDir;
    private final boolean dumpStatistics;

    @Getter
    private final ObservableSet<ApiTradeStatistics> observableApiTradeStatisticsSet = FXCollections.observableSet();

    @Inject
    public ApiTradeStatisticsManager(P2PService p2PService,
                                     ApiTradeStatisticsStorageService apiTradeStatisticsStorageService,
                                     TradeStatistics3StorageService tradeStatistics3StorageService,
                                     TradeStatisticsManager tradeStatisticsManager,
                                     AppendOnlyDataStoreService appendOnlyDataStoreService,
                                     @Named(Config.STORAGE_DIR) File storageDir,
                                     @Named(Config.DUMP_STATISTICS) boolean dumpStatistics) {
        this.p2PService = p2PService;
        this.apiTradeStatisticsStorageService = apiTradeStatisticsStorageService;
        this.tradeStatistics3StorageService = tradeStatistics3StorageService;
        this.tradeStatisticsManager = tradeStatisticsManager;
        this.storageDir = storageDir;
        this.dumpStatistics = dumpStatistics;

        appendOnlyDataStoreService.addService(apiTradeStatisticsStorageService);
    }


    public void onAllServicesInitialized() {
        log.info("onAllServicesInitialized");

        // First, set up a ApiTradeStatistics listener for new API trading stats.
        p2PService.getP2PDataStorage().addAppendOnlyDataStoreListener(payload -> {
            if (payload instanceof ApiTradeStatistics) {
                ApiTradeStatistics apiTradeStatistics = (ApiTradeStatistics) payload;
                byte[] tradeStatistics3Hash = apiTradeStatistics.getTradeStatistics3Hash();
                TradeStatistics3 tradeStatistics3 = tradeStatisticsManager.findTradeStatistics3WithHash(tradeStatistics3Hash);
                if (!tradeStatistics3.isValid()) {
                    return;
                }
                apiTradeStatistics.setTradeStatistics3(tradeStatistics3);
                observableApiTradeStatisticsSet.add(apiTradeStatistics);
                maybeDumpStatistics();
            }
        });

        // Load existing ApiTradeStatistics storage.
        Set<ApiTradeStatistics> set = apiTradeStatisticsStorageService.getMapOfAllData().values().stream()
                .filter(e -> e instanceof ApiTradeStatistics)
                .map(e -> (ApiTradeStatistics) e)
                .filter(s -> s.getTradeStatistics3().isValid())
                .collect(Collectors.toSet());
        observableApiTradeStatisticsSet.addAll(set);

        maybeDumpStatistics();
    }

    // No JFX in API
    public List<ApiTradeStatistics> getApiTradeStatistics() {
        var array = observableApiTradeStatisticsSet.toArray(new ApiTradeStatistics[0]);
        return Arrays.asList(array);
    }

    public void maybeRepublishApiTradeStatistics(String whatDoIDoNow) {
        // See TradeManager L 449:
        //  tradeStatisticsManager.maybeRepublishTradeStatistics(allTrades, referralId, isTorNetworkNode);
    }

    // TODO The most basic reporting methods to make sure everything works on peers.

    public void shutDown() {
        // no op yet
    }

    private void maybeDumpStatistics() {
        if (!dumpStatistics) {
            return;
        }
    }

    public static void main(String[] args) {
        ApiTradeStatisticsManager mgr = new ApiTradeStatisticsManager(null,
                null,
                null,
                null,
                null,
                null,
                false);
        var list = mgr.getApiTradeStatistics();
        log.info("List size: {}", list.size());
    }
}
