package com.gkcontas.scheduler.exception;

public class UnknownJobException extends RuntimeException {

    public UnknownJobException(String jobName) {
        super("Unknown job: '%s'.".formatted(jobName));
    }
}
