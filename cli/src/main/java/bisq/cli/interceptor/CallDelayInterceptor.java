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

import lombok.extern.slf4j.Slf4j;

import static java.lang.System.currentTimeMillis;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

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

    private final boolean logDelays;

    public CallDelayInterceptor() {
        this(false);
    }

    public CallDelayInterceptor(boolean logDelays) {
        this.logDelays = logDelays;
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
                    if(logDelays)
                        log.warn("Delaying {} call for {} ms.", method.getFullMethodName(), minDelay);
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
}
