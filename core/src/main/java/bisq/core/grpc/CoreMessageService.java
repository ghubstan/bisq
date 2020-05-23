package bisq.core.grpc;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

import javax.inject.Inject;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class CoreMessageService {

    private final CoreApi coreApi;
    private final CoreWalletService walletService;

    @Inject
    public CoreMessageService(CoreApi coreApi, CoreWalletService walletService) {
        this.coreApi = coreApi;
        this.walletService = walletService; // TODO inject into coreApi instead of this
    }

    public String call(String params, boolean isGatewayRequest) {
        log.info("RPC request: '{}'", params);

        if (params.isEmpty()) {
            // TODO How do you return 404, not HTTP/1.1 200 OK?
            //      Does bitcoind rpc also return 200 on error? (Monkey see monkey do.)
            if (isGatewayRequest)
                return toJson("no method specified");
            else
                throw new IllegalArgumentException("no method specified");
        }

        var parser = new OptionParser();
        OptionSet options = parser.parse(params);
        @SuppressWarnings("unchecked") var nonOptionArgs = (List<String>) options.nonOptionArguments();
        var methodName = nonOptionArgs.get(0);
        log.info("Calling method {}", methodName);

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
    }


    private String toJson(String data) {
        return "{\"data\": \"" + data + "\"}";
    }

}
