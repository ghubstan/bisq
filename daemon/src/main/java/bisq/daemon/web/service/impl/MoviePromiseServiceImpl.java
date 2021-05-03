package bisq.daemon.web.service.impl;


import java.util.ArrayList;
import java.util.List;

import static bisq.daemon.web.model.Movie.get2001;
import static bisq.daemon.web.model.Movie.getG1;
import static bisq.daemon.web.model.Movie.getG2;



import bisq.daemon.web.model.Movie;
import bisq.daemon.web.service.MoviePromiseService;
import ratpack.exec.Promise;

public class MoviePromiseServiceImpl implements MoviePromiseService {

    @Override
    public Promise<Movie> getMovie() {
        return Promise.value(getG1());
    }

    @Override
    public Promise<List<Movie>> getMovies() {
        List<Movie> movies = new ArrayList<>();
        movies.add(getG1());
        movies.add(getG2());
        movies.add(get2001());
        return Promise.value(movies);
    }
}
