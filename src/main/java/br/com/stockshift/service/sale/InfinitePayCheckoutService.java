package br.com.stockshift.service.sale;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
@Slf4j
public class InfinitePayCheckoutService {

    private static final String CHECKOUT_URL = "https://api.infinitepay.io/invoices/public/checkout/links";

    @Value("${app.api-base-url:http://localhost:8080/stockshift}")
    private String apiBaseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public CheckoutLinkResponse generatePaymentLink(String handle, List<CheckoutItem> items, String orderNsu) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        CheckoutRequest payload = new CheckoutRequest();
        payload.setHandle(handle);
        payload.setItems(items);
        payload.setOrderNsu(orderNsu);
        payload.setWebhookUrl(apiBaseUrl + "/api/sales/infinitepay/webhook");

        HttpEntity<CheckoutRequest> entity = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<CheckoutLinkResponse> response = restTemplate.exchange(
                    CHECKOUT_URL, HttpMethod.POST, entity, CheckoutLinkResponse.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Payment link generated for order {}", orderNsu);
                return response.getBody();
            }

            log.error("Unexpected response from InfinitePay: {}", response.getStatusCode());
            throw new RuntimeException("Erro ao gerar link de pagamento");
        } catch (Exception e) {
            log.error("Error calling InfinitePay Checkout API for order {}: {}", orderNsu, e.getMessage());
            throw new RuntimeException("Erro ao gerar link de pagamento: " + e.getMessage());
        }
    }

    @Data
    public static class CheckoutRequest {
        private String handle;
        private List<CheckoutItem> items;
        @JsonProperty("order_nsu")
        private String orderNsu;
        @JsonProperty("webhook_url")
        private String webhookUrl;
    }

    @Data
    public static class CheckoutItem {
        private int quantity;
        private long price;
        private String description;

        public CheckoutItem() {}

        public CheckoutItem(int quantity, long price, String description) {
            this.quantity = quantity;
            this.price = price;
            this.description = description;
        }
    }

    @Data
    public static class CheckoutLinkResponse {
        private String url;
        private String slug;
    }
}
