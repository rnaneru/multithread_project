package org.example.vacancyparser.hh;

public class HhClientException extends RuntimeException {

    public HhClientException(String message) {
        super(message);
    }

    public HhClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
