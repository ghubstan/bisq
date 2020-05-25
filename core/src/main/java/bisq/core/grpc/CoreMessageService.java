package bisq.core.grpc;

import io.grpc.StatusRuntimeException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.inject.Inject;

import java.text.DecimalFormat;

import java.math.BigDecimal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

import static java.lang.String.format;

@Slf4j
class CoreMessageService {

    private final CoreApi coreApi;

    private final Gson gson = new GsonBuilder().create();

    // Used by a regex matcher to split command tokens by space, excepting those
    // enclosed in dbl quotes.
    private final Pattern paramsPattern = Pattern.compile("([^\"]\\S*|\".+?\")\\s*");

    @Inject
    public CoreMessageService(CoreApi coreApi) {
        this.coreApi = coreApi;
    }

    public String call(String params, boolean isGatewayRequest) {
        log.info("RPC request: '{}'", params);

        if (params.isEmpty()) {
            return handleException(
                    new IllegalArgumentException("no method specified"),
                    isGatewayRequest);
        }

        var paramTokens = getParamTokens(params);
        var methodName = paramTokens.get(0);
        final Method method;
        try {
            method = Method.valueOf(methodName);
        } catch (IllegalArgumentException ex) {
            return handleException(
                    new IllegalArgumentException(format("'%s' is not a supported method", methodName), ex),
                    isGatewayRequest);
        }

        try {
            coreApi.getBalance();  // If the wallet or balance is unavailable, you can't do anything.
        } catch (IllegalStateException ex) {
            String reason = ex.getMessage();
            if (reason.equals("wallet is not yet available") || reason.equals("balance is not yet available")) {
                return handleException(new IllegalStateException("server not ready for requests", ex), isGatewayRequest);
            }
        }

        try {
            switch (method) {
                case help: {
                    String cmd = (paramTokens.size() > 1) ? paramTokens.get(1) : null;
                    if (cmd != null) {
                        try {
                            return formatResponse(coreApi.getHelp(Method.valueOf(cmd)), isGatewayRequest);
                        } catch (IllegalArgumentException ex) {
                            return handleException(
                                    new IllegalArgumentException(
                                            format("'%s\n\n%s' is not a supported method", cmd, coreApi.getHelp(null)),
                                            ex),
                                    isGatewayRequest);
                        }
                    } else {
                        return formatResponse(coreApi.getHelp(null), isGatewayRequest);
                    }
                }
                case getversion: {
                    return formatResponse(coreApi.getVersion(), isGatewayRequest);
                }
                case getbalance: {
                    try {
                        var satoshiBalance = coreApi.getBalance();
                        var satoshiDivisor = new BigDecimal(100000000);
                        var btcFormat = new DecimalFormat("###,##0.00000000");
                        @SuppressWarnings("BigDecimalMethodWithoutRoundingCalled")
                        var btcBalance = btcFormat.format(BigDecimal.valueOf(satoshiBalance).divide(satoshiDivisor));
                        return formatResponse(btcBalance, isGatewayRequest);
                    } catch (IllegalStateException ex) {
                        return handleException(ex, isGatewayRequest);
                    }
                }
                case lockwallet: {
                    try {
                        coreApi.lockWallet();
                        return formatResponse("wallet locked", isGatewayRequest);
                    } catch (IllegalStateException ex) {
                        return handleException(ex, isGatewayRequest);
                    }
                }
                case unlockwallet: {
                    if (paramTokens.size() < 2)
                        return handleException(
                                new IllegalArgumentException("no password specified"),
                                isGatewayRequest);

                    var password = paramTokens.get(1);

                    if (paramTokens.size() < 3)
                        return handleException(
                                new IllegalArgumentException("no unlock timeout specified"),
                                isGatewayRequest);

                    long timeout;
                    try {
                        timeout = Long.parseLong(paramTokens.get(2));
                    } catch (NumberFormatException ex) {
                        return handleException(
                                new IllegalArgumentException(format("'%s' is not a number", paramTokens.get(2)), ex),
                                isGatewayRequest);
                    }

                    try {
                        coreApi.unlockWallet(password, timeout);
                        return formatResponse("wallet unlocked", isGatewayRequest);
                    } catch (IllegalStateException ex) {
                        return handleException(ex, isGatewayRequest);
                    }
                }
                case setwalletpassword: {
                    if (paramTokens.size() < 2)
                        return handleException(
                                new IllegalArgumentException("no password specified"),
                                isGatewayRequest);

                    var password = paramTokens.get(1);
                    var newPassword = paramTokens.size() == 3 ? paramTokens.get(2).trim() : "";
                    try {
                        coreApi.setWalletPassword(password, newPassword);
                        return formatResponse("wallet encrypted"
                                        + (!newPassword.isEmpty() ? " with new password" : ""),
                                isGatewayRequest);
                    } catch (IllegalStateException ex) {
                        return handleException(ex, isGatewayRequest);
                    }
                }
                case removewalletpassword: {
                    if (paramTokens.size() < 2)
                        return handleException(
                                new IllegalArgumentException("no password specified"),
                                isGatewayRequest);

                    var password = paramTokens.get(1);
                    try {
                        coreApi.removeWalletPassword(password);
                        return formatResponse("wallet decrypted", isGatewayRequest);
                    } catch (IllegalStateException ex) {
                        return handleException(ex, isGatewayRequest);
                    }
                }
                default: {
                    return handleException(
                            new RuntimeException(format("unhandled method '%s'", method)),
                            isGatewayRequest);
                }
            }
        } catch (StatusRuntimeException ex) {
            // Remove the leading gRPC status code (e.g. "UNKNOWN: ") from the message
            String message = ex.getMessage().replaceFirst("^[A-Z_]+: ", "");
            throw new RuntimeException(message, ex);
        }
    }

    private List<String> getParamTokens(String params) {
        List<String> paramTokens = new ArrayList<>();
        Matcher m = paramsPattern.matcher(params);
        while (m.find()) {
            String rawToken = m.group(1);
            // We only want to strip leading and trailing dbl quotes from the token,
            // and allow passwords to contain quotes.
            if (rawToken.length() >= 2 && rawToken.charAt(0) == '"' && rawToken.charAt(rawToken.length() - 1) == '"')
                rawToken = rawToken.substring(1, rawToken.length() - 1);
            paramTokens.add(rawToken);
        }
        return paramTokens;
    }

    private String formatResponse(String data, boolean isGatewayRequest) {
        return isGatewayRequest ? toJson(data) : data;
    }

    private String handleException(RuntimeException ex, boolean isGatewayRequest) {
        if (isGatewayRequest)
            return toJson(ex.getMessage());
        else
            throw ex;
    }

    private String toJson(String data) {
        Map<String, String> map = new HashMap<>() {{
            put("data", data);
        }};
        return gson.toJson(map, Map.class);
    }
}
