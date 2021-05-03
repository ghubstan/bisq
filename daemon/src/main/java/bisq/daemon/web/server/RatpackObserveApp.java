package bisq.daemon.web.server;


import java.util.List;



import bisq.daemon.web.model.Movie;
import bisq.daemon.web.service.MoviePromiseService;
import bisq.daemon.web.service.impl.MoviePromiseServiceImpl;
import ratpack.exec.Promise;
import ratpack.handling.Handler;
import ratpack.jackson.Jackson;
import ratpack.rx2.RxRatpack;
import ratpack.server.RatpackServer;

public class RatpackObserveApp {
    /**
     * Try hitting http://localhost:5050/movies or http://localhost:5050/movie to see the application in action.
     *
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        RxRatpack.initialize();

        Handler moviePromiseHandler = ctx -> {
            MoviePromiseService promiseSvc = ctx.get(MoviePromiseService.class);
            Promise<Movie> moviePromise = promiseSvc.getMovie();
            RxRatpack.single(moviePromise)
                    .subscribe(movie -> ctx.render(Jackson.json(movie)));
        };

        Handler moviesPromiseHandler = ctx -> {
            MoviePromiseService promiseSvc = ctx.get(MoviePromiseService.class);
            Promise<List<Movie>> moviePromises = promiseSvc.getMovies();
            RxRatpack.observe(moviePromises)
                    .toList()
                    .subscribe(movie -> ctx.render(Jackson.json(movie)));
        };

        RatpackServer.start(def -> def
                .registryOf(regSpec -> regSpec.add(MoviePromiseService.class, new MoviePromiseServiceImpl()))
                .handlers(chain -> chain.get("movie", moviePromiseHandler)
                        .get("movies", moviesPromiseHandler)));


    }
}
