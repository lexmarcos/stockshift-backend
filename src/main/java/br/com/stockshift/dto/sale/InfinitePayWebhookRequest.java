package br.com.stockshift.dto.sale;

import lombok.Data;

import java.util.List;

@Data
public class InfinitePayWebhookRequest {
    private String invoice_slug;
    private Long amount;
    private Long paid_amount;
    private Integer installments;
    private String capture_method;
    private String transaction_nsu;
    private String order_nsu;
    private String receipt_url;
    private List<Object> items;
}
