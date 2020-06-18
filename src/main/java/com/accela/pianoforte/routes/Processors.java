package com.accela.pianoforte.routes;

import com.accela.pianoforte.common.HMacMD5;
import com.accela.pianoforte.common.UTCTicks;
import com.accela.pianoforte.main.AppConfig;
import com.accela.pianoforte.model.*;
import com.accela.pianoforte.services.TransactionStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.vavr.Tuple2;
import io.vavr.control.Option;
import io.vavr.control.Try;
import org.apache.camel.Exchange;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
        query.set("events", eventsSpec());
        exchange.getMessage().setBody(query.toString());
    }

    private static ObjectNode eventsSpec() {
        final ObjectNode result = factory.objectNode();

        final ObjectNode queries = factory.objectNode();
        queries.set("eventType", factory.textNode("$.event"));
        result.set("queries", queries);

        final ObjectNode states = factory.objectNode();
        states.set("ready", factory.textNode("begin"));
        states.set("success", factory.textNode("success"));
        states.set("failure", factory.textNode("failure"));
        states.set("cancel", factory.textNode("abort"));
        result.set("states", states);

        return result;
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


    protected void getCredentials(final Exchange exchange) {
        final String accessId = appConfig.getApiAccessKey();
        final String version = "2.0";
        final String method = "sale";
        final String txOrderNb = Long.toString(System.currentTimeMillis());
        final String amount = "10.00";
        final Long timestamp = UTCTicks.getUtcTime(OffsetDateTime.now());
        final String customerToken = "";
        final String paymentToken = "";
        final String hash = String.join("|", ImmutableList.<String>builder()
                .add(accessId, method, version, amount, timestamp.toString(), txOrderNb, customerToken, paymentToken)
                .build());
        System.out.println(hash);
        final String hashCode = HMacMD5.getHmacMD5(hash, appConfig.getApiSecureKey());
        exchange.getMessage().setBody(Credentials.builder()
                .apiAccessId(accessId)
                .locationId(appConfig.getApiLocationId())
                .method(method)
                .amount(amount)
                .versionNumber(version)
                .orderNumber(txOrderNb)
                .utcTime(timestamp.toString())
                .signature(hashCode)
                .build(), Credentials.class);
    }

    private static final Function<Object,String> urldecoder = encoded ->
            Try.of(() -> URLDecoder.decode(encoded.toString(), "UTF-8")).getOrElse(encoded.toString());
}