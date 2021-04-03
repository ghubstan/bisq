package bisq.cli.interceptor;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.MethodDescriptor;

import com.google.common.collect.ImmutableMap;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import org.slf4j.event.Level;

import lombok.extern.slf4j.Slf4j;

import static java.lang.String.format;
import static java.lang.System.currentTimeMillis;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * gRPC ClientInterceptor to delay rate metered calls in a non-intrusive way.
 */
@SuppressWarnings("rawtypes")
@Slf4j
public class CallDelayInterceptor implements ClientInterceptor {

    private final long oneSecond = SECONDS.toMillis(1);
    private final long fiveSeconds = SECONDS.toMillis(5);
    private final long oneMinute = MINUTES.toMillis(1);

    private final ImmutableMap<MethodDescriptor, Long> methodDelayMap = ImmutableMap.<MethodDescriptor, Long>builder()
            // Delay these calls by up to 1 second maximum.
            .put(bisq.proto.grpc.OffersGrpc.getGetOffersMethod(), oneSecond)
            .put(bisq.proto.grpc.OffersGrpc.getGetMyOffersMethod(), oneSecond)
            .put(bisq.proto.grpc.PaymentAccountsGrpc.getGetPaymentAccountsMethod(), oneSecond)
            .put(bisq.proto.grpc.PaymentAccountsGrpc.getCreatePaymentAccountMethod(), oneSecond)
            .put(bisq.proto.grpc.PaymentAccountsGrpc.getCreateCryptoCurrencyPaymentAccountMethod(), oneSecond)
            .put(bisq.proto.grpc.TradesGrpc.getGetTradeMethod(), oneSecond)
            // Delay these calls by up to 5 second maximum.
            .put(bisq.proto.grpc.WalletsGrpc.getSetWalletPasswordMethod(), fiveSeconds)
            .put(bisq.proto.grpc.WalletsGrpc.getRemoveWalletPasswordMethod(), fiveSeconds)
            .put(bisq.proto.grpc.WalletsGrpc.getSendBtcMethod(), fiveSeconds)
            .put(bisq.proto.grpc.WalletsGrpc.getSendBsqMethod(), fiveSeconds)
            // Delay these calls by up to 1 minute maximum.
            .put(bisq.proto.grpc.OffersGrpc.getCreateOfferMethod(), oneMinute)
            .put(bisq.proto.grpc.TradesGrpc.getTakeOfferMethod(), oneMinute)
            .build();

    private final Map<MethodDescriptor, Long> methodCallTimestampMap = new HashMap<>();

    private final Predicate<MethodDescriptor> hasConfiguredDelay = (m) -> methodDelayMap.get(m) != null;

    // This may seem overkill but we should never log delays in the CLI console, and
    // should be able to log delays with a configurable log level in bots and tests.
    private final Level logLevel;

    public CallDelayInterceptor() {
        this(null);
    }

    public CallDelayInterceptor(Level logLevel) {
        this.logLevel = logLevel;
        methodDelayMap.keySet().stream().iterator()
                .forEachRemaining(methodDescriptor ->
                        methodCallTimestampMap.put(methodDescriptor, 0L));
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
                                                               CallOptions callOptions,
                                                               Channel next) {
        maybeDelayCall(method);
        return next.newCall(method, callOptions);
    }

    private void maybeDelayCall(MethodDescriptor method) {
        try {
            if (!hasConfiguredDelay.test(method))
                return;

            var lastCallTimestamp = methodCallTimestampMap.get(method);
            if (lastCallTimestamp != 0) {
                var maxDelay = methodDelayMap.get(method);
                var now = currentTimeMillis();
                if (now - lastCallTimestamp < maxDelay) {
                    var minDelay = maxDelay - (now - lastCallTimestamp);
                    maybeLogDelay(method, minDelay);
                    delayCall(minDelay);
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("interceptor call delays not correctly configured");
        } finally {
            methodCallTimestampMap.put(method, currentTimeMillis());
        }
    }

    private void delayCall(long milliseconds) {
        try {
            MILLISECONDS.sleep(milliseconds);
        } catch (InterruptedException ignored) {
            // empty
        }
    }

    private void maybeLogDelay(MethodDescriptor method, long delay) {
        if (logLevel == null)
            return;

        String msg = format("Delaying %s call for %d ms.", method.getFullMethodName(), delay);
        switch (logLevel) {
            case ERROR:
                log.error(msg);
                break;
            case WARN:
                log.warn(msg);
                break;
            case INFO:
                log.info(msg);
                break;
            case DEBUG:
                log.debug(msg);
                break;
            case TRACE:
                log.trace(msg);
                break;
            default:
                return;
        }
    }
}
