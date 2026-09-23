package com.gkcontas.scheduler.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * The message is spelled out rather than left to Bean Validation's default.
 *
 * <p>Default constraint messages are resolved from a resource bundle using the JVM's
 * locale, so the very same API answers "must not be blank" on one machine and
 * "não deve estar em branco" on another. An API contract cannot depend on the locale of
 * the host it happens to run on.
 */
public record UpdateCronRequest(
        @NotBlank(message = "must not be blank") String cronExpression) {
}
