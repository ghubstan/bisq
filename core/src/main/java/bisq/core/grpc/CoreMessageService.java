package bisq.core.grpc;

import io.grpc.StatusRuntimeException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.inject.Inject;

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

    private enum Method {
        help,
        getversion,
        getbalance,
        lockwallet,
        unlockwallet,
        removewalletpassword,
        setwalletpassword
    }

    private final CoreApi coreApi;
    private final CoreWalletService walletService;

    private final Gson gson = new GsonBuilder().create();

    // Used by a regex matcher to split command tokens by space, excepting those
    // enclosed in dbl quotes.
    private final Pattern paramsPattern = Pattern.compile("([^\"]\\S*|\".+?\")\\s*");

    @Inject
    public CoreMessageService(CoreApi coreApi, CoreWalletService walletService) {
        this.coreApi = coreApi;
        this.walletService = walletService; // TODO inject into coreApi instead of this
    }

    public String call(String params, boolean isGatewayRequest) {
        log.info("RPC request: '{}'", params);

        if (params.isEmpty()) {
            if (isGatewayRequest)
                return toJson("no method specified");
            else
                throw new IllegalArgumentException("no method specified");
        }

        var paramTokens = getParamTokens(params);
        var methodName = paramTokens.get(0);
        final Method method;
        try {
            method = Method.valueOf(methodName);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(format("'%s' is not a supported method", methodName));
        }

        try {
            switch (method) {
                case help: {
                    if (isGatewayRequest)
                        return toJson(coreApi.getHelp());
                    else
                        return coreApi.getHelp();
                }
                case getversion: {
                    if (isGatewayRequest)
                        return toJson(coreApi.getVersion());
                    else
                        return coreApi.getVersion();
                }
                case getbalance: {
                    if (isGatewayRequest)
                        try {
                            return toJson(String.valueOf(walletService.getAvailableBalance()));
                        } catch (IllegalStateException e) {
                            return toJson(e.getMessage());
                        }
                    else
                        return String.valueOf(walletService.getAvailableBalance());
                }
                case setwalletpassword: {
                    if (paramTokens.size() < 2) {
                        if (isGatewayRequest)
                            return toJson("no password specified");
                        else
                            throw new IllegalArgumentException("no password specified");
                    }
                    var hasNewPassword = paramTokens.size() == 3;
                    var newPassword = "";
                    if (hasNewPassword)
                        newPassword = paramTokens.get(2);
                    if (isGatewayRequest)
                        try {
                            return toJson("wallet encrypted" + (hasNewPassword ? " with new password" : ""));
                        } catch (IllegalStateException e) {
                            return toJson(e.getMessage());
                        }
                    else
                        return "wallet encrypted" + (hasNewPassword ? " with new password" : "");
                }
                default: {
                    if (isGatewayRequest)
                        return toJson(format("unhandled method '%s'", method));
                    else
                        throw new RuntimeException(format("unhandled method '%s'", method));

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

    private String toJson(String data) {
        Map<String, String> map = new HashMap<>() {{
            put("data", data);
        }};
        return gson.toJson(map, Map.class);
    }

}
