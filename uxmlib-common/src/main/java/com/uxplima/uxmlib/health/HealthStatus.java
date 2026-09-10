package com.uxplima.uxmlib.health;

/** How bad one answer is. The order is the severity, worst last, because a report folds by it. */
public enum HealthStatus {

    /** Nothing to say: it works. */
    OK,

    /** It works and something about it is not what the operator meant. */
    WARN,

    /** It does not work. */
    FAIL
}
