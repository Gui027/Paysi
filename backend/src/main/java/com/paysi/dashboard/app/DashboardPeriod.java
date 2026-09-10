package com.paysi.dashboard.app;

import java.time.Instant;

public record DashboardPeriod(String preset, Instant from, Instant to) {
}
