package bisq.daemon.web.handler;

import bisq.core.api.CoreApi;

import lombok.extern.slf4j.Slf4j;



import ratpack.handling.Context;
import ratpack.handling.Handler;

@Slf4j
public class GetVersionHandler extends AbstractHandler implements Handler {

    @Override
    public void handle(Context ctx) throws Exception {
        String version = ctx.get(CoreApi.class).getVersion();
        ctx.render(version);
    }
}
