package bisq.daemon.web.service.impl;


import com.google.common.util.concurrent.Uninterruptibles;

import static bisq.daemon.web.model.Movie.get2001;
import static bisq.daemon.web.model.Movie.getG1;
import static bisq.daemon.web.model.Movie.getG2;
import static bisq.daemon.web.model.Movie.getM;
import static java.util.concurrent.TimeUnit.MILLISECONDS;



import bisq.daemon.web.event.MovieEvent;
import bisq.daemon.web.model.Movie;
import bisq.daemon.web.service.MovieObservableService;
import io.reactivex.Observable;

public class MovieObservableServiceImpl implements MovieObservableService {

    @Override
    public Observable<Movie> getMovie() {
        return Observable.just(getG1());
    }

    @Override
    public Observable<Movie> getMovies() {
        return Observable.just(getG1(), getG2(), get2001());
    }

    @Override
    public Observable<MovieEvent> streamMovie(String movieId) {
        Observable observable = Observable.<MovieEvent>create(emitter -> {
            Uninterruptibles.sleepUninterruptibly(2500, MILLISECONDS);
            Movie movie = Movie.getMovie(movieId);
            emitter.onNext(new MovieEvent(movie));
            emitter.onComplete();
        });
        return observable;
    }

    @Override
    public Observable<MovieEvent> streamMovies() {
        Observable observable = Observable.<MovieEvent>create(emitter -> {
            Uninterruptibles.sleepUninterruptibly(2500, MILLISECONDS);
            emitter.onNext(new MovieEvent(getG1()));
            Uninterruptibles.sleepUninterruptibly(2500, MILLISECONDS);
            emitter.onNext(new MovieEvent(getG2()));
            Uninterruptibles.sleepUninterruptibly(2500, MILLISECONDS);
            emitter.onNext(new MovieEvent(get2001()));
            Uninterruptibles.sleepUninterruptibly(2500, MILLISECONDS);
            emitter.onNext(new MovieEvent(getM()));
            emitter.onComplete();
        });
        return observable;
    }
}
