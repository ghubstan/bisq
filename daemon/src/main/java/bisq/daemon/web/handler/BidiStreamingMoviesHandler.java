package bisq.daemon.web.handler;

import lombok.extern.slf4j.Slf4j;



import bisq.daemon.web.event.MovieEvent;
import bisq.daemon.web.service.MovieObservableService;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Observable;
import org.reactivestreams.Publisher;
import ratpack.handling.Context;
import ratpack.handling.Handler;
import ratpack.rx2.RxRatpack;
import ratpack.sse.ServerSentEvents;

@Slf4j
public class BidiStreamingMoviesHandler extends AbstractHandler implements Handler {

    @Override
    public void handle(Context ctx) throws Exception {
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
    }
}
