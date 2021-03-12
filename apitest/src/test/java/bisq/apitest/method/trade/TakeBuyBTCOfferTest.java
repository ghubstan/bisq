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

package bisq.apitest.method.trade;

import bisq.core.payment.PaymentAccount;

import bisq.proto.grpc.BtcBalanceInfo;
import bisq.proto.grpc.TradeInfo;

import io.grpc.StatusRuntimeException;

import java.util.function.Predicate;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestMethodOrder;

import static bisq.cli.CurrencyFormat.formatSatoshis;
import static bisq.core.btc.wallet.Restrictions.getDefaultBuyerSecurityDepositAsPercent;
import static bisq.core.trade.Trade.Phase.DEPOSIT_CONFIRMED;
import static bisq.core.trade.Trade.Phase.DEPOSIT_PUBLISHED;
import static bisq.core.trade.Trade.Phase.FIAT_SENT;
import static bisq.core.trade.Trade.Phase.PAYOUT_PUBLISHED;
import static bisq.core.trade.Trade.State.*;
import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static protobuf.Offer.State.OFFER_FEE_PAID;
import static protobuf.OpenOffer.State.AVAILABLE;

@Disabled
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TakeBuyBTCOfferTest extends AbstractTradeTest {

    // Alice is buyer, Bob is seller.

    // Maker and Taker fees are in BSQ.
    private static final String TRADE_FEE_CURRENCY_CODE = "bsq";

    @Test
    @Order(1)
    public void testTakeAlicesBuyOffer(final TestInfo testInfo) {
        try {
            PaymentAccount alicesUsdAccount = createDummyF2FAccount(aliceClient, "US");
            var alicesOffer = aliceClient.createMarketBasedPricedOffer("buy",
                    "usd",
                    12500000,
                    12500000, // min-amount = amount
                    0.00,
                    getDefaultBuyerSecurityDepositAsPercent(),
                    alicesUsdAccount.getId(),
                    TRADE_FEE_CURRENCY_CODE);
            var offerId = alicesOffer.getId();
            assertFalse(alicesOffer.getIsCurrencyForMakerFeeBtc());

            // Wait for Alice's AddToOfferBook task.
            // Wait times vary;  my logs show >= 2 second delay.
            sleep(3000); // TODO loop instead of hard code wait time
            var alicesUsdOffers = aliceClient.getMyOffersSortedByDate("buy", "usd");
            assertEquals(1, alicesUsdOffers.size());

            PaymentAccount bobsUsdAccount = createDummyF2FAccount(bobClient, "US");
            var trade = takeAlicesOffer(offerId, bobsUsdAccount.getId(), TRADE_FEE_CURRENCY_CODE);
            assertNotNull(trade);
            assertEquals(offerId, trade.getTradeId());
            assertFalse(trade.getIsCurrencyForTakerFeeBtc());
            // Cache the trade id for the other tests.
            tradeId = trade.getTradeId();

            genBtcBlocksThenWait(1, 4000);
            alicesUsdOffers = aliceClient.getMyOffersSortedByDate("buy", "usd");
            assertEquals(0, alicesUsdOffers.size());

            if (!isLongRunningTest) {
                trade = bobClient.getTrade(trade.getTradeId());
                EXPECTED_PROTOCOL_STATUS.setState(SELLER_PUBLISHED_DEPOSIT_TX)
                        .setPhase(DEPOSIT_PUBLISHED)
                        .setDepositPublished(true);
                verifyExpectedProtocolStatus(trade);
                logTrade(log, testInfo, "Bob's view after taking offer and sending deposit", trade);
            }

            for (int i = 1; i <= (isLongRunningTest ? 5 : 2); i++) {
                genBtcBlocksThenWait(1, 2500);
                trade = bobClient.getTrade(trade.getTradeId());

                if (!trade.getIsDepositConfirmed()) {
                    log.info("Waiting for {}: DEPOSIT_CONFIRMED_IN_BLOCK_CHAIN, attempt # {}", trade.getDepositTxId(), i);
                    sleep(5000);
                    continue;
                }

                EXPECTED_PROTOCOL_STATUS.setState(DEPOSIT_CONFIRMED_IN_BLOCK_CHAIN)
                        .setPhase(DEPOSIT_CONFIRMED)
                        .setDepositConfirmed(true);
                verifyExpectedProtocolStatus(trade);
                logTrade(log, testInfo, "Bob's view after deposit is confirmed", trade);
                break;
            }

        } catch (StatusRuntimeException e) {
            fail(e);
        }
    }

    @Test
    @Order(2)
    public void testAlicesConfirmPaymentStarted(final TestInfo testInfo) {
        try {
            var trade = aliceClient.getTrade(tradeId);

            Predicate<TradeInfo> tradeStateAndPhaseCorrect = (t) ->
                    t.getState().equals(DEPOSIT_CONFIRMED_IN_BLOCK_CHAIN.name())
                            && t.getPhase().equals(DEPOSIT_CONFIRMED.name());
            for (int i = 1; i <= (isLongRunningTest ? 5 : 2); i++) {
                if (!tradeStateAndPhaseCorrect.test(trade)) {
                    log.error("INVALID_PHASE for trade in STATE={} PHASE={} before confirming payment started.",
                            trade.getState(), trade.getPhase());
                    // fail("Bad trade state and phase.");
                    sleep(1000 * 10);
                    trade = aliceClient.getTrade(tradeId);
                    continue;
                } else {
                    break;
                }
            }

            if (!tradeStateAndPhaseCorrect.test(trade)) {
                fail(format("INVALID_PHASE for trade in STATE=%s PHASE=%s before confirming payment received.",
                        trade.getState(),
                        trade.getPhase()));
            }

            aliceClient.confirmPaymentStarted(trade.getTradeId());
            sleep(6000);

            for (int i = 1; i <= (isLongRunningTest ? 5 : 2); i++) {
                trade = aliceClient.getTrade(tradeId);

                if (!trade.getIsFiatSent()) {
                    log.info("Waiting for BUYER_SAW_ARRIVED_FIAT_PAYMENT_INITIATED_MSG, attempt # {}", i);
                    sleep(5000);
                    continue;
                }

                assertEquals(OFFER_FEE_PAID.name(), trade.getOffer().getState());
                EXPECTED_PROTOCOL_STATUS.setState(BUYER_SAW_ARRIVED_FIAT_PAYMENT_INITIATED_MSG)
                        .setPhase(FIAT_SENT)
                        .setFiatSent(true);
                verifyExpectedProtocolStatus(trade);
                logTrade(log, testInfo, "Alice's view after confirming fiat payment sent", trade);
                break;

            }
        } catch (StatusRuntimeException e) {
            fail(e);
        }
    }

    @Test
    @Order(3)
    public void testBobsConfirmPaymentReceived(final TestInfo testInfo) {
        try {
            var trade = bobClient.getTrade(tradeId);

            Predicate<TradeInfo> tradeStateAndPhaseCorrect = (t) ->
                    t.getState().equals(SELLER_RECEIVED_FIAT_PAYMENT_INITIATED_MSG.name())
                            && (t.getPhase().equals(PAYOUT_PUBLISHED.name()) || t.getPhase().equals(FIAT_SENT.name()));

            for (int i = 1; i <= (isLongRunningTest ? 5 : 2); i++) {
                if (!tradeStateAndPhaseCorrect.test(trade)) {
                    log.error("INVALID_PHASE for trade in STATE={} PHASE={} before confirming payment received.",
                            trade.getState(), trade.getPhase());
                    // fail("Bad trade state and phase.");
                    sleep(1000 * 10);
                    trade = bobClient.getTrade(tradeId);
                    continue;
                } else {
                    break;
                }
            }

            if (!tradeStateAndPhaseCorrect.test(trade)) {
                fail(format("INVALID_PHASE for trade in STATE=%s PHASE=%s before confirming payment received.",
                        trade.getState(),
                        trade.getPhase()));
            }

            bobClient.confirmPaymentReceived(trade.getTradeId());
            sleep(3000);

            trade = bobClient.getTrade(tradeId);
            // Note: offer.state == available
            assertEquals(AVAILABLE.name(), trade.getOffer().getState());
            EXPECTED_PROTOCOL_STATUS.setState(SELLER_SAW_ARRIVED_PAYOUT_TX_PUBLISHED_MSG)
                    .setPhase(PAYOUT_PUBLISHED)
                    .setPayoutPublished(true)
                    .setFiatReceived(true);
            verifyExpectedProtocolStatus(trade);
            logTrade(log, testInfo, "Bob's view after confirming fiat payment received", trade);

        } catch (StatusRuntimeException e) {
            fail(e);
        }
    }

    @Test
    @Order(4)
    public void testAlicesKeepFunds(final TestInfo testInfo) {
        try {
            genBtcBlocksThenWait(1, 1000);

            var trade = aliceClient.getTrade(tradeId);
            logTrade(log, testInfo, "Alice's view before keeping funds", trade);

            aliceClient.keepFunds(tradeId);

            genBtcBlocksThenWait(1, 1000);

            trade = aliceClient.getTrade(tradeId);
            EXPECTED_PROTOCOL_STATUS.setState(BUYER_RECEIVED_PAYOUT_TX_PUBLISHED_MSG)
                    .setPhase(PAYOUT_PUBLISHED);
            verifyExpectedProtocolStatus(trade);
            logTrade(log, testInfo, "Alice's view after keeping funds", trade);
            BtcBalanceInfo currentBalance = aliceClient.getBtcBalances();
            log.info("{} Alice's current available balance: {} BTC",
                    testName(testInfo),
                    formatSatoshis(currentBalance.getAvailableBalance()));
        } catch (StatusRuntimeException e) {
            fail(e);
        }
    }
}
