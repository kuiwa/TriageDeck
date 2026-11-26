package com.triagedeck.alert.domain;

public enum Severity {
    CRITICAL(4),
    HIGH(3),
    MEDIUM(2),
    LOW(1),
    INFO(0);

    private final int rank;

    Severity(int rank) {
        this.rank = rank;
    }

    public boolean isMoreSevereThan(Severity other) {
        return rank > other.rank;
    }
}
