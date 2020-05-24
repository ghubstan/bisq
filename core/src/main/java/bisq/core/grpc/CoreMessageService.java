package bisq.core.grpc;

import io.grpc.StatusRuntimeException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

import javax.inject.Inject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import static java.lang.String.format;

@Slf4j
class CoreMessageService {

    private enum Method {
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

        var parser = new OptionParser();
        OptionSet options = parser.parse(params);
        @SuppressWarnings("unchecked") var nonOptionArgs = (List<String>) options.nonOptionArguments();
        var methodName = nonOptionArgs.get(0);
        final Method method;
        try {
            method = Method.valueOf(methodName);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(format("'%s' is not a supported method", methodName));
        }

        try {
            switch (method) {
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
                    if (nonOptionArgs.size() < 2) {
                        if (isGatewayRequest)
                            return toJson("no password specified");
                        else
                            throw new IllegalArgumentException("no password specified");
                    }
                    var hasNewPassword = nonOptionArgs.size() == 3;
                    var newPassword = "";
                    if (hasNewPassword)
                        newPassword = nonOptionArgs.get(2);
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


        /*
        if (methodName.equals("getversion")) {
            if (isGatewayRequest)
                return toJson(coreApi.getVersion());
            else
                return coreApi.getVersion();
        }

        if (methodName.equals("getbalance")) {
            if (isGatewayRequest)
                try {
                    return toJson(String.valueOf(walletService.getAvailableBalance()));
                } catch (IllegalStateException e) {
                    return toJson(e.getMessage());
                }
            else
                return String.valueOf(walletService.getAvailableBalance());
        }
        return "echoed command params " + params;
         */
    }

    private String toJson(String data) {
        Map<String, String> map = new HashMap<>() {{
            put("data", data);
        }};
        return gson.toJson(map, Map.class);
    }

}
