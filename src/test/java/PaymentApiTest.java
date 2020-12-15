import com.accela.pianoforte.routes.ApiRoute;
import config.AppConfig;
import io.vavr.control.Try;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;

import static fixtures.Fixtures.paymentResponseUrlencoded;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PaymentApiTest extends CamelTestSupport {
    private static final AppConfig testConfig = getConfiguration();
    private static final String endpoint = String.format(
            "http://localhost:%s/pianoforte/api/payment/checkout", testConfig.getRestLocalPort());
    private static final String jsonRequest =
            "{\"personalName\": {\"firstName\":\"Karl\",\"lastName\":\"Marx\"},\"agency\":\"test-agency\"," +
                    "\"transactionId\":\"1586197589861\"," +
                    "\"transactionType\":\"EC\"," +
                    "\"continueUrl\":\"http://localhost:9090/pianoforte/checkout\"," +
                    "\"amount\":123.34}";

    @Test
    @DisplayName("API call succeeds")
    public void testPaymentRequest() {
        final String result = template.requestBodyAndHeader(endpoint,
                jsonRequest, Exchange.CONTENT_TYPE, "application/json", String.class);
        assertEquals(checkoutQuery,result);
    }

    @Test
    @DisplayName("Checkout page served")
    public void testCheckoutPage() {
        final String pageAddress = String.format(
                "http://localhost:%s/pianoforte/checkout", testConfig.getRestLocalPort());
        final String result = template.requestBody(pageAddress, null, String.class);
        assertTrue(result.contains("Checkout Page"));
    }

    @Test
    @DisplayName("Response page served")
    public void testResponsePage() {
        final String pageAddress = String.format(
                "http://localhost:%s/pianoforte/api/payment/return", testConfig.getRestLocalPort());
        final String result = template.requestBody(
                pageAddress, paymentResponseUrlencoded, String.class);
        assertEquals("{}", result);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() throws Exception {
                context.addRoutes(new ApiRoute(testConfig));
                from("direct:payment-checkout")
                        .setBody().simple(checkoutQuery);
                from("direct:payment-response")
                        .setBody().simple("{}");
            }
        };
    }

    private static AppConfig getConfiguration() {
        return Try.of(() -> {
            final AppConfig result = new AppConfig("/route.properties");
            final Integer localPort = Try.of(() -> (new ServerSocket(0)).getLocalPort()).get();
            result.update(AppConfig.REST_LOCAL_PORT, Integer.toString(localPort));
            return result;
        }).get();
    }

    private static final String checkoutQuery =
            "{\"data\":[" +
                "{\"pg_billto_postal_name_first\":\"Karl\"}," +
                "{\"pg_billto_postal_name_last\":\"Marx\"}," +
                "{\"pg_api_login_id\":\"9F3FA809B8\"}," +
                "{\"pg_transaction_type\":\"20\"}," +
                "{\"pg_version_number\":\"1.0\"},{\"pg_total_amount\":\"123.34\"}," +
                "{\"pg_utc_time\":\"637219276208950000\"}," +
                "{\"pg_transaction_order_number\":\"urn:test-agency:transaction-id:1586197589861\"}," +
                "{\"pg_ts_hash\":\"BB1AF00676B1FF5585F8192D4A942672\"}," +
                "{\"pg_return_url\":\"http://localhost:9090/pianoforte/api/payment/return\"}," +
                "{\"pg_continue_url\":\"http://localhost:9090/pianoforte/checkout\"}," +
                "{\"pg_cancel_url\":\"http://localhost:9090/pianoforte/api/payment/cancel\"}]," +
            "\"url\":\"https://sandbox.paymentsgateway.net/swp/co/default.aspx\"," +
            "\"method\":\"POST\"," +
            "\"contentType\":\"application/x-www-form-urlencoded\"}";

}