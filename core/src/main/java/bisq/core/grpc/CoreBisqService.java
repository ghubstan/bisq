package bisq.core.grpc;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class CoreBisqService {

    public String call(String uri) {
        return "response string for uri " + uri;
    }

}
