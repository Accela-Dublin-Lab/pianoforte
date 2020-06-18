package com.accela.pianoforte.routes;

import com.accela.pianoforte.main.AppConfig;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;

import static org.apache.camel.Exchange.CONTENT_TYPE;
import static org.apache.http.entity.ContentType.TEXT_HTML;

public class ApiRoute extends RouteBuilder {
    private final AppConfig appConfig;

    public ApiRoute(final AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    @Override
    public void configure() {
        restConfiguration()
                .component("jetty")
                .contextPath(appConfig.getRestContextPath())
                .port(appConfig.getRestLocalPort());

        rest("/").id("home")
                .get("/checkout")
                    .to("direct:checkout-page")
                .get("/credentials")
                    .to("direct:get-credentials")
                .get("/checkout2")
                    .to("direct:checkout2-page")
                .get("/checkout/complete/{id}")
                    .to("direct:completed-page")
                .get("/checkout/failure")
                    .to("direct:failure-page")
                .get("/scripts/{name}")
                    .to("direct:scripts")
                .get("/image/{name}")
                    .to("direct:images")
                .get("/css/{name}")
                    .to("direct:styles");

        rest(appConfig.getRestBase()).id("api-route")
                .post(appConfig.getRestCheckoutPayment())
                    .to("direct:payment-checkout")
                .post(appConfig.getRestReturnUrl())
                    .to("direct:payment-response")
                .get("/transaction/{id}")
                    .to("direct:transaction-query");

        from("direct:checkout-page")
                .setProperty("asset", constant("pages/paymentPage.html"))
                .setHeader(CONTENT_TYPE, constant(TEXT_HTML.toString()))
                .process(ApiRoute::streamAsset);

        from("direct:checkout2-page")
                .setProperty("asset", constant("pages/checkoutv2.html"))
                .setHeader(CONTENT_TYPE, constant(TEXT_HTML.toString()))
                .process(ApiRoute::streamAsset);

        from("direct:completed-page")
                .setProperty("asset", simple("pages/completedPage.html"))
                .setHeader(CONTENT_TYPE, constant(TEXT_HTML.toString()))
                .process(ApiRoute::streamAsset);

        from("direct:failure-page")
                .setProperty("asset", constant("pages/failedPage.html"))
                .setHeader(CONTENT_TYPE, constant(TEXT_HTML.toString()))
                .process(ApiRoute::streamAsset);

        from("direct:scripts")
            .setProperty("asset", simple("scripts/${header.name}"))
            .setHeader(CONTENT_TYPE, constant("text/javascript"))
            .process(ApiRoute::streamAsset);

        from("direct:images")
                .setProperty("asset", simple("assets/${header.name}"))
                .setHeader(CONTENT_TYPE, constant("image/png"))
                .process(ApiRoute::streamAsset);

        from("direct:styles")
                .setProperty("asset", simple("assets/${header.name}"))
                .setHeader(CONTENT_TYPE, constant("text/css"))
                .process(ApiRoute::streamAsset);
    }

    private static void streamAsset(final Exchange exchange) {
        final String page404 = "<div id=\"main\"><div class=\"fof\"><h1>Error 404</h1></div></div>";
        final InputStream assetStream = Optional.ofNullable(exchange.getProperty("asset", String.class))
                .map(asset -> ApiRoute.class.getClassLoader().getResourceAsStream(asset))
                .orElse(new ByteArrayInputStream(page404.getBytes()));
        exchange.getMessage().setBody(assetStream, InputStream.class);
    }

}
