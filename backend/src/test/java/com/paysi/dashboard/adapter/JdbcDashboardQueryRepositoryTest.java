package com.paysi.dashboard.adapter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcDashboardQueryRepositoryTest {
    @Test
    void masksBuyerNameBeforeItReachesThePanel() {
        assertThat(JdbcDashboardQueryRepository.maskName("Marina Duarte")).isEqualTo("M*** D***");
        assertThat(JdbcDashboardQueryRepository.maskName("Marina")).isEqualTo("M***");
        assertThat(JdbcDashboardQueryRepository.maskName(" ")).isEqualTo("Comprador");
    }
}
