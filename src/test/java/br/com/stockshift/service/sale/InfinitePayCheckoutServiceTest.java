package br.com.stockshift.service.sale;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InfinitePayCheckoutServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldNotSerializeRedirectUrlInCheckoutRequest() throws Exception {
        InfinitePayCheckoutService.CheckoutRequest request = new InfinitePayCheckoutService.CheckoutRequest();
        request.setHandle("merchant-handle");
        request.setOrderNsu("sale-123");
        request.setWebhookUrl("http://localhost:8080/stockshift/api/sales/infinitepay/webhook");
        request.setItems(List.of(new InfinitePayCheckoutService.CheckoutItem(1, 1500L, "Produto")));

        JsonNode payload = objectMapper.valueToTree(request);

        assertThat(payload.has("redirect_url")).isFalse();
        assertThat(payload.get("handle").asText()).isEqualTo("merchant-handle");
        assertThat(payload.get("order_nsu").asText()).isEqualTo("sale-123");
        assertThat(payload.get("webhook_url").asText()).contains("/api/sales/infinitepay/webhook");
    }
}
