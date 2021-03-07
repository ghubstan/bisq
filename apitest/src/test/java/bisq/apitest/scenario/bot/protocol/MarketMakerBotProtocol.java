package bisq.apitest.scenario.bot.protocol;

import bisq.proto.grpc.OfferInfo;
import bisq.proto.grpc.TradeInfo;

import protobuf.PaymentAccount;

import java.io.File;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

import lombok.extern.slf4j.Slf4j;

import static bisq.apitest.scenario.bot.protocol.ProtocolStep.DONE;
import static bisq.apitest.scenario.bot.protocol.ProtocolStep.WAIT_FOR_OFFER_TAKER;
import static bisq.apitest.scenario.bot.shutdown.ManualShutdown.checkIfShutdownCalled;
import static bisq.cli.TableFormat.formatOfferTable;
import static java.lang.String.format;
import static java.util.Collections.singletonList;



import bisq.apitest.method.BitcoinCliHelper;
import bisq.apitest.scenario.bot.BotClient;
import bisq.apitest.scenario.bot.script.BashScriptGenerator;
import bisq.cli.TradeFormat;

@Slf4j
public class MarketMakerBotProtocol extends BotProtocol {

    // If target spread is 4%.  Bob will create
    //      BUY  BTC offers at -2% (below mkt price)
    //      SELL BTC offers at +2% (above mkt price)
    // static final double PRICE_MARGIN = 2.00;

    static final double PRICE_MARGIN = 6.50; // Target spread is 13%.

    private final String direction;
    private final AtomicLong bobsBankBalance;

    public MarketMakerBotProtocol(BotClient botClient,
                                  PaymentAccount paymentAccount,
                                  long protocolStepTimeLimitInMs,
                                  BitcoinCliHelper bitcoinCli,
                                  BashScriptGenerator bashScriptGenerator,
                                  String direction,
                                  AtomicLong bobsBankBalance) {
        super("Maker",
                botClient,
                paymentAccount,
                protocolStepTimeLimitInMs,
                bitcoinCli,
                bashScriptGenerator);
        this.direction = direction;
        this.bobsBankBalance = bobsBankBalance;
    }

    @Override
    public void run() {
        checkIsStartStep();

        var isBuy = direction.equalsIgnoreCase("BUY");

        Function<Supplier<OfferInfo>, TradeInfo> makeTrade = waitForNewTrade.andThen(waitForTakerFeeTxConfirm);
        var trade = isBuy
                ? makeTrade.apply(createBuyOffer)
                : makeTrade.apply(createSellOffer);

        var makerIsBuyer = trade.getOffer().getDirection().equalsIgnoreCase(BUY);
        Function<TradeInfo, TradeInfo> completeFiatTransaction = makerIsBuyer
                ? sendPaymentStartedMessage.andThen(waitForPaymentReceivedConfirmation)
                : waitForPaymentStartedMessage.andThen(sendPaymentReceivedMessage);
        completeFiatTransaction.apply(trade);

        Function<TradeInfo, TradeInfo> closeTrade = waitForPayoutTx.andThen(keepFundsFromTrade);
        closeTrade.apply(trade);

        long bankBalanceDelta = isBuy
                ? -1 * toDollars(trade.getOffer().getVolume())
                : toDollars(trade.getOffer().getVolume());
        bobsBankBalance.addAndGet(bankBalanceDelta);

        currentProtocolStep = DONE;
    }

    private final Supplier<OfferInfo> createBuyOffer = () -> {
        checkIfShutdownCalled("Interrupted before creating random BUY offer.");
        // TODO LOOP 3 times until we create a valid offer.
        for (int i = 0; i < 3; i++) {
            try {
                var offer = botClient.createOfferAtMarketBasedPrice(paymentAccount,
                        "BUY",
                        currencyCode,
                        2500000,
                        2500000,
                        PRICE_MARGIN,
                        0.15,
                        "BSQ");
                log.info("Created BUY / {} offer at {}% below current market price of {}:\n{}",
                        currencyCode,
                        PRICE_MARGIN,
                        botClient.getCurrentBTCMarketPriceAsString(currencyCode),
                        formatOfferTable(singletonList(offer), currencyCode));
                return offer;
            } catch (Exception ex) {
                log.error("Failed to create offer at attempt #{}.", i, ex);
                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException interruptedException) {
                }
            }
        }
        throw new IllegalStateException(format("%s could not create offer after 3 attempts.",
                this.getBotDescription()));
    };

    private final Supplier<OfferInfo> createSellOffer = () -> {
        checkIfShutdownCalled("Interrupted before creating random SELL offer.");
        // TODO LOOP 3 times until we create a valid offer.
        for (int i = 0; i < 3; i++) {
            try {
                var offer = botClient.createOfferAtMarketBasedPrice(paymentAccount,
                        "SELL",
                        currencyCode,
                        2500000,
                        2500000,
                        PRICE_MARGIN,
                        0.15,
                        "BSQ");
                log.info("Created SELL / {} offer at {}% above current market price of {}:\n{}",
                        currencyCode,
                        PRICE_MARGIN,
                        botClient.getCurrentBTCMarketPriceAsString(currencyCode),
                        formatOfferTable(singletonList(offer), currencyCode));
                return offer;
            } catch (Exception ex) {
                log.error("Failed to create offer at attempt #{}.", i, ex);
                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException interruptedException) {
                }
            }
        }
        throw new IllegalStateException(format("%s could not create offer after 3 attempts.",
                this.getBotDescription()));
    };

    private final Function<Supplier<OfferInfo>, TradeInfo> waitForNewTrade = (latestOffer) -> {
        initProtocolStep.accept(WAIT_FOR_OFFER_TAKER);
        OfferInfo offer = latestOffer.get();
        createTakeOfferCliScript(offer);
        log.info("Waiting for offer {} to be taken.", offer.getId());
        int numDelays = 0;
        while (isWithinProtocolStepTimeLimit()) {
            checkIfShutdownCalled("Interrupted while waiting for offer to be taken.");
            try {
                var trade = getNewTrade(offer.getId());
                if (trade.isPresent()) {
                    return trade.get();
                } else {
                    if (++numDelays % 5 == 0) {
                        log.warn("Offer {} still waiting to be taken, current state = {}",
                                offer.getId(), offer.getState());
                        String offerCounterCurrencyCode = offer.getCounterCurrencyCode();
                        List<OfferInfo> myCurrentOffers = botClient.getMyOffersSortedByDate(offerCounterCurrencyCode);
                        log.info("RobotBob's current offers ({} is in the list, or fail):\n{}",
                                offer.getId(),
                                formatOfferTable(myCurrentOffers, offerCounterCurrencyCode));
                    }
                    sleep(randomDelay.get());
                }
            } catch (Exception ex) {
                throw new IllegalStateException(this.getBotClient().toCleanGrpcExceptionMessage(ex), ex);
            }
        } // end while

        // If the while loop is exhausted, the offer was not taken within the protocol step time limit.
        throw new IllegalStateException("Offer was never taken; we won't wait any longer.");
    };

    private Optional<TradeInfo> getNewTrade(String offerId) {
        try {
            var trade = botClient.getTrade(offerId);
            log.info("Offer {} was taken, new trade:\n{}", offerId, TradeFormat.format(trade));
            return Optional.of(trade);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private void createTakeOfferCliScript(OfferInfo offer) {
        String scriptFilename = "takeoffer-" + offer.getId() + ".sh";
        File script = bashScriptGenerator.createTakeOfferScript(offer, scriptFilename);
        printCliHintAndOrScript(script, "The manual CLI side can take the offer");
    }
}
