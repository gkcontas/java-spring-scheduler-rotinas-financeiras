package com.gkcontas.scheduler.exception;

public class InvalidCronExpressionException extends RuntimeException {

    public InvalidCronExpressionException(String cron) {
        super(("Invalid cron expression: '%s'. Spring uses six fields "
                + "(second minute hour day-of-month month day-of-week), e.g. '0 0 3 * * *'.").formatted(cron));
    }
}
