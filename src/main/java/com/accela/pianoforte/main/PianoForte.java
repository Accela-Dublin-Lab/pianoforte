package com.accela.pianoforte.main;

import com.accela.pianoforte.routes.ApiRoute;
import com.accela.pianoforte.routes.PaymentRoute;
import com.accela.pianoforte.routes.Processors;
import com.accela.pianoforte.services.TransactionStore;
import config.AppConfig;
import org.apache.camel.main.Main;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public class PianoForte {
    public static void main(final String[] args) {
        final Main routes = new Main();
        final AppConfig appConfig = new AppConfig("/route.properties");
        final Processors processors = new Processors(new TransactionStore(),
                () -> OffsetDateTime.now(ZoneOffset.UTC), appConfig);
        routes.addRoutesBuilder(new PaymentRoute(processors));
        routes.addRoutesBuilder(new ApiRoute(appConfig));
        routes.start();
    }

}
