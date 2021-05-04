package bisq.daemon.web.server;


import bisq.core.api.CoreApi;

import com.google.inject.Inject;

import lombok.extern.slf4j.Slf4j;



import bisq.daemon.web.handler.GetVersionHandler;
import io.reactivex.Observable;
import ratpack.guice.Guice;
import ratpack.rx2.RxRatpack;
import ratpack.server.RatpackServer;
import ratpack.server.ServerConfig;

@Slf4j
public class WebServer {

    // Hit http://localhost:5050/error to see the error handler in action.

    private RatpackServer ratpackServer;

    private final CoreApi coreApi;

    @Inject
    public WebServer(CoreApi coreApi) {
        this.coreApi = coreApi;
        RxRatpack.initialize();
    }

    public void start() {

        try {
            ServerConfig serverConfig = ServerConfig.of(config -> config
                    .port(5050)
                    .findBaseDir()
            );

            this.ratpackServer = RatpackServer.start(server -> server
                    .serverConfig(serverConfig)
                    .registry(Guice.registry(bindings -> bindings.module(WebAppDependencyModule.class).add(CoreApi.class, coreApi)))
                    .handlers(chain -> chain
                            .get("server-error", ctx -> {
                                Observable.<String>error(new IllegalStateException("Server error from observable"))
                                        .subscribe(s -> {
                                        });
                            })
                            // .get("streaming-movies", ctx -> ctx.get(StreamingMoviesHandler.class).handle(ctx))
                            // .get("bidi-streaming-movies", ctx -> ctx.get(BidiStreamingMoviesHandler.class).handle(ctx))
                            .get("version", ctx -> ctx.get(GetVersionHandler.class).handle(ctx))
                            .get(ctx -> ctx.render("Welcome to Bisq Web App"))
                    )
            );

            log.info("Ratpack running... set breakpoint here to check registry bindings.");

        } catch (Exception ex) {
            log.error("", ex);
            throw new RuntimeException(ex);
        }
    }

    public void shutdown() {
        if (ratpackServer.isRunning()) {
            try {
                ratpackServer.stop();
            } catch (Exception ex) {
                log.error("Error stopping web server.", ex);
            }
        }
    }
}

/*
Using Guice

   public static void main(String[] args) throws Exception {

        // https://www.baeldung.com/ratpack-rxjava
        RxRatpack.initialize();

        // https://www.programcreek.com/java-api-examples/?api=ratpack.server.ServerConfig
        ServerConfig serverConfig = ServerConfig.of(config -> config
                .port(5050)
                .findBaseDir()
        );

        // https://www.programcreek.com/java-api-examples/?api=ratpack.server.RatpackServer
        RatpackServer.start(server -> server
                .serverConfig(serverConfig)
                .registry(Guice.registry(bindings -> bindings.module(DependencyModule.class)))
                .handlers(chain -> chain
                        .get("foo", ctx -> ctx.get(FooHandler.class).handle(ctx))
                        .get("pump", ctx -> {
                            var dataPumpService = ctx.get(DataPumpService.class);
                            ctx.render(dataPumpService.generate());
                        })
                        .get("shit", ctx -> {
                            var dataPumpService = ctx.get(DataPumpService.class);
                            // ctx.render("streaming shit...");
                            dataPumpService.streamingShit(ctx);
                        })
                        .get(ctx -> ctx.render("Welcome to Bisq Web App"))
                )
        );
    }

 */
