package com.paysi.admin.domain;

import java.util.UUID;

public record AdminSearchResult(String type, UUID id, String status, String summary) {}
