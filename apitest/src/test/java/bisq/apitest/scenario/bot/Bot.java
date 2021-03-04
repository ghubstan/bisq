package bisq.apitest.scenario.bot;

import bisq.core.locale.Country;

import protobuf.PaymentAccount;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.locale.CountryUtil.findCountryByCode;
import static bisq.core.payment.payload.PaymentMethod.CLEAR_X_CHANGE_ID;
import static bisq.core.payment.payload.PaymentMethod.getPaymentMethodById;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MINUTES;



import bisq.apitest.method.BitcoinCliHelper;
import bisq.apitest.scenario.bot.script.BashScriptGenerator;
import bisq.apitest.scenario.bot.script.BotScript;

@Slf4j
public
class Bot {

    static final String MAKE = "MAKE";
    static final String TAKE = "TAKE";

    protected final BotClient makerBotClient;
    protected final BitcoinCliHelper bitcoinCli;
    protected final BashScriptGenerator bashScriptGenerator;
    protected final String[] actions;
    protected final long protocolStepTimeLimitInMs;
    protected final boolean stayAlive;
    protected final boolean isUsingTestHarness;
    protected final PaymentAccount paymentAccount;

    public Bot(BotClient botClient,
               BotScript botScript,
               BitcoinCliHelper bitcoinCli,
               BashScriptGenerator bashScriptGenerator) {
        this.makerBotClient = botClient;
        this.bitcoinCli = bitcoinCli;
        this.bashScriptGenerator = bashScriptGenerator;
        this.actions = botScript.getActions();
        this.protocolStepTimeLimitInMs = MINUTES.toMillis(botScript.getProtocolStepTimeLimitInMinutes());
        this.stayAlive = botScript.isStayAlive();
        this.isUsingTestHarness = botScript.isUseTestHarness();
        if (isUsingTestHarness)
            this.paymentAccount = createBotPaymentAccount(botScript);
        else
            this.paymentAccount = this.makerBotClient.getPaymentAccount(botScript.getPaymentAccountIdForBot());
    }

    private PaymentAccount createBotPaymentAccount(BotScript botScript) {
        BotPaymentAccountGenerator accountGenerator = new BotPaymentAccountGenerator(makerBotClient);

        String paymentMethodId = botScript.getBotPaymentMethodId();
        if (paymentMethodId != null) {
            if (paymentMethodId.equals(CLEAR_X_CHANGE_ID)) {
                return accountGenerator.createZellePaymentAccount("Bob's Zelle Account",
                        "Bob");
            } else {
                throw new UnsupportedOperationException(
                        format("This bot test does not work with %s payment accounts yet.",
                                getPaymentMethodById(paymentMethodId).getDisplayString()));
            }
        } else {
            String countryCode = botScript.getCountryCode();
            Country country = findCountryByCode(countryCode).orElseThrow(() ->
                    new IllegalArgumentException(countryCode + " is not a valid iso country code."));
            return accountGenerator.createF2FPaymentAccount(country, country.name + " F2F Account");
        }
    }
}
