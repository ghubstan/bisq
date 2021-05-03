package bisq.daemon.web.service;

import java.util.List;



import bisq.daemon.web.model.Movie;
import ratpack.exec.Promise;


public interface MoviePromiseService {

    Promise<List<Movie>> getMovies();

    Promise<Movie> getMovie();

}
