package bisq.daemon.web.server;

import bisq.daemon.web.model.Movie;
import bisq.daemon.web.service.MovieObservableService;
import bisq.daemon.web.service.impl.MovieObservableServiceImpl;
import io.reactivex.Observable;
import ratpack.jackson.Jackson;
import ratpack.rx2.RxRatpack;
import ratpack.server.RatpackServer;

public class RatpackParallelismApp {

    /**
     * Try hitting http://localhost:5050/movies to see the application in action.
     *
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        RxRatpack.initialize();
        RatpackServer.start(def -> def.registryOf(regSpec -> regSpec.add(MovieObservableService.class, new MovieObservableServiceImpl()))
                .handlers(chain -> chain.get("movies", ctx -> {
                    MovieObservableService movieSvc = ctx.get(MovieObservableService.class);
                    Observable<Movie> movieObs = movieSvc.getMovies();
                    Observable<String> upperCasedNames = movieObs.compose(RxRatpack::forkEach)
                            .map(movie -> movie.getName().toUpperCase())
                            .serialize();
                    RxRatpack.promiseAll(upperCasedNames)
                            .then(movie -> {
                                ctx.render(Jackson.json(movie));
                            });
                })));
    }
}
