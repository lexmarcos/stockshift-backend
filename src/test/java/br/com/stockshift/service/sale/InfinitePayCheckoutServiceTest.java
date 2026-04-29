package br.com.stockshift.service.sale;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class InfinitePayCheckoutServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldNotSerializeRedirectUrlInCheckoutRequest() throws Exception {
        InfinitePayCheckoutService.CheckoutRequest request = new InfinitePayCheckoutService.CheckoutRequest();
        request.setHandle("merchant-handle");
        request.setOrderNsu("sale-123");
        request.setWebhookUrl("http://localhost:8080/stockshift/api/sales/infinitepay/webhook/secret-token");
        request.setItems(List.of(new InfinitePayCheckoutService.CheckoutItem(1, 1500L, "Produto")));

        JsonNode payload = objectMapper.valueToTree(request);

        assertThat(payload.has("redirect_url")).isFalse();
        assertThat(payload.get("handle").asText()).isEqualTo("merchant-handle");
        assertThat(payload.get("order_nsu").asText()).isEqualTo("sale-123");
        assertThat(payload.get("webhook_url").asText()).contains("/api/sales/infinitepay/webhook/secret-token");
    }

    @Test
    void shouldGeneratePaymentLinkAndSendWebhookUrl() {
        InfinitePayCheckoutService service = new InfinitePayCheckoutService();
        ReflectionTestUtils.setField(service, "apiBaseUrl", "http://localhost:8080/stockshift");
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://api.infinitepay.io/invoices/public/checkout/links"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/api/sales/infinitepay/webhook/secret-token")))
                .andRespond(withSuccess("{\"url\":\"https://pay.example\",\"slug\":\"abc\"}", MediaType.APPLICATION_JSON));

        InfinitePayCheckoutService.CheckoutLinkResponse response = service.generatePaymentLink(
                "merchant",
                List.of(new InfinitePayCheckoutService.CheckoutItem(1, 1500L, "Product")),
                "order-1",
                "secret-token");

        assertThat(response.getUrl()).isEqualTo("https://pay.example");
        assertThat(response.getSlug()).isEqualTo("abc");
        server.verify();
    }

    @Test
    void shouldCheckPaymentStatus() {
        InfinitePayCheckoutService service = new InfinitePayCheckoutService();
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://api.infinitepay.io/invoices/public/checkout/payment_check"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"transaction_nsu\":\"txn-1\"")))
                .andRespond(withSuccess("""
                        {"success":true,"paid":true,"amount":1500,"paid_amount":1510,"installments":1,"capture_method":"pix"}
                        """, MediaType.APPLICATION_JSON));

        InfinitePayCheckoutService.PaymentCheckResponse response =
                service.checkPayment("merchant", "order-1", "txn-1", "slug-1");

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getPaid()).isTrue();
        assertThat(response.getAmount()).isEqualTo(1500L);
        assertThat(response.getPaidAmount()).isEqualTo(1510L);
        assertThat(response.getCaptureMethod()).isEqualTo("pix");
        server.verify();
    }

    @Test
    void shouldWrapUnexpectedInfinitePayResponse() {
        InfinitePayCheckoutService service = new InfinitePayCheckoutService();
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://api.infinitepay.io/invoices/public/checkout/links"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> service.generatePaymentLink(
                "merchant",
                List.of(new InfinitePayCheckoutService.CheckoutItem(1, 1500L, "Product")),
                "order-1",
                "secret-token"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Erro ao gerar link");
    }
}
