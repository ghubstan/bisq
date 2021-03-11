package bisq.apitest.scenario.bot.protocol;

import bisq.proto.grpc.OfferInfo;
import bisq.proto.grpc.TradeInfo;

import protobuf.PaymentAccount;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import static bisq.apitest.scenario.bot.shutdown.ManualShutdown.checkIfShutdownCalled;
import static bisq.cli.GrpcClient.PRICE_OUT_OF_TOLERANCE_ERROR;
import static bisq.cli.GrpcClient.UNCONF_TX_LIMIT_HIT_ERROR;
import static java.lang.System.currentTimeMillis;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;



import bisq.apitest.scenario.bot.BotClient;

@SuppressWarnings({"unchecked", "rawtypes"})
@Slf4j
@Getter
public class TakeOfferHelper {

    static final int MAX_TAKE_OFFER_ATTEMPTS = 50;

    final AtomicReference<Optional<TradeInfo>> tradeReference = new AtomicReference(Optional.empty());
    final AtomicReference<Optional<Throwable>> errorReference = new AtomicReference(Optional.empty());

    final BiPredicate<AtomicReference<Optional<TradeInfo>>, AtomicReference<Optional<Throwable>>> responseReceived =
            (trade, error) -> trade.get().isPresent() || error.get().isPresent();

    private final BotClient botClient;
    private final String botDescription;
    private final OfferInfo offer;
    private final PaymentAccount paymentAccount;
    private final String feeCurrency;
    @Getter
    private final long takeOfferRequestDeadlineInSec;
    private final int maxAttemptsBeforeFail;

    public TakeOfferHelper(BotClient botClient,
                           String botDescription,
                           OfferInfo offer,
                           PaymentAccount paymentAccount,
                           String feeCurrency,
                           long takeOfferRequestDeadlineInSec,
                           int maxAttemptsBeforeFail) {
        this.botClient = botClient;
        this.botDescription = botDescription;
        this.offer = offer;
        this.paymentAccount = paymentAccount;
        this.feeCurrency = feeCurrency;
        this.takeOfferRequestDeadlineInSec = takeOfferRequestDeadlineInSec;
        this.maxAttemptsBeforeFail = maxAttemptsBeforeFail;
    }

    private final AtomicLong startTime = new AtomicLong();
    private final AtomicLong stopTime = new AtomicLong();
    private final Consumer<Long> setSingleAttemptDeadline = (now) -> {
        startTime.set(now);
        stopTime.set(now + SECONDS.toMillis(this.getTakeOfferRequestDeadlineInSec()));
    };
    private final Predicate<Long> deadlineReached = (t) -> t > stopTime.get();

    @Getter
    private TradeInfo newTrade;
    @Getter
    private Throwable takeOfferError;

    public final Supplier<Boolean> hasNewTrade = () -> newTrade != null;
    public final Supplier<Boolean> hasTakeOfferError = () -> takeOfferError != null;
    private final CountDownLatch attemptDeadlineLatch = new CountDownLatch(1);

    public synchronized void run() {
        checkIfShutdownCalled("Interrupted before attempting to take offer " + offer.getId());
        int attemptCount = 0;
        while (++attemptCount < maxAttemptsBeforeFail) {
            AtomicReference<TradeInfo> newTradeReference = new AtomicReference(null);
            AtomicReference<Throwable> takeOfferErrorReference = new AtomicReference(null);

            logCurrentTakeOfferAttempt(attemptCount);
            sendTakeOfferRequest(newTradeReference, takeOfferErrorReference);

            // If we have a trade, exit now.  If we have a throwable, we might make
            // another attempt depending on the error. If we don't have any type of reply
            // (a trade or throwable), takeoffer has failed.
            if (isSuccessfulTakeOfferRequest.test(newTradeReference)) {
                newTrade = newTradeReference.get();
                break;
            } else if (takeOfferErrorIsNotFatal.test(takeOfferErrorReference)) {
                takeOfferError = takeOfferErrorReference.get();
                logNextTakeOfferAttemptAndWait(attemptCount, 10);
                // Reset the single attempt deadline and try again.
                setSingleAttemptDeadline.accept(currentTimeMillis());
            } else if (takeOfferErrorReference.get() != null) {
                takeOfferError = takeOfferErrorReference.get();
                log.error("Fatal error attempting to take offer {}. Reason: {}", offer.getId(), takeOfferError.getMessage());
                break;
            } else {
                log.error("Fatal error attempting to take offer {} with no reason from server", offer.getId());
                break;
            }
        }
    }

    private void logNextTakeOfferAttemptAndWait(int attemptCount, long waitInSec) {
        // Take care to not let bots exceed call rate limit on mainnet.
        log.info("The takeoffer {} request attempt #{} will be made in {} seconds.",
                offer.getId(),
                attemptCount+1,
                waitInSec);
        try {
            SECONDS.sleep(waitInSec);
        } catch (InterruptedException ignored) {
            // empty
        }
    }

    private void logCurrentTakeOfferAttempt(int attemptCount) {
        log.info("{} taking {} / {} offer {}.  Attempt # {}.",
                botDescription,
                offer.getDirection(),
                offer.getCounterCurrencyCode(),
                offer.getId(),
                attemptCount);
    }

    private final Predicate<AtomicReference<TradeInfo>> isSuccessfulTakeOfferRequest = (t) -> {
        if (t.get() != null) {
            try {
                log.info("Created trade {}  Allowing 5s for trade prep before continuing.", newTrade.getTradeId());
                SECONDS.sleep(5);
            } catch (InterruptedException ignored) {
                // empty
            }
            return true;
        } else {
            return false;
        }
    };

    private final Predicate<AtomicReference<Throwable>> takeOfferErrorIsNotFatal = (e) -> {
        Throwable t = e.get();
        if (t != null) {
            return this.getBotClient().takeOfferFailedForOneOfTheseReasons(t,
                    PRICE_OUT_OF_TOLERANCE_ERROR,
                    UNCONF_TX_LIMIT_HIT_ERROR);
        } else {
            return false;
        }
    };

    private void sendTakeOfferRequest(AtomicReference<TradeInfo> newTradeReference,
                                      AtomicReference<Throwable> takeOfferErrorReference) {
        checkIfShutdownCalled("Interrupted while attempting to take offer " + offer.getId());

        botClient.tryToTakeOffer(offer.getId(),
                paymentAccount,
                feeCurrency,
                newTradeReference::set,
                takeOfferErrorReference::set);

        Supplier<Boolean> isReplyReceived = () ->
                newTradeReference.get() != null || takeOfferErrorReference.get() != null;

        setSingleAttemptDeadline.accept(currentTimeMillis());
        while (!deadlineReached.test(currentTimeMillis()) && !isReplyReceived.get()) {
            try {
                //noinspection ResultOfMethodCallIgnored
                attemptDeadlineLatch.await(10, MILLISECONDS);
            } catch (InterruptedException ignored) {
                // empty
            }
        }
        logRequestResult(newTradeReference, takeOfferErrorReference, isReplyReceived);
    }

    private void logRequestResult(AtomicReference<TradeInfo> newTradeReference,
                                  AtomicReference<Throwable> takeOfferErrorReference,
                                  Supplier<Boolean> isReplyReceived) {
        if (isReplyReceived.get()) {
            if (newTradeReference.get() != null)
                log.info("The takeoffer request returned new trade: {}.",
                        newTradeReference.get().getTradeId());
            else
                log.warn("The takeoffer request returned error: {}.",
                        takeOfferErrorReference.get().getMessage());
        } else {
            log.error("The takeoffer request failed: no reply received within the {} second deadline.",
                    takeOfferRequestDeadlineInSec);
        }
    }
}
