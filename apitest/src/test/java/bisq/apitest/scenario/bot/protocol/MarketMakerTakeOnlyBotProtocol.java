package bisq.apitest.scenario.bot.protocol;

import bisq.proto.grpc.OfferInfo;
import bisq.proto.grpc.TradeInfo;

import protobuf.PaymentAccount;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

import lombok.extern.slf4j.Slf4j;

import static bisq.apitest.scenario.bot.protocol.ProtocolStep.DONE;
import static bisq.apitest.scenario.bot.protocol.ProtocolStep.FIND_OFFER;
import static bisq.apitest.scenario.bot.protocol.ProtocolStep.TAKE_OFFER;
import static bisq.apitest.scenario.bot.shutdown.ManualShutdown.checkIfShutdownCalled;
import static bisq.cli.TableFormat.formatOfferTable;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;



import bisq.apitest.method.BitcoinCliHelper;
import bisq.apitest.scenario.bot.BotClient;
import bisq.apitest.scenario.bot.script.BashScriptGenerator;

@Slf4j
public class MarketMakerTakeOnlyBotProtocol extends BotProtocol {

    private final AtomicLong takersBankBalance;

    public MarketMakerTakeOnlyBotProtocol(BotClient botClient,
                                          PaymentAccount paymentAccount,
                                          long protocolStepTimeLimitInMs,
                                          BitcoinCliHelper bitcoinCli,
                                          BashScriptGenerator bashScriptGenerator,
                                          AtomicLong takersBankBalance) {
        super("Taker",
                botClient,
                paymentAccount,
                protocolStepTimeLimitInMs,
                bitcoinCli,
                bashScriptGenerator);
        this.takersBankBalance = takersBankBalance;
    }

    @Override
    public void run() {
        checkIsStartStep();

        Function<OfferInfo, TradeInfo> takeTrade = takeOffer.andThen(waitForTakerFeeTxConfirm);
        var trade = takeTrade.apply(findOffer.get());

        var takerIsSeller = trade.getOffer().getDirection().equalsIgnoreCase(BUY);
        Function<TradeInfo, TradeInfo> completeFiatTransaction = takerIsSeller
                ? waitForPaymentStartedMessage.andThen(sendPaymentReceivedMessage)
                : sendPaymentStartedMessage.andThen(waitForPaymentReceivedConfirmation);
        completeFiatTransaction.apply(trade);

        Function<TradeInfo, TradeInfo> closeTrade = waitForPayoutTx.andThen(keepFundsFromTrade);
        closeTrade.apply(trade);

        var iAmSeller = trade.getOffer().getDirection().equalsIgnoreCase("BUY");
        long bankBalanceDelta = iAmSeller
                ? toDollars(trade.getOffer().getVolume())
                : -1 * toDollars(trade.getOffer().getVolume());
        takersBankBalance.addAndGet(bankBalanceDelta);

        currentProtocolStep = DONE;
    }

    private final Supplier<Optional<OfferInfo>> firstOffer = () -> {
        var offers = botClient.getOffersSortedByDate(currencyCode);
        if (offers.size() > 0) {
            log.info("Offers found:\n{}", formatOfferTable(offers, currencyCode));
            OfferInfo offer = offers.get(0);
            log.info("Will take first offer {}", offer.getId());
            return Optional.of(offer);
        } else {
            log.info("No buy or sell {} offers found.", currencyCode);
            return Optional.empty();
        }
    };

    private final Supplier<OfferInfo> findOffer = () -> {
        initProtocolStep.accept(FIND_OFFER);
        log.info("Looking for a {} offer.", currencyCode);
        int numDelays = 0;
        while (isWithinProtocolStepTimeLimit()) {
            checkIfShutdownCalled("Interrupted while checking offers.");
            try {
                Optional<OfferInfo> offer = firstOffer.get();
                if (offer.isPresent()) {
                    return offer.get();
                } else {
                    if (++numDelays % 5 == 0) {
                        List<OfferInfo> currentOffers = botClient.getOffersSortedByDate(currencyCode);
                        log.info("Still no available {} offers for taker bot, current offers:\n{}",
                                currencyCode,
                                formatOfferTable(currentOffers, currencyCode));
                    }
                    sleep(randomDelay.get());
                }
            } catch (Exception ex) {
                throw new IllegalStateException(this.getBotClient().toCleanGrpcExceptionMessage(ex), ex);
            }
        } // end while

        // If the while loop is exhausted, the offer was not created within the protocol step time limit.
        throw new IllegalStateException("Offer was never created; we won't wait any longer.");
    };


    private final Function<OfferInfo, TradeInfo> takeOffer = (offer) -> {
        initProtocolStep.accept(TAKE_OFFER);
        checkIfShutdownCalled("Interrupted before taking offer.");
        String feeCurrency = RANDOM.nextBoolean() ? "BSQ" : "BTC";
        TakeOfferHelper takeOfferHelper = new TakeOfferHelper(botClient,
                botDescription,
                offer,
                paymentAccount,
                feeCurrency,
                60,
                15);

        takeOfferHelper.run();

        if (takeOfferHelper.hasNewTrade.get()) {

            try {
                log.info("{} waiting 5s for trade prep before allowing any gettrade calls.", botDescription);
                SECONDS.sleep(5);
            } catch (InterruptedException ignored) {
                // empty
            }
            return takeOfferHelper.getNewTrade();

        } else if (takeOfferHelper.hasTakeOfferError.get()) {

            throw new IllegalStateException(format("%s's takeoffer %s attempt failed.",
                    botDescription,
                    offer.getId()),
                    takeOfferHelper.getTakeOfferError());
        } else {
            throw new IllegalStateException(format("%s's takeoffer %s attempt failed for unknown reason.",
                    botDescription,
                    offer.getId()));
        }

    };
}


