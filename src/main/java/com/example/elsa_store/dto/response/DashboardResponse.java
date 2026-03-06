package com.example.elsa_store.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardResponse {

    private Double todayRevenue;

    private Double monthRevenue;

    private Long processingOrders;

    private Long completedOrders;

}