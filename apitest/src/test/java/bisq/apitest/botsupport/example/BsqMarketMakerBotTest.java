package bisq.apitest.botsupport.example;

import bisq.proto.grpc.OfferInfo;

import protobuf.PaymentAccount;

import io.grpc.StatusRuntimeException;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import static bisq.apitest.botsupport.protocol.BotProtocol.BTC;
import static bisq.apitest.config.ApiTestConfig.BSQ;
import static bisq.cli.CurrencyFormat.formatBsqAmount;
import static bisq.cli.TableFormat.formatBalancesTbls;
import static bisq.cli.TableFormat.formatOfferTable;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static protobuf.OfferPayload.Direction.BUY;
import static protobuf.OfferPayload.Direction.SELL;



import bisq.apitest.botsupport.BotClient;
import bisq.cli.GrpcClient;
import bisq.cli.TradeFormat;

// Requirements:  test harness, registered agents, BsqMarketMakerBot.
//
// $ ./bisq-apitest --apiPassword=xyz --supportingApps=bitcoind,seednode,arbdaemon,alicedaemon,bobdaemon  --shutdownAfterTests=false --enableBisqDebugging=false
// $ ./bisq-bot --password=xyz --port=9998 bsqmarketmaker --target-btc-amount=0.005 --target-price=0.00004 --target-spread=13.00 --trade-cycle-limit=10 --new-payment-accts-limit=10
//
//
// $ ./bisq-bot --password=xyz --port=9998 bsqmarketmaker --target-btc-amount=0.05 --target-price=0.00004 --target-spread=13.00 --trade-cycle-limit=5 --new-payment-accts-limit=10
// $ ./bisq-bot --password=xyz --port=9998 bsqmarketmaker --target-btc-amount=0.05 --target-price=0.00004 --target-spread=13.00 --trade-cycle-limit=20 --new-payment-accts-limit=20
// Only use the one below without the the bloom filter hack.
// $ ./bisq-bot --password=xyz --port=9998 bsqmarketmaker --target-btc-amount=0.05 --target-price=0.00004 --target-spread=13.00 --trade-cycle-limit=100 --new-payment-accts-limit=30
//
@Slf4j
public class BsqMarketMakerBotTest {

    // TODO Make this an option
    protected static final boolean USE_CALL_DELAY_INTERCEPTOR = true;

    // TODO Install shutdown hook, check state.

    private static final int MAX_TRADE_CYCLES = 1;

    private final BotClient botClient;
    private final PaymentAccount paymentAccount;
    private final List<OfferInfo> offers;

    public BsqMarketMakerBotTest() {
        this.botClient = new BotClient(new GrpcClient("localhost", 9999, "xyz", USE_CALL_DELAY_INTERCEPTOR));
        this.paymentAccount = botClient.createCryptoCurrencyPaymentAccount("Bob's Instant Acct", true);
        this.offers = new ArrayList<>();
    }

    public void runTradeCycles() {
        try {
            verifyHavePaymentAccount();

            int tradeCycle = 0;
            while (tradeCycle <= MAX_TRADE_CYCLES) {
                offers.addAll(botClient.getOffersSortedByDate(BTC));
                verifyHaveOffers(tradeCycle);  // Should 1 BUY and 1 SELL for each trade cycle

                takeBuyBsqSellBtcOffer();
                rest(45_000); // Wallet may not have correct available balance, wait a bit.
                takeSellBsqBuyBtcOffer();

                verifyHaveTrades();
                rest(45_000); // Wallet may not have correct available balance, wait a bit.

                sendAliceBsqPaymentForHerBtc();
                rest(45_000);

                verifyBsqReceivedFromAlice();
                rest(45_000);

                rest(5_000);
                closeTrades();

                rest(5_000);
                printBalance();

                offers.clear();

                tradeCycle++;
                if (tradeCycle < MAX_TRADE_CYCLES) {
                    log.info("Completed {} trade cycle(s).  Starting the next in 1 minute.", tradeCycle);
                    rest(60_000);
                }
            }

            log.info("Shutting down taker bot.");

        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }

    protected void takeBuyBsqSellBtcOffer() {
        var offer = offers.stream().filter(o -> o.getDirection().equals(SELL.name())).findFirst().get();
        log.info("Bob is taking Buy BSQ (Sell BTC) offer {} using payment account {}.",
                offer.getId(),
                paymentAccount.getId());
        botClient.takeOffer(offer.getId(), paymentAccount, BSQ);
    }

    protected void takeSellBsqBuyBtcOffer() {
        var offer = offers.stream().filter(o -> o.getDirection().equals(BUY.name())).findFirst().get();
        log.info("Bob is taking Sell BSQ (Buy BTC) offer {} using payment account {}.",
                offer.getId(),
                paymentAccount.getId());
        botClient.takeOffer(offer.getId(), paymentAccount, BTC);
    }

    protected void verifyHavePaymentAccount() {
        if (paymentAccount == null)
            throw new IllegalStateException("No payment account for taking offers.");

        log.info("Bob is using '{}' with id = {}", paymentAccount.getAccountName(), paymentAccount.getId());
    }

    protected void verifyHaveOffers(int currentTradeCycle) {
        if (currentTradeCycle == MAX_TRADE_CYCLES) {
            log.warn("We're done, shutting down");
            System.exit(0);
        }

        if (offers.isEmpty()) {
            if (currentTradeCycle < MAX_TRADE_CYCLES) {
                throw new IllegalStateException(
                        format("No offers to take in trade cycle %d.", currentTradeCycle));
            } else {
                log.warn("No offers, might be finished after {} cycle(s).", currentTradeCycle);
                return;
            }
        } else if (offers.size() == 1) {
            // TODO Wait for the next offer to sync up again.
            for (int i = 0; i < 10; i++) {
                log.warn("There is only 1 available offer {} at start of cycle, will check again in 10 seconds.",
                        offers.get(0).getId());
                rest(10_000);
                offers.clear();
                offers.addAll(botClient.getOffersSortedByDate(BTC));
                if (offers.size() == 2) {
                    log.info("Now Bob can take offers:\n{}", formatOfferTable(offers, BSQ));
                    return;
                }
            }
            throw new IllegalStateException(
                    format("No offers to take in trade cycle %d.", currentTradeCycle));
        } else {
            log.info("Bob can take offers:\n{}", formatOfferTable(offers, BSQ));
        }
    }

    protected void verifyHaveTrades() {
        while (true) {
            var alicesBuyOfferId = getAlicesBuyBsqOfferId();
            try {
                var bobsBuyBsqTrade = botClient.getTrade(alicesBuyOfferId);
                if (bobsBuyBsqTrade != null) {
                    log.info("Bob's Buy BSQ (Sell BTC) Trade: \n{}", TradeFormat.format(bobsBuyBsqTrade));
                    break;
                } else {
                    throw new IllegalStateException(format("Take BUY offer %s failed.", alicesBuyOfferId));
                }
            } catch (StatusRuntimeException ex) {
                if (botClient.tradeContractIsNotReady.test(ex, alicesBuyOfferId)) {
                    log.warn("{} for trade {}, will try again", ex.getMessage(), alicesBuyOfferId);
                    rest(10_000);
                } else {
                    throw ex;
                }
            }
        }

        while (true) {
            var alicesSellOfferId = getAlicesSellBsqOfferId();
            try {
                var bobsSellBsqTrade = botClient.getTrade(alicesSellOfferId);
                if (bobsSellBsqTrade != null) {
                    log.info("Bob's Sell BSQ (Buy BTC) Trade: \n{}", TradeFormat.format(bobsSellBsqTrade));
                    break;
                } else {
                    throw new IllegalStateException(format("Take SELL offer %s failed.", alicesSellOfferId));
                }
            } catch (StatusRuntimeException ex) {
                if (botClient.tradeContractIsNotReady.test(ex, alicesSellOfferId)) {
                    log.warn("{} for trade {}, will try again", ex.getMessage(), alicesSellOfferId);
                    rest(10_000);
                } else {
                    throw ex;
                }
            }
        }
    }

    protected void sendAliceBsqPaymentForHerBtc() {
        try {
            var tradeId = getAlicesBuyBsqOfferId();
            var trade = botClient.getTrade(tradeId);
            botClient.makeBsqPayment(trade);
            log.info("Payment sent, generate a btc block now.");
            rest(15_000);
            botClient.sendConfirmPaymentStartedMessage(tradeId);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    protected void verifyBsqReceivedFromAlice() {
        try {
            var tradeId = getAlicesSellBsqOfferId();
            var trade = botClient.getTrade(tradeId);

            // TODO refactor into BotClient
            var contract = trade.getContract();
            var bsqSats = trade.getOffer().getVolume();
            var receiveAmountAsString = formatBsqAmount(bsqSats);
            var address = contract.getIsBuyerMakerAndSellerTaker()
                    ? contract.getTakerPaymentAccountPayload().getAddress()
                    : contract.getMakerPaymentAccountPayload().getAddress();
            log.info("Bob verifying payment of {} BSQ was received to address {} for trade with id {}.",
                    receiveAmountAsString,
                    address,
                    tradeId);
            log.info("Give bot time to send payment.");
            for (int i = 0; i < 5; i++) {
                rest(30_000);
                boolean receivedPayment = botClient.verifyBsqSentToAddress(address, receiveAmountAsString);
                if (receivedPayment) {
                    log.warn("Payment received, sending payment rcvd confirmation.");
                    botClient.sendConfirmPaymentReceivedMessage(tradeId);
                    break;
                } else {
                    log.warn("Payment NOT received.");
                }
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    protected void closeTrades() {
        for (OfferInfo offer : offers) {
            var tradeId = offer.getId();
            log.info("Sending keepfunds request for trade {}.", tradeId);
            botClient.sendKeepFundsMessage(tradeId);
        }
    }

    protected void printBalance() {
        log.info("Finished Trade Cycle\n{}", formatBalancesTbls(botClient.getBalance()));
    }

    protected String getAlicesBuyBsqOfferId() {
        // Buy BSQ = SELL BTC
        return offers.stream().filter(o -> o.getDirection().equals(SELL.name())).findFirst().get().getId();
    }

    protected String getAlicesSellBsqOfferId() {
        // Sell BSQ = BUY BTC
        return offers.stream().filter(o -> o.getDirection().equals(BUY.name())).findFirst().get().getId();
    }


    private void rest(long duration) {
        try {
            MILLISECONDS.sleep(duration);
        } catch (InterruptedException interruptedException) {
            // empty
        }
    }

    public static void main(String[] args) {
        BsqMarketMakerBotTest bob = new BsqMarketMakerBotTest();
        bob.runTradeCycles();
    }
}
