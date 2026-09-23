package com.gkcontas.scheduler.model;

/**
 * Tells apart a run started by the schedule from one an operator forced through the API.
 * When a routine misbehaves, the first question is always which of the two it was.
 */
public enum TriggerSource {
    SCHEDULE,
    MANUAL
}
