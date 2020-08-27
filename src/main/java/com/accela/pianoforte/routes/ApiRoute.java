package com.accela.pianoforte.routes;

import com.accela.pianoforte.main.AppConfig;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
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
                .post("/payments")
                    .to("direct:payments")
                .post("/authenticate")
                    .to("direct:authenticate")
                .get("/configuration/{adapter-id}")
                    .to("direct:configuration")
                .put("/organizations/{org-id}/locations/{loc-id}/transactions/{trans-id}")
                    .to("direct:void")
                .post("/response")
                    .to("direct:response")
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

        from("direct:authenticate")
            .convertBodyTo(String.class)
            .log(LoggingLevel.INFO, "com.accela", "authenticate", "${body}")
            .setBody(constant("{\"status\":200,\"result\":\"234523452345234534523452345234523452345\"}"))
            .log(LoggingLevel.INFO, "com.accela", "authenticate", "${body}");
        
        from("direct:configuration")
            .convertBodyTo(String.class)
            .log(LoggingLevel.INFO, "com.accela", "configuration", "${headers.adapter-id}")
            .process(exch -> {
                exch.getMessage().setBody(loadResource("agencyConfig.json"));
            })
            .log(LoggingLevel.INFO, "com.accela", "configuration", "${body}");

        from("direct:response")
            .convertBodyTo(String.class)
            .log(LoggingLevel.INFO, "com.accela", "response", "${body}")
            .log(LoggingLevel.INFO, "com.accela", "auth-header", "Authorization=${headers.Authorization}")
            .process(exch -> exch.getIn().getHeaders().forEach((k,v) ->
                System.out.println(">>> "+k+" => "+v)))
            .setBody(constant("{\"status\":200,\"message\":\"OK\"}"))
            .log(LoggingLevel.INFO, "com.accela", "configuration", "${body}");

        from("direct:payments")
            .convertBodyTo(String.class)
            .log(LoggingLevel.INFO, "com.accela", "${body}");

        from("direct:void")
            .log(LoggingLevel.INFO, "com.accela", "void", "OrgId: ${headers.org-id}")
            .log(LoggingLevel.INFO, "com.accela", "void", "LocId: ${headers.loc-id}")
            .log(LoggingLevel.INFO, "com.accela", "void", "TrnId: ${headers.trans-id}")
            .convertBodyTo(String.class)
            .log(LoggingLevel.INFO, "com.accela", "void-transaction", "${body}")
            .setBody(constant("{" +
                "  \"transaction_id\": \"trn_aa785423-9f87-4177-b88c-0966ce7e7d93\"," +
                "  \"location_id\": \"loc_251336\"," +
                "  \"action\": \"void\"," +
                "  \"authorization_code\": \"2TI295\"," +
                "  \"entered_by\": \"Martin Heidegger\"," +
                "  \"response\": {" +
                "    \"environment\": \"sandbox\"," +
                "    \"response_type\": \"A\"," +
                "    \"response_code\": \"A01\"," +
                "    \"response_desc\": \"APPROVED\"," +
                "    \"authorization_code\": \"40832334\"" +
                "  }," +
                "  \"links\": {" +
                "    \"disputes\": \"https://sandbox.forte.net/API/v3/transactions/trn_5e722288-3bd0-4421-821d-b660db86a5e5/disputes\"," +
                "    \"settlements\": \"https://sandbox.forte.net/API/v3/transactions/trn_5e722288-3bd0-4421-821d-b660db86a5e5/settlements\"," +
                "    \"self\": \"https://sandbox.forte.net/API/v3/transactions/trn_5e722288-3bd0-4421-821d-b660db86a5e5/\"" +
                "  }" +
                "}"));

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

    private static byte[] loadResource(final String path) throws URISyntaxException, IOException {
        return Files.readAllBytes(Paths.get(ApiRoute.class.getClassLoader().getResource(path).toURI()));
    }

}
