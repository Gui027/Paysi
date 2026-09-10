package com.paysi.dashboard.app;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DashboardBlock<T>(State state, T data, String code, String message) {
    public enum State { SUCCESS, EMPTY, ERROR }

    public static <T> DashboardBlock<T> success(T data) {
        return new DashboardBlock<>(State.SUCCESS, data, null, null);
    }

    public static <T> DashboardBlock<T> empty() {
        return new DashboardBlock<>(State.EMPTY, null, null, null);
    }

    public static <T> DashboardBlock<T> error(String code, String message) {
        return new DashboardBlock<>(State.ERROR, null, code, message);
    }
}
