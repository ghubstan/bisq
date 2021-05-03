package bisq.daemon.web.server;


import bisq.daemon.web.model.Movie;
import bisq.daemon.web.service.MovieObservableService;
import bisq.daemon.web.service.impl.MovieObservableServiceImpl;
import io.reactivex.Observable;
import ratpack.handling.Handler;
import ratpack.jackson.Jackson;
import ratpack.rx2.RxRatpack;
import ratpack.server.RatpackServer;

public class RatpackPromiseApp {

    /**
     * Try hitting http://localhost:5050/movies or http://localhost:5050/movie to see the application in action.
     *
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        RxRatpack.initialize();

        Handler movieHandler = (ctx) -> {
            MovieObservableService movieSvc = ctx.get(MovieObservableService.class);
            Observable<Movie> movieObs = movieSvc.getMovie();
            RxRatpack.promiseAll(movieObs)
                    .then(movie -> ctx.render(Jackson.json(movie)));
        };

        Handler moviesHandler = (ctx) -> {
            MovieObservableService movieSvc = ctx.get(MovieObservableService.class);
            Observable<Movie> movieObs = movieSvc.getMovies();
            RxRatpack.promiseAll(movieObs)
                    .then(movie -> ctx.render(Jackson.json(movie)));
        };

        RatpackServer.start(def -> def
                .registryOf(rSpec -> rSpec.add(MovieObservableService.class, new MovieObservableServiceImpl()))
                .handlers(chain -> chain.get("movie", movieHandler)
                        .get("movies", moviesHandler)));
    }

}
