package com.accela.pianoforte.routes;

import com.github.mustachejava.Mustache;
import config.AppConfig;
import com.accela.pianoforte.model.*;
import com.accela.pianoforte.services.TransactionStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import io.vavr.Tuple2;
import io.vavr.control.Option;
import io.vavr.control.Try;
import org.apache.camel.Exchange;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.fasterxml.jackson.databind.node.JsonNodeFactory.instance;
import static io.vavr.API.For;
import static java.lang.Integer.parseInt;

public class Processors {
    private static final JsonNodeFactory factory = instance;
    private final RequestFormBuilder formBuilder;
    private final AppConfig appConfig;
    private final TransactionStore store;

    public Processors(final TransactionStore store,
                      final Supplier<OffsetDateTime> timestamper, final AppConfig appConfig) {
        this.store = store;
        this.appConfig = appConfig;
        formBuilder = new RequestFormBuilder(timestamper, appConfig);
    }

    protected void toEMVQuery(final Exchange exchange) {
        exchange.getMessage().setHeader("accountId", appConfig.getPayPalAccountId());
        exchange.getMessage().setHeader("clientId", appConfig.getPayPalClientId());
    }

    protected void toRedirectQuery(final Exchange exchange) {
        final Request request = exchange.getIn().getBody(Request.class);
        final List<JsonNode> fields = formBuilder.build(request).entrySet().stream()
                .map(entry -> (ObjectNode) factory.objectNode()
                        .set(entry.getKey(), factory.textNode(entry.getValue())))
                .collect(Collectors.toList());
        final ObjectNode query = factory.objectNode();
        query.set("data", factory.arrayNode().addAll(fields));
        query.set("url", factory.textNode(appConfig.getCheckoutUrl()));
        query.set("method", factory.textNode("POST"));
        query.set("contentType", factory.textNode("application/x-www-form-urlencoded"));
        exchange.getMessage().setBody(query.toString());
    }

    protected void parseResponse(final Exchange exchange) {
        final Map<String, String> fields = exchange.getIn()
                .getHeaders().entrySet().stream()
                    .filter(e -> e.getKey().startsWith("pg_"))
                    .collect(Collectors.toMap(Map.Entry::getKey,
                            entry -> urldecoder.apply(entry.getValue())));

        exchange.getMessage().setBody(Response.builder()
                .amount(new BigDecimal(fields.get("pg_total_amount").replaceAll(",", "")))
                .transactionType(fields.get("pg_transaction_type"))
                .transactionId(
                        Try.of(() -> new URI(fields.get("pg_transaction_order_number"))).getOrNull())
                .personalName(new PersonalName(
                        fields.get("pg_billto_postal_name_first"),
                        fields.get("pg_billto_postal_name_last")))
                .contact(new Contact(
                        fields.get("pg_billto_postal_name_company"),
                        fields.get("pg_billto_postal_street_line1"),
                        fields.get("pg_billto_postal_street_line2"),
                        fields.get("pg_billto_postal_city"),
                        fields.get("pg_billto_postal_stateprov"),
                        fields.get("pg_billto_postal_postalcode"),
                        fields.get("pg_billto_telecom_phone_number"),
                        fields.get("pg_billto_online_email")))
                .paymentOutcome(PaymentOutcome.builder()
                        .responseText(fields.get("pg_response_description"))
                        .responseCode(fields.get("pg_response_code"))
                        .description(
                                appConfig.getResponseDescription(fields.get("pg_response_code")))
                        .responseType(fields.get("pg_response_type"))
                        .authorizationCode(fields.get("pg_authorization_code"))
                        .traceNumber(fields.get("pg_trace_number")).build())
                .instrument(buildInstrument(fields)).build(), Response.class);
    }

    private static Instrument buildInstrument(final Map<String, String> fields) {
        final Instrument.InstrumentBuilder builder = Instrument.builder()
                .number(parseInt(fields.get("pg_last4")))
                .type("EC");
        final Option<Instrument> instrument = For(
                Option.of(fields.get("pg_payment_card_expdate_year")),
                Option.of(fields.get("pg_payment_card_expdate_month")),
                Option.of(fields.get("pg_payment_card_type"))
        ).yield((year, month, issuer) ->
                builder.expiryDate(YearMonth.of(parseInt(year), parseInt(month)))
                        .issuer(issuer)
                        .type("CC")
                        .build()
        );
        return instrument.getOrElse(builder.build());
    }

    protected void storeResponse(final Exchange exchange) {
        store.add(Option.of(exchange.getIn().getBody(Response.class)));
    }

    private static final Map<String, String> notFound =
            ImmutableMap.<String, String>builder()
                    .put("error", "Transaction not found").build();

    protected void lookupResponse(final Exchange exchange) {
        final Tuple2<Object,Integer> result = store.get(exchange.getIn().getHeader("id", URI.class))
                .map(response -> new Tuple2<Object,Integer>(response, 200))
                .getOrElse(() -> new Tuple2<>(notFound, 404));
        exchange.getMessage().setBody(result._1);
        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, result._2);
    }

    private static final Function<Object,String> urldecoder = encoded ->
            Try.of(() -> URLDecoder.decode(encoded.toString(), "UTF-8")).getOrElse(encoded.toString());
}