import config.AppConfig;
import com.accela.pianoforte.routes.PaymentRoute;
import com.accela.pianoforte.routes.Processors;
import com.accela.pianoforte.services.TransactionStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vavr.Tuple2;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;

import static fixtures.Fixtures.paymentResponse;
import static fixtures.Fixtures.paymentResponseUrlencoded;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PaymentRouteTest extends CamelTestSupport {
    private static final TypeReference<ArrayList<JsonNode>> typeWitness =
            new TypeReference<ArrayList<JsonNode>>() {};
    private static final String jsonRequestCC =
            "{\"personalName\": {" +
                "\"firstName\":\"Karl\"," +
                "\"lastName\":\"Marx\"" +
             "}," +
             "\"contact\": {" +
                "\"street1\":\"1, Main Street\"," +
                "\"city\":\"Burton\"," +
                "\"state\":\"CA\"," +
                "\"postCode\":\"12345\"" +
             "}," +
             "\"clientLocation\":\"http://localhost:9090/pianoforte/checkout\"," +
             "\"transactionType\":\"CC\",\"amount\":123.34," +
             "\"transactionId\":\"urn:test-agency:transaction-id:1586197589861\"}";
    private static final String jsonRequestEC =
            "{\"personalName\": {" +
                    "\"firstName\":\"Karl\"," +
                    "\"lastName\":\"Marx\"" +
                    "}," +
                    "\"contact\": {" +
                    "\"street1\":\"1, Main Street\"," +
                    "\"city\":\"Burton\"," +
                    "\"state\":\"CA\"," +
                    "\"postCode\":\"12345\"" +
                    "}," +
                    "\"clientLocation\":\"http://localhost:9090/pianoforte/checkout\"," +
                    "\"transactionType\":\"EC\",\"amount\":123.34," +
                    "\"transactionId\":\"urn:test-agency:transaction-id:1586197589861\"}";

    @Test
    @DisplayName("Credit Card Payment request should return redirect query document")
    public void testCCPaymentRequest() throws JsonProcessingException {
        final String response = template.requestBodyAndHeader("direct:payment-checkout",
                jsonRequestCC, Exchange.CONTENT_TYPE, "application/json", String.class);

        final JsonNode result = (new ObjectMapper()).readTree(response);
        assertEquals("POST", result.get("method").asText());
        assertEquals("application/x-www-form-urlencoded", result.get("contentType").asText());
        assertEquals("https://sandbox.paymentsgateway.net/swp/co/default.aspx", result.get("url").asText());
        final Map<String, String> fieldMap = mapResponse(result);
        assertEquals("Karl", fieldMap.get("pg_billto_postal_name_first"));
        assertEquals("Marx", fieldMap.get("pg_billto_postal_name_last"));
        assertEquals("9F3FA809B8", fieldMap.get("pg_api_login_id"));
        assertEquals("10", fieldMap.get("pg_transaction_type"));
        assertEquals("1.0", fieldMap.get("pg_version_number"));
        assertEquals("123.34", fieldMap.get("pg_total_amount"));
        assertEquals("637134786000000000", fieldMap.get("pg_utc_time"));
        assertTrue(fieldMap.containsKey("pg_utc_time"));
        assertEquals("urn:test-agency:transaction-id:1586197589861",fieldMap.get("pg_transaction_order_number"));
        assertEquals("3A98C930C5620F4BF1BABEEEA46C7DC9", fieldMap.get("pg_ts_hash"));
        assertTrue(fieldMap.containsKey("pg_ts_hash"));
        assertEquals(
                "http://localhost:9090/pianoforte/checkout/complete/urn%3Atest-agency%3Atransaction-id%3A1586197589861",
                fieldMap.get("pg_continue_url"));
        assertEquals(
                "http://localhost:9090/pianoforte/checkout/complete/urn%3Atest-agency%3Atransaction-id%3A1586197589861",
                fieldMap.get("pg_cancel_url"));
        assertEquals("http://localhost:9090/pianoforte/api/payment/return",fieldMap.get("pg_return_url"));
    }

    @Test
    @DisplayName("eCheck Payment request should return redirect query document")
    public void testECPaymentRequest() throws JsonProcessingException {
        final String response = template.requestBodyAndHeader("direct:payment-checkout",
                jsonRequestEC, Exchange.CONTENT_TYPE, "application/json", String.class);

        final JsonNode result = (new ObjectMapper()).readTree(response);
        final Map<String, String> fieldMap = mapResponse(result);
        assertEquals("20", fieldMap.get("pg_transaction_type"));
    }

    private Map<String, String> mapResponse(final JsonNode result) {
        return new ObjectMapper().convertValue(result.get("data"), typeWitness)
                .stream()
                .map(object -> object.fields().next())
                .map(node -> new Tuple2<>(node.getKey(), node.getValue().asText()))
                .collect(Collectors.toMap(Tuple2::_1, Tuple2::_2));
    }

    @Test
    @DisplayName("Payment response from provider should be transformed")
    public void testPaymentResponse() throws JsonProcessingException {
        final String response = template.requestBodyAndHeaders(
                "direct:payment-response", paymentResponseUrlencoded, paymentResponse, String.class);
        final JsonNode result = (new ObjectMapper()).readTree(response);
        final JsonNode outcome = result.get("paymentOutcome");
        assertEquals("1200.0", result.get("amount").asText());
        assertEquals("TEST APPROVAL", outcome.get("responseText").asText());
        assertEquals("A01", outcome.get("responseCode").asText());
        assertEquals("A", outcome.get("responseType").asText());
        assertEquals("965fc3ec-f221-49ac-b54e-4e3fb4f3ce20", outcome.get("traceNumber").asText());
        assertEquals("6RW586", outcome.get("authorizationCode").asText());
        assertEquals("10", result.get("transactionType").asText());
        assertEquals("urn:test-agency:transaction-id:1586338120514", result.get("transactionId").asText());
    }

    @Test
    @DisplayName("Payment response from provider should be cached")
    public void testPaymentResponseCache() throws JsonProcessingException {
        template.sendBodyAndHeaders("direct:payment-response", paymentResponseUrlencoded, paymentResponse);
        final String response = template.requestBodyAndHeader(
                "direct:transaction-query", null, "id",
                "urn:test-agency:transaction-id:1586338120514", String.class);
        final JsonNode result = (new ObjectMapper()).readTree(response);

        final JsonNode outcome = result.get("paymentOutcome");
        assertEquals("1200.0", result.get("amount").asText());
        assertEquals("TEST APPROVAL", outcome.get("responseText").asText());
        assertEquals("A01", outcome.get("responseCode").asText());
        assertEquals("A", outcome.get("responseType").asText());
        assertEquals("965fc3ec-f221-49ac-b54e-4e3fb4f3ce20", outcome.get("traceNumber").asText());
        assertEquals("6RW586", outcome.get("authorizationCode").asText());
        assertEquals("10", result.get("transactionType").asText());
        assertEquals("urn:test-agency:transaction-id:1586338120514", result.get("transactionId").asText());
    }

    @Test
    @DisplayName("Missing transaction should return error when queried")
    public void testPaymentResponseMissing() throws JsonProcessingException {
        final String response = template.requestBodyAndHeader("direct:transaction-query", null,
                "id", "urn:test-agency:transaction-id:1586338120500", String.class);
        final JsonNode result = (new ObjectMapper()).readTree(response);

        assertEquals("Transaction not found", result.get("error").asText());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() throws Exception {
                final Processors processors = new Processors(new TransactionStore(),
                        () -> OffsetDateTime.parse("2020-01-01T12:30Z"), new AppConfig("/route.properties"));
                context.addRoutes(new PaymentRoute(processors));
            }
        };
    }

}