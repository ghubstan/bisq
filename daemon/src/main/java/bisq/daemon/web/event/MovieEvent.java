package bisq.daemon.web.event;

import bisq.daemon.web.model.Movie;
import ratpack.func.Function;
import ratpack.sse.Event;

public class MovieEvent implements Event<Movie> {

    private final Movie movie;

    public MovieEvent(Movie movie) {
        this.movie = movie;
    }

    @Override
    public Movie getItem() {
        return movie;
    }

    @Override
    public String getId() {
        return movie.getName();
    }

    @Override
    public String getEvent() {
        return "Movie-" + movie.getId() + "-Event";
    }

    @Override
    public String getData() {
        return movie.toString();
    }

    @Override
    public String getComment() {
        return "Movie-" + movie.getId() + "-Comment";
    }

    @Override
    public Event<Movie> id(Function<? super Movie, String> function) throws Exception {
        return this;
    }

    @Override
    public Event<Movie> id(String id) {
        return this;
    }

    @Override
    public Event<Movie> event(Function<? super Movie, String> function) throws Exception {
        return this;
    }

    @Override
    public Event<Movie> event(String event) {
        return this;
    }

    @Override
    public Event<Movie> data(Function<? super Movie, String> function) throws Exception {
        return this;
    }

    @Override
    public Event<Movie> data(String data) {
        return this;
    }

    @Override
    public Event<Movie> comment(String comment) {
        movie.setComment(comment);
        return this;
    }

    @Override
    public String toString() {
        return "MovieEvent{" +
                "movie=" + movie +
                '}';
    }
}
