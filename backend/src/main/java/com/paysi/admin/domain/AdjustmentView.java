package com.paysi.admin.domain;

import java.util.UUID;

public record AdjustmentView(UUID id, String status, boolean autoApproved) {}
