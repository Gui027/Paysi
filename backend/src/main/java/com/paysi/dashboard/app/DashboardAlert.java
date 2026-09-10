package com.paysi.dashboard.app;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DashboardAlert(String id, String tone, String title, String description, String actionUrl) {
}
