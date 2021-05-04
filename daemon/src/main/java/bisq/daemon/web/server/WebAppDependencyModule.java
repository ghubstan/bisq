package bisq.daemon.web.server;

import com.google.inject.AbstractModule;



import bisq.daemon.web.handler.BidiStreamingMoviesHandler;
import bisq.daemon.web.handler.BisqClientErrorHandler;
import bisq.daemon.web.handler.BisqServerErrorHandler;
import bisq.daemon.web.handler.GetVersionHandler;
import bisq.daemon.web.handler.StreamingMoviesHandler;
import bisq.daemon.web.service.MovieObservableService;
import bisq.daemon.web.service.MoviePromiseService;
import bisq.daemon.web.service.impl.MovieObservableServiceImpl;
import bisq.daemon.web.service.impl.MoviePromiseServiceImpl;
import ratpack.error.ClientErrorHandler;
import ratpack.error.ServerErrorHandler;

public class WebAppDependencyModule extends AbstractModule {

    public void configure() {
        bind(ServerErrorHandler.class).toInstance(new BisqServerErrorHandler());
        bind(ClientErrorHandler.class).toInstance(new BisqClientErrorHandler());

        // demo movie streaming services
        bind(MovieObservableService.class).toInstance(new MovieObservableServiceImpl());
        bind(MoviePromiseService.class).toInstance(new MoviePromiseServiceImpl());

        // demo movie streaming web handlers
        bind(BidiStreamingMoviesHandler.class).toInstance(new BidiStreamingMoviesHandler());
        bind(StreamingMoviesHandler.class).toInstance(new StreamingMoviesHandler());

        // bisq core api web handlers
        bind(GetVersionHandler.class).toInstance(new GetVersionHandler());
    }
}
