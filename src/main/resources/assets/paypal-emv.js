let access_token, isSandBox, environment, identity, payment_config, reader, order, payment;

/*
    MIURA:    manufacturer correlates to the [Chip Card Reader](https://www.paypal.com/us/webapps/mpp/credit-card-reader-how-to/chip-card-reader),
    INGENICO: manufacturer correlates to the [Chip and Tap Reader](https://www.paypal.com/us/webapps/mpp/credit-card-reader-how-to/chip-and-tap-reader),
    VERIFONE: manufacturer correlates to the Verifone P400 reader which is only available for use with the Web SDK at this time.
*/
const readers = {
    chipCardReader: "MIURA",
    chipAndTapReader: "INGENICO",
    verifoneP400: "VERIFONE"
}
const cardReader = "chipCardReader";
const cardReaderType = readers[cardReader];

const eventHandler = () => {
    payment_config = pphwebsdk.PaymentConfiguration.create();
    payment_config.subscribe
        .onConnectReaderSuccess(function (details) {
            //Details of card reader provided
        })
        .onConnectReaderFailure(function (err) {
            //Unable to connect to reader - handle error scenario
        })
        .onMerchantInitializeSuccess(function (merchant) {
            //Merchant initialized successfully
        })
        .onMerchantInitializeFailure(function (err) {
            //Unable to intialize merchant - handle error scenario
        })
        .onPaymentSuccess(function (txnRecord) {
            //Payment success
        })
        .onPaymentFailure(function (err) {
            //Payment failed - handle error scenario
        })
        .onRefundSuccess(function (txnRecord) {
            //Payment success
        })
        .onRefundFailure(function (err) {
            //Payment failed - handle error scenario
        });
}

const completeSetUp = () => {
    access_token = "";
    isSandBox = true;
    environment = isSandBox ? pphwebsdk.Types.Environment.SANDBOX : pphwebsdk.Types.Environment.LIVE;
    identity = pphwebsdk.Identity.create(access_token).refreshUrl(refresh_url).environment(environment);
    eventHandler();
    reader = payment_config.reader();
    reader.connectionType(pphwebsdk.Types.ReaderConnectionType.USB);
    reader.manufacturer(pphwebsdk.Types.Manufacturers[cardReaderType]);
}

const onSetupPayPalHereBtnClicked = () => {
    pphwebsdk.Setup.startUIFlow().then(function (completionStep) {
        // PPHWebInterface has been installed and, if clicked, a test of the connected card reader was completed.
        // setup is complete
    });
}

const createOrder = (purchase, tip) => { // tip = 1.00 purchase = [{ name: "Cake", price: 1.00, quantity: 3 }]
    order = pphwebsdk.Order.create();
    purchase.map(data => order.item(data.item).price(data.price).quantity(data.quantity));
    order.tip(tip);
    processPayment();
}

const processPayment = () => {
    // this will update the reader firmware. if not updated the payment cannot go through
    pphwebsdk.Reader.pendingUpdateInstall();
    payment = pphwebsdk.Payment.create(identity, payment_config);
    payment.for(order).as(pphwebsdk.Types.PaymentMethod.CARD).sale();
}

const refund = (txnId, ppInvoiceId, totalAmount) => {
    order = pphwebsdk.Order.create().on(txnId).with(ppInvoiceId).for(totalAmount);
    payment = pphwebsdk.Payment.create(identity, payment_config);
    payment.for(order).as(pphwebsdk.Types.PaymentMethod.CARD).refund();
}

// TODO: set the loading status to true
pphwebsdk.Setup.isSetupComplete().then(function() {
    // Continue to payment flow
}).catch(function(err) {
    // Run startUIFlow to setup SDK
    onSetupPayPalHereBtnClicked();
});
