package bisq.core.grpc;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class CoreMessageService {

    // TODO if params are wrapped in json, return json, else a string.

    public String call(String params) {
        log.info("gRPC client request:  {}", params);
        return "echoed command params " + params;
    }

}
