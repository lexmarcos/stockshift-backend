package br.com.stockshift.service.sale;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;

@Service
@Slf4j
public class InfinitePayCheckoutService {

    private static final String CHECKOUT_URL = "https://api.infinitepay.io/invoices/public/checkout/links";
    private static final String PAYMENT_CHECK_URL = "https://api.infinitepay.io/invoices/public/checkout/payment_check";

    @Value("${app.api-base-url:http://localhost:8080/stockshift}")
    private String apiBaseUrl;

    private final RestTemplate restTemplate;

    public InfinitePayCheckoutService() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        this.restTemplate = new RestTemplate(requestFactory);
    }

    public CheckoutLinkResponse generatePaymentLink(
            String handle, List<CheckoutItem> items, String orderNsu, String webhookToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        CheckoutRequest payload = new CheckoutRequest();
        payload.setHandle(handle);
        payload.setItems(items);
        payload.setOrderNsu(orderNsu);
        payload.setWebhookUrl(apiBaseUrl + "/api/sales/infinitepay/webhook/" + webhookToken);

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

    public PaymentCheckResponse checkPayment(String handle, String orderNsu, String transactionNsu, String slug) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        PaymentCheckRequest payload = new PaymentCheckRequest();
        payload.setHandle(handle);
        payload.setOrderNsu(orderNsu);
        payload.setTransactionNsu(transactionNsu);
        payload.setSlug(slug);

        try {
            ResponseEntity<PaymentCheckResponse> response = restTemplate.exchange(
                    PAYMENT_CHECK_URL, HttpMethod.POST, new HttpEntity<>(payload, headers), PaymentCheckResponse.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }

            log.warn("Unexpected InfinitePay payment_check response for order {}: {}", orderNsu, response.getStatusCode());
            throw new RuntimeException("Erro ao consultar status do pagamento");
        } catch (Exception e) {
            log.warn("Error calling InfinitePay payment_check for order {}: {}", orderNsu, e.getMessage());
            throw new RuntimeException("Erro ao consultar status do pagamento: " + e.getMessage());
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

    @Data
    public static class PaymentCheckRequest {
        private String handle;
        @JsonProperty("order_nsu")
        private String orderNsu;
        @JsonProperty("transaction_nsu")
        private String transactionNsu;
        private String slug;
    }

    @Data
    public static class PaymentCheckResponse {
        private Boolean success;
        private Boolean paid;
        private Long amount;
        @JsonProperty("paid_amount")
        private Long paidAmount;
        private Integer installments;
        @JsonProperty("capture_method")
        private String captureMethod;
    }
}
