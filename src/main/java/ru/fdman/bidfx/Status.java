package ru.fdman.bidfx;

public enum Status {
    FOLDER(Integer.MIN_VALUE),
    OK(0),
    SKIPPED(400),
    WARN(500),
    ERROR(900),
    CRITICAL(999),
    SMTH_GOES_WRONG(1000),
    ;

    Status(int priority) {
        this.priority = priority;
    }

    public int getPriority() {
        return priority;
    }

    private int priority = 0;
}
