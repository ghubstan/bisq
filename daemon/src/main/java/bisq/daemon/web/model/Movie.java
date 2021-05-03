package bisq.daemon.web.model;

import lombok.Getter;
import lombok.Setter;

/**
 * POJO class for Movie object
 */
@Getter
@Setter
public class Movie {

    private String id;
    private String name;
    private String year;
    private String director;
    private Double rating;
    private String comment;

    public static Movie getMovie(String movieId) {
        switch (movieId) {
            case "G1":
                return getG1();
            case "G2":
                return getG2();
            case "2001":
                return get2001();
            case "M":
                return getM();
            default:
                throw new IllegalArgumentException("No movie with id " + movieId);
        }
    }

    public static Movie getG1() {
        Movie movie = new Movie();
        movie.setId("G1");
        movie.setName("The Godfather");
        movie.setYear("1972");
        movie.setDirector("Coppola");
        movie.setRating(9.2);
        movie.setComment("No comment");
        return movie;
    }

    public static Movie getG2() {
        Movie movie = new Movie();
        movie.setId("G2");
        movie.setName("The Godfather Part 2");
        movie.setYear("1974");
        movie.setDirector("Coppola");
        movie.setRating(9.0);
        movie.setComment("No comment");
        return movie;
    }

    public static Movie get2001() {
        Movie movie = new Movie();
        movie.setId("2001");
        movie.setName("2001 Space Odyssey");
        movie.setYear("1969");
        movie.setDirector("Kubrick");
        movie.setRating(10.0);
        movie.setComment("No comment");
        return movie;
    }

    public static Movie getM() {
        Movie movie = new Movie();
        movie.setId("M");
        movie.setName("Mash");
        movie.setYear("1970");
        movie.setDirector("Robert Altman");
        movie.setRating(8.0);
        movie.setComment("No comment");
        return movie;
    }

    @Override
    public String toString() {
        return "Movie{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", year='" + year + '\'' +
                ", director='" + director + '\'' +
                ", rating=" + rating +
                ", comment='" + comment + '\'' +
                '}';
    }
}

