package com.accela.pianoforte.routes;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.http.HttpHeaders.CONTENT_TYPE;
import static org.apache.http.entity.ContentType.APPLICATION_JSON;

public class PaymentRoute extends RouteBuilder {
    private static final Logger logger = LoggerFactory.getLogger(PaymentRoute.class);
    private final Processors processors;

    public PaymentRoute(final Processors processors) {
        this.processors = processors;
    }

    @Override
    public void configure() {
        getContext().getGlobalOptions().put("CamelJacksonEnableTypeConverter", "true");

        from("direct:payment-checkout").routeId("checkout")
                .process(processors::toRedirectQuery)
                .process(exch ->
                        logger.info("respond to to client => "+exch.getIn().getBody(String.class)))
                .setHeader(CONTENT_TYPE, constant(APPLICATION_JSON));

        from("direct:payment-response")
                .to("log:com.accela?level=INFO&showAll=true")
                .process(processors::parseResponse)
                .process(processors::storeResponse)
                .marshal().json(JsonLibrary.Jackson)
                .setHeader(CONTENT_TYPE, constant(APPLICATION_JSON))
                .to("direct:platform");

        from("direct:platform")
                .process(exch ->
                    logger.info("POST to platform => "+exch.getIn().getBody(String.class)));

        from("direct:transaction-query")
                .process(processors::lookupResponse)
                .marshal().json(JsonLibrary.Jackson)
                .setHeader(CONTENT_TYPE, constant(APPLICATION_JSON));


        from("direct:get-credentials")
                .process(processors::getCredentials)
                .marshal().json(JsonLibrary.Jackson)
                .setHeader(CONTENT_TYPE, constant(APPLICATION_JSON));
    }

}