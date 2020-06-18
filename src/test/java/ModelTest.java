import com.accela.pianoforte.model.*;
import com.google.common.collect.ImmutableMap;
import io.vavr.control.Option;
import io.vavr.control.Try;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.time.Month;
import java.time.YearMonth;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static fixtures.Fixtures.paymentResponse;
import static io.vavr.API.For;
import static java.lang.Integer.parseInt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ModelTest {

    @Test
    @DisplayName("response is transformed correctly")
    public void testBuildResponse() throws URISyntaxException {
        final Map<String, String> headers = paymentResponse.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> urldecoder.apply(entry.getValue())));
        final Instrument.InstrumentBuilder ccBuilder = Instrument.builder()
                .type("CC")
                .number(parseInt(headers.get("pg_last4")))
                .expiryDate(YearMonth.of(
                        parseInt(headers.get("pg_payment_card_expdate_year")),
                        parseInt(headers.get("pg_payment_card_expdate_month"))))
                .issuer(headers.get("pg_payment_card_type"));
        final PaymentOutcome.PaymentOutcomeBuilder outcomeBuilder = PaymentOutcome.builder()
                .responseText(headers.get("pg_response_description"))
                .responseCode(headers.get("pg_response_code"))
                .responseType(headers.get("pg_response_type"))
                .authorizationCode(headers.get("pg_authorization_code"))
                .traceNumber(headers.get("pg_trace_number"));
        final Response.ResponseBuilder builder = Response.builder()
                .amount(new BigDecimal(headers.get("pg_total_amount").replaceAll(",", "")))
                .paymentOutcome(outcomeBuilder.build())
                .transactionType(headers.get("pg_transaction_type"))
                .transactionId(new URI(headers.get("pg_transaction_order_number")))
                .personalName(new PersonalName(
                        headers.get("pg_billto_postal_name_first"),
                        headers.get("pg_billto_postal_name_last")))
                .contact(new Contact(
                        headers.get("pg_billto_postal_name_company"),
                        headers.get("pg_billto_postal_street_line1"),
                        headers.get("pg_billto_postal_street_line2"),
                        headers.get("pg_billto_postal_city"),
                        headers.get("pg_billto_postal_stateprov"),
                        headers.get("pg_billto_postal_postalcode"),
                        headers.get("pg_billto_telecom_phone_number"),
                        headers.get("pg_billto_online_email")))
                .instrument(ccBuilder.build());
        assertTrue(ccBuilder.toString().contains("expiryDate=2020-04"));
        assertTrue(outcomeBuilder.toString().contains("authorizationCode=6RW586"));
        assertTrue(builder.toString().contains("amount=1200.00"));
    }

    @Test
    public void testInstrumentBuilder() {
        final ImmutableMap<String, String> data = ImmutableMap.<String, String>builder()
                .put("pg_last4", "1234")
                .put("pg_payment_card_expdate_month", "12")
                .put("pg_payment_card_expdate_year", "2020")
                .put("pg_payment_card_type", "visa").build();

        final Instrument.InstrumentBuilder builder = Instrument.builder()
                .number(parseInt(data.get("pg_last4")))
                .type("EC");
        final Option<Instrument> instrument = For(
                Option.of(data.get("pg_payment_card_expdate_year")),
                Option.of(data.get("pg_payment_card_expdate_month")),
                Option.of(data.get("pg_payment_card_type"))
        ).yield((year, month, issuer) ->
                builder.expiryDate(YearMonth.of(parseInt(year), parseInt(month)))
                        .issuer(issuer)
                        .type("CC")
                        .build()
        );
        final Instrument result = instrument.getOrElse(builder.build());
        assertEquals("CC", result.getType());
        assertEquals("************1234", result.getNumber());
        assertEquals("visa", result.getIssuer());
        assertEquals(Month.DECEMBER, result.getExpiryDate().getMonth());
        assertEquals(2020, result.getExpiryDate().getYear());
    }

    private static final Function<Object,String> urldecoder = encoded ->
            Try.of(() -> URLDecoder.decode(encoded.toString(), "UTF-8")).getOrElse(encoded.toString());
}
