package com.paysi.payment.provider.asaas.dto;

import java.util.List;

public record AsaasListResponse<T>(List<T> data, Boolean hasMore, Integer totalCount) {
    public List<T> dataOrEmpty() {
        return data == null ? List.of() : data;
    }
}
