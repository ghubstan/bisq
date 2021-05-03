package bisq.daemon.web.service;

import bisq.daemon.web.event.MovieEvent;
import bisq.daemon.web.model.Movie;
import io.reactivex.Observable;

public interface MovieObservableService {

    Observable<Movie> getMovies();

    Observable<Movie> getMovie();

    Observable<MovieEvent> streamMovie(String movieId);

    Observable<MovieEvent> streamMovies();
}

