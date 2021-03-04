/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.apitest.scenario.bot.protocol;


import bisq.proto.grpc.TradeInfo;

import protobuf.PaymentAccount;

import java.security.SecureRandom;

import java.text.DecimalFormat;

import java.io.File;

import java.math.RoundingMode;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import static bisq.apitest.scenario.bot.protocol.ProtocolStep.*;
import static bisq.apitest.scenario.bot.shutdown.ManualShutdown.checkIfShutdownCalled;
import static java.lang.String.format;
import static java.lang.System.currentTimeMillis;
import static java.util.Arrays.stream;
import static java.util.concurrent.TimeUnit.MILLISECONDS;



import bisq.apitest.method.BitcoinCliHelper;
import bisq.apitest.scenario.bot.BotClient;
import bisq.apitest.scenario.bot.script.BashScriptGenerator;
import bisq.cli.TradeFormat;

@Slf4j
public abstract class BotProtocol {

    static final SecureRandom RANDOM = new SecureRandom();
    static final String BUY = "BUY";
    static final String SELL = "SELL";

    protected final Supplier<Long> randomDelay = () -> (long) (2000 + RANDOM.nextInt(5000));

    protected final AtomicLong protocolStepStartTime = new AtomicLong(0);
    protected final Consumer<ProtocolStep> initProtocolStep = (step) -> {
        currentProtocolStep = step;
        printBotProtocolStep();
        protocolStepStartTime.set(currentTimeMillis());
    };

    @Getter
    protected ProtocolStep currentProtocolStep;

    @Getter
    protected final String botDescription;
    @Getter // Functions within 'this' need the @Getter.
    protected final BotClient botClient;
    protected final PaymentAccount paymentAccount;
    protected final String currencyCode;
    protected final long protocolStepTimeLimitInMs;
    protected final BitcoinCliHelper bitcoinCli;
    @Getter
    protected final BashScriptGenerator bashScriptGenerator;


    public BotProtocol(String botDescription,
                       BotClient botClient,
                       PaymentAccount paymentAccount,
                       long protocolStepTimeLimitInMs,
                       BitcoinCliHelper bitcoinCli,
                       BashScriptGenerator bashScriptGenerator) {
        this.botDescription = botDescription;
        this.botClient = botClient;
        this.paymentAccount = paymentAccount;
        this.currencyCode = Objects.requireNonNull(paymentAccount.getSelectedTradeCurrency()).getCode();
        this.protocolStepTimeLimitInMs = protocolStepTimeLimitInMs;
        this.bitcoinCli = bitcoinCli;
        this.bashScriptGenerator = bashScriptGenerator;
        this.currentProtocolStep = START;
    }

    public abstract void run();

    protected boolean isWithinProtocolStepTimeLimit() {
        return (currentTimeMillis() - protocolStepStartTime.get()) < protocolStepTimeLimitInMs;
    }

    protected void checkIsStartStep() {
        if (currentProtocolStep != START) {
            throw new IllegalStateException("First bot protocol step must be " + START.name());
        }
    }

    protected void printBotProtocolStep() {
        log.info("{} is starting protocol step {}.  Time limit is {} minutes.",
                botDescription,
                currentProtocolStep.name(),
                MILLISECONDS.toMinutes(protocolStepTimeLimitInMs));

        if (currentProtocolStep.equals(WAIT_FOR_TAKER_DEPOSIT_TX_CONFIRMED)) {
            log.info("Generate a btc block to trigger taker's deposit fee tx confirmation.");
            createGenerateBtcBlockScript();
        }
    }

    protected final Function<TradeInfo, TradeInfo> waitForTakerFeeTxConfirm = (trade) -> {
        sleep(5000);
        waitForTakerFeeTxPublished(trade.getTradeId());
        waitForTakerFeeTxConfirmed(trade.getTradeId());
        return trade;
    };

    protected final Function<TradeInfo, TradeInfo> waitForPaymentStartedMessage = (trade) -> {
        initProtocolStep.accept(WAIT_FOR_PAYMENT_STARTED_MESSAGE);
        createPaymentStartedScript(trade);
        log.info("{} is waiting for a 'payment started' message from buyer for trade with id {}.",
                this.getBotDescription(),
                trade.getTradeId());
        while (isWithinProtocolStepTimeLimit()) {
            checkIfShutdownCalled("Interrupted before checking if 'payment started' message has been sent.");
            int numDelays = 0;
            try {
                var t = this.getBotClient().getTrade(trade.getTradeId());
                if (t.getIsFiatSent()) {
                    log.info("Buyer has started payment for trade:\n{}", TradeFormat.format(t));
                    return t;
                }
            } catch (Exception ex) {
                throw new IllegalStateException(this.getBotClient().toCleanGrpcExceptionMessage(ex));
            }
            if (++numDelays % 5 == 0) {
                log.warn("{} is still waiting for 'payment started' message for trade {}",
                        this.getBotDescription(),
                        trade.getShortId());
            }
            sleep(randomDelay.get());
        } // end while

        // If the while loop is exhausted, a payment started msg was not detected.
        throw new IllegalStateException("Payment started msg never sent; we won't wait any longer.");
    };

    protected final Function<TradeInfo, TradeInfo> sendPaymentStartedMessage = (trade) -> {
        log.info("{} is sending 'payment started' msg for trade with id {}.",
                this.getBotDescription(),
                trade.getTradeId());
        initProtocolStep.accept(SEND_PAYMENT_STARTED_MESSAGE);
        checkIfShutdownCalled("Interrupted before sending 'payment started' message.");
        this.getBotClient().sendConfirmPaymentStartedMessage(trade.getTradeId());
        return trade;
    };

    protected final Function<TradeInfo, TradeInfo> waitForPaymentReceivedConfirmation = (trade) -> {
        initProtocolStep.accept(WAIT_FOR_PAYMENT_RECEIVED_CONFIRMATION_MESSAGE);
        createPaymentReceivedScript(trade);
        log.info("{} is waiting for a 'payment received confirmation' message from seller for trade with id {}.",
                this.getBotDescription(),
                trade.getTradeId());
        int numDelays = 0;
        while (isWithinProtocolStepTimeLimit()) {
            checkIfShutdownCalled("Interrupted before checking if 'payment received confirmation' message has been sent.");
            try {
                var t = this.getBotClient().getTrade(trade.getTradeId());
                if (t.getIsFiatReceived()) {
                    log.info("Seller has received payment for trade:\n{}", TradeFormat.format(t));
                    return t;
                }
            } catch (Exception ex) {
                throw new IllegalStateException(this.getBotClient().toCleanGrpcExceptionMessage(ex));
            }
            if (++numDelays % 5 == 0) {
                log.warn("{} is still waiting for 'payment received confirmation' message for trade {}",
                        this.getBotDescription(),
                        trade.getShortId());
            }
            sleep(randomDelay.get());
        } // end while

        // If the while loop is exhausted, a payment rcvd confirmation msg was not detected within the protocol step time limit.
        throw new IllegalStateException("Payment was never received; we won't wait any longer.");
    };

    protected final Function<TradeInfo, TradeInfo> sendPaymentReceivedMessage = (trade) -> {
        log.info("{} is sending 'payment received confirmation' msg for trade with id {}.",
                this.getBotDescription(),
                trade.getTradeId());
        initProtocolStep.accept(SEND_PAYMENT_RECEIVED_CONFIRMATION_MESSAGE);
        checkIfShutdownCalled("Interrupted before sending 'payment received confirmation' message.");
        this.getBotClient().sendConfirmPaymentReceivedMessage(trade.getTradeId());
        return trade;
    };

    protected final Function<TradeInfo, TradeInfo> waitForPayoutTx = (trade) -> {
        initProtocolStep.accept(WAIT_FOR_PAYOUT_TX);
        log.info("{} is waiting on the 'payout tx published' confirmation for trade with id {}.",
                this.getBotDescription(),
                trade.getTradeId());
        while (isWithinProtocolStepTimeLimit()) {
            checkIfShutdownCalled("Interrupted before checking if payout tx has been published.");
            int numDelays = 0;
            try {
                var t = this.getBotClient().getTrade(trade.getTradeId());
                if (t.getIsPayoutPublished()) {
                    log.info("Payout tx {} has been published for trade:\n{}",
                            t.getPayoutTxId(),
                            TradeFormat.format(t));
                    return t;
                }
            } catch (Exception ex) {
                throw new IllegalStateException(this.getBotClient().toCleanGrpcExceptionMessage(ex));
            }
            if (++numDelays % 5 == 0) {
                log.warn("{} is still waiting for payout tx for trade {}",
                        this.getBotDescription(),
                        trade.getShortId());
            }
            sleep(randomDelay.get());
        } // end while

        // If the while loop is exhausted, a payout tx was not detected within the protocol step time limit.
        throw new IllegalStateException("Payout tx was never published; we won't wait any longer.");
    };

    protected final Function<TradeInfo, TradeInfo> keepFundsFromTrade = (trade) -> {
        initProtocolStep.accept(KEEP_FUNDS);
        var isBuy = trade.getOffer().getDirection().equalsIgnoreCase(BUY);
        var isSell = trade.getOffer().getDirection().equalsIgnoreCase(SELL);
        var cliUserIsSeller = (this instanceof MarketMakerBotProtocol && isBuy)
                || (this instanceof MakerBotProtocol && isBuy)
                || (this instanceof TakerBotProtocol && isSell);
        if (cliUserIsSeller) {
            createKeepFundsScript(trade);
        } else {
            createGetBalanceScript();
        }
        checkIfShutdownCalled("Interrupted before closing trade with 'keep funds' command.");
        this.getBotClient().sendKeepFundsMessage(trade.getTradeId());
        return trade;
    };

    protected void createPaymentStartedScript(TradeInfo trade) {
        String scriptFilename = "confirmpaymentstarted-" + trade.getShortId() + ".sh";
        File script = bashScriptGenerator.createPaymentStartedScript(trade, scriptFilename);
        printCliHintAndOrScript(script, "The manual CLI side can send a 'payment started' message");
    }

    protected void createPaymentReceivedScript(TradeInfo trade) {
        String scriptFilename = "confirmpaymentreceived-" + trade.getShortId() + ".sh";
        File script = bashScriptGenerator.createPaymentReceivedScript(trade, scriptFilename);
        printCliHintAndOrScript(script, "The manual CLI side can sent a 'payment received confirmation' message");
    }

    protected void createKeepFundsScript(TradeInfo trade) {
        String scriptFilename = "keepfunds-" + trade.getShortId() + ".sh";
        File script = bashScriptGenerator.createKeepFundsScript(trade, scriptFilename);
        printCliHintAndOrScript(script, "The manual CLI side can close the trade");
    }

    protected void createGetBalanceScript() {
        File script = bashScriptGenerator.createGetBalanceScript();
        printCliHintAndOrScript(script, "The manual CLI side can view current balances");
    }

    protected void createGenerateBtcBlockScript() {
        String newBitcoinCoreAddress = bitcoinCli.getNewBtcAddress();
        File script = bashScriptGenerator.createGenerateBtcBlockScript(newBitcoinCoreAddress);
        printCliHintAndOrScript(script, "The manual CLI side can generate 1 btc block");
    }

    protected void printCliHintAndOrScript(File script, String hint) {
        log.info("{} by running bash script '{}'.", hint, script.getAbsolutePath());
        if (this.getBashScriptGenerator().isPrintCliScripts())
            this.getBashScriptGenerator().printCliScript(script, log);

        sleep(5000); // Allow 5s for CLI user to read the hint.
    }

    protected void sleep(long ms) {
        try {
            MILLISECONDS.sleep(ms);
        } catch (InterruptedException ignored) {
            // empty
        }
    }

    private void waitForTakerFeeTxPublished(String tradeId) {
        waitForTakerDepositFee(tradeId, WAIT_FOR_TAKER_DEPOSIT_TX_PUBLISHED);
    }

    private void waitForTakerFeeTxConfirmed(String tradeId) {
        waitForTakerDepositFee(tradeId, WAIT_FOR_TAKER_DEPOSIT_TX_CONFIRMED);
    }

    private void waitForTakerDepositFee(String tradeId, ProtocolStep depositTxProtocolStep) {
        initProtocolStep.accept(depositTxProtocolStep);
        validateCurrentProtocolStep(WAIT_FOR_TAKER_DEPOSIT_TX_PUBLISHED, WAIT_FOR_TAKER_DEPOSIT_TX_CONFIRMED);
        log.info(waitingForDepositFeeTxMsg(tradeId));
        while (isWithinProtocolStepTimeLimit()) {
            String warning = format("Interrupted before checking taker deposit fee tx is %s for trade %s.",
                    depositTxProtocolStep.equals(WAIT_FOR_TAKER_DEPOSIT_TX_PUBLISHED) ? "published" : "confirmed",
                    tradeId);
            checkIfShutdownCalled(warning);
            try {
                var trade = this.getBotClient().getTrade(tradeId);
                if (isDepositFeeTxStepComplete.test(trade))
                    return;
                else
                    sleep(randomDelay.get());
            } catch (Exception ex) {
                if (this.getBotClient().tradeContractIsNotReady.test(ex, tradeId))
                    sleep(randomDelay.get());
                else
                    throw new IllegalStateException(this.getBotClient().toCleanGrpcExceptionMessage(ex));
            }
        }  // end while

        // If the while loop is exhausted, a deposit fee tx was not published or confirmed within the protocol step time limit.
        throw new IllegalStateException(stoppedWaitingForDepositFeeTxMsg(tradeId));
    }

    private final Predicate<TradeInfo> isDepositFeeTxStepComplete = (trade) -> {
        if (currentProtocolStep.equals(WAIT_FOR_TAKER_DEPOSIT_TX_PUBLISHED) && trade.getIsDepositPublished()) {
            log.info("Taker deposit fee tx {} has been published.", trade.getDepositTxId());
            return true;
        } else if (currentProtocolStep.equals(WAIT_FOR_TAKER_DEPOSIT_TX_CONFIRMED) && trade.getIsDepositConfirmed()) {
            log.info("Taker deposit fee tx {} has been confirmed.", trade.getDepositTxId());
            return true;
        } else {
            return false;
        }
    };

    private void validateCurrentProtocolStep(Enum<?>... validBotSteps) {
        for (Enum<?> validBotStep : validBotSteps) {
            if (currentProtocolStep.equals(validBotStep))
                return;
        }
        throw new IllegalStateException("Unexpected bot step: " + currentProtocolStep.name() + ".\n"
                + "Must be one of "
                + stream(validBotSteps).map((Enum::name)).collect(Collectors.joining(","))
                + ".");
    }

    private String waitingForDepositFeeTxMsg(String tradeId) {
        return format("Waiting for taker deposit fee tx for trade %s to be %s.",
                tradeId,
                currentProtocolStep.equals(WAIT_FOR_TAKER_DEPOSIT_TX_PUBLISHED) ? "published" : "confirmed");
    }

    private String stoppedWaitingForDepositFeeTxMsg(String tradeId) {
        return format("Taker deposit fee tx for trade %s took too long to be %s;  we won't wait any longer.",
                tradeId,
                currentProtocolStep.equals(WAIT_FOR_TAKER_DEPOSIT_TX_PUBLISHED) ? "published" : "confirmed");
    }

    public static long toDollars(long volume) {
        DecimalFormat df = new DecimalFormat("#########");
        df.setMaximumFractionDigits(0);
        df.setRoundingMode(RoundingMode.UNNECESSARY);
        return Long.parseLong(df.format((double) volume / 10000));
    }
}
