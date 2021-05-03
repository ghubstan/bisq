package bisq.daemon.web.server;


import com.google.common.util.concurrent.Uninterruptibles;

import java.time.Duration;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import static java.lang.System.exit;



import bisq.daemon.web.client.HttpClientApplication;
import bisq.daemon.web.event.MovieEvent;
import bisq.daemon.web.model.Movie;
import bisq.daemon.web.service.MovieObservableService;
import bisq.daemon.web.service.MoviePromiseService;
import bisq.daemon.web.service.impl.MovieObservableServiceImpl;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Observable;
import org.reactivestreams.Publisher;
import ratpack.exec.Promise;
import ratpack.handling.Handler;
import ratpack.jackson.Jackson;
import ratpack.rx2.RxRatpack;
import ratpack.server.RatpackServer;
import ratpack.sse.ServerSentEvents;

@Slf4j
public class RatpackStreamingObserveApp {
    /**
     * Try
     *      http://localhost:5050/streaming-movies
     *      http://localhost:5050/bidi-streaming-movies
     *  to see the application in action.
     */
    public static void main(String[] args) throws Exception {
        RxRatpack.initialize();

        Handler moviesPromiseHandler = ctx -> {
            MoviePromiseService promiseSvc = ctx.get(MoviePromiseService.class);
            Promise<List<Movie>> moviePromises = promiseSvc.getMovies();
            RxRatpack.observe(moviePromises)
                    .toList()
                    .subscribe(movie -> ctx.render(Jackson.json(movie)));
        };

        Handler streamingMoviesHandler = (ctx) -> {
            MovieObservableService movieSvc = ctx.get(MovieObservableService.class);
            Observable<MovieEvent> movieObs = movieSvc.streamMovies();
            Publisher<MovieEvent> publisher = RxRatpack.publisher(movieObs, BackpressureStrategy.BUFFER);
            ServerSentEvents events = ServerSentEvents.serverSentEvents(publisher, movieEventEvent -> {
                var item = movieEventEvent.getItem();
                movieEventEvent.id("Event-Id-" + System.currentTimeMillis())
                        .event("Event-Type-Movie-Data")
                        .data(item.getData());
                log.trace("SERVER -> {}", movieEventEvent.toString());
            });
            events.render(ctx);
            log.info("Rendered all events.");
        };

        Handler bidiStreamMovieHandler = (ctx) -> {
            String movieId = ctx.getPathTokens().get("movieId");
            MovieObservableService movieSvc = ctx.get(MovieObservableService.class);
            Observable<MovieEvent> movieObs = movieSvc.streamMovie(movieId);
            Publisher<MovieEvent> publisher = RxRatpack.publisher(movieObs, BackpressureStrategy.BUFFER);
            ServerSentEvents events = ServerSentEvents.serverSentEvents(publisher, movieEventEvent -> {
                var item = movieEventEvent.getItem();
                movieEventEvent.id("Event-Id-" + System.currentTimeMillis())
                        .event("Event-Type-Movie-Data")
                        .data(item.getData());
                log.trace("SERVER -> {}", movieEventEvent);
            });
            events.render(ctx);
            log.info("Rendered all events.");
        };

        Handler bidiStreamAllMoviesHandler = (ctx) -> {
            MovieObservableService moviesSvc = ctx.get(MovieObservableService.class);
            Observable<MovieEvent> moviesObs = moviesSvc.streamMovies();
            Publisher<MovieEvent> moviesPublisher = RxRatpack.publisher(moviesObs, BackpressureStrategy.BUFFER);
            ServerSentEvents moviesEvents = ServerSentEvents.serverSentEvents(moviesPublisher, movieEventEvent -> {
                var item = movieEventEvent.getItem();
                movieEventEvent.id("Event-Id-" + System.currentTimeMillis())
                        .event("Event-Type-Movie-Data")
                        .data(item.getData());
                log.trace("SERVER -> {}", movieEventEvent);
            });
            moviesEvents.render(ctx);
            log.info("Rendered all events.");
        };

        RatpackServer.start(def -> def
                .registryOf(regSpec -> regSpec.add(MovieObservableService.class, new MovieObservableServiceImpl()))
                .handlers(chain -> chain
                        .get("streaming-movies", streamingMoviesHandler)
                        .get("bidi-streaming-movies", bidiStreamAllMoviesHandler)
                        .get("bidi-streaming-movies/:movieId", bidiStreamMovieHandler)));

        HttpClientApplication httpClientApplication = new HttpClientApplication();
        httpClientApplication.doStreamingMoviesRequest();
        httpClientApplication.doBidiStreamingMoviesRequest();

        Uninterruptibles.sleepUninterruptibly(Duration.ofSeconds(15));
        exit(0);
    }
}
