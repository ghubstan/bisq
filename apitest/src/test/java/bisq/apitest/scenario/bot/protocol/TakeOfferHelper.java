package bisq.apitest.scenario.bot.protocol;

import bisq.proto.grpc.OfferInfo;
import bisq.proto.grpc.TradeInfo;

import protobuf.PaymentAccount;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import static bisq.apitest.scenario.bot.shutdown.ManualShutdown.checkIfShutdownCalled;
import static java.lang.String.format;
import static java.lang.System.currentTimeMillis;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;



import bisq.apitest.scenario.bot.BotClient;

@Slf4j
@Getter
public class TakeOfferHelper {
    final AtomicReference<Optional<TradeInfo>> tradeReference = new AtomicReference(Optional.empty());
    final AtomicReference<Optional<Throwable>> errorReference = new AtomicReference(Optional.empty());
    final BiPredicate<AtomicReference<Optional<TradeInfo>>, AtomicReference<Optional<Throwable>>> responseReceived =
            (trade, error) -> trade.get().isPresent() || error.get().isPresent();
    final Consumer<TradeInfo> resultHandler = (result) -> tradeReference.set(Optional.of(result));
    final Consumer<Throwable> errorHandler = (error) -> errorReference.set(Optional.of(error));

    private final BotClient botClient;
    private final String botDescription;
    private final OfferInfo offer;
    private final PaymentAccount paymentAccount;
    private final String feeCurrency;
    private final long deadlineInSec;

    public TakeOfferHelper(BotClient botClient,
                           String botDescription,
                           OfferInfo offer,
                           PaymentAccount paymentAccount,
                           String feeCurrency,
                           long deadlineInSec) {
        this.botClient = botClient;
        this.botDescription = botDescription;
        this.offer = offer;
        this.paymentAccount = paymentAccount;
        this.feeCurrency = feeCurrency;
        this.deadlineInSec = deadlineInSec;
    }

    public void run() {
        log.info("{} taking {} / {} offer {}.",
                botDescription,
                offer.getDirection(),
                offer.getCounterCurrencyCode(),
                offer.getId());
        long startTime = currentTimeMillis();
        long stopTime = startTime + SECONDS.toMillis(deadlineInSec);
        Predicate<Long> deadlineReached = (t) -> t > stopTime;
        CountDownLatch latch = new CountDownLatch(1);
        botClient.tryToTakeOffer(offer.getId(), paymentAccount, feeCurrency, resultHandler, errorHandler);
        while (!deadlineReached.test(currentTimeMillis()) && !responseReceived.test(tradeReference, errorReference)) {
            checkIfShutdownCalled("Interrupted while taking offer.");
            try {
                latch.await(10, MILLISECONDS);
            } catch (InterruptedException ignored) {
                // empty
            }
        }
    }

    public boolean hasError() {
        return errorReference.get().isPresent();
    }

    public Throwable getError() {
        return errorReference.get().get();  // get(reference.value).get(optional.value)
    }

    public boolean hasTrade() {
        return tradeReference.get().isPresent();
    }

    public TradeInfo getTrade() {
        return tradeReference.get().orElseThrow(() ->
                new IllegalStateException(format("%s's take offer %s attempt did not throw an exception, but failed.",
                        botDescription,
                        offer.getId())));
    }
}
