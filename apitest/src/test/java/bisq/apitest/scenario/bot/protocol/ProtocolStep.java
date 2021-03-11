package bisq.apitest.scenario.bot.protocol;

// TODO create final fields 'shortName', 'waitForActionMessage', 'doActionMessage', 'interruptMessage'
public enum ProtocolStep {
    START,
    FIND_OFFER,
    TAKE_OFFER,
    WAIT_FOR_OFFER_TAKER,
    WAIT_FOR_TAKER_DEPOSIT_TX_PUBLISHED,
    WAIT_FOR_TAKER_DEPOSIT_TX_CONFIRMED,
    SEND_PAYMENT_STARTED_MESSAGE,
    WAIT_FOR_PAYMENT_STARTED_MESSAGE,
    SEND_PAYMENT_RECEIVED_CONFIRMATION_MESSAGE,
    WAIT_FOR_PAYMENT_RECEIVED_CONFIRMATION_MESSAGE,
    WAIT_FOR_PAYOUT_TX,
    KEEP_FUNDS,
    DONE
}
