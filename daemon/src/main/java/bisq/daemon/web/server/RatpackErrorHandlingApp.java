package bisq.daemon.web.server;


import bisq.daemon.web.handler.BisqClientErrorHandler;
import bisq.daemon.web.handler.BisqServerErrorHandler;
import io.reactivex.Observable;
import ratpack.error.ClientErrorHandler;
import ratpack.error.ServerErrorHandler;
import ratpack.rx2.RxRatpack;
import ratpack.server.RatpackServer;
import ratpack.server.ServerConfig;

public class RatpackErrorHandlingApp {

    /**
     * Try hitting http://localhost:5050/error to see the error handler in action
     *
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        RxRatpack.initialize();
        ServerConfig serverConfig = ServerConfig.of(config -> config
                .port(5050)
                .findBaseDir()
        );

        RatpackServer.start(def -> def.registryOf(regSpec -> regSpec
                .add(ServerErrorHandler.class, new BisqServerErrorHandler())
                .add(ClientErrorHandler.class, new BisqClientErrorHandler()))
                .handlers(chain -> chain
                        .get("server-error", ctx -> {
                            Observable.<String>error(new IllegalStateException("Error from observable"))
                                    .subscribe(s -> {
                                    });
                        })
                        .get(ctx -> ctx.render("Welcome to Bisq Web App"))));
    }
}
