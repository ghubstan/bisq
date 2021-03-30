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

package bisq.bot.protocol;

import bisq.proto.grpc.OfferInfo;
import bisq.proto.grpc.TradeInfo;

import protobuf.PaymentAccount;

import java.math.BigDecimal;
import java.math.MathContext;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import lombok.Getter;

import static bisq.bot.protocol.ProtocolStep.DONE;
import static bisq.bot.protocol.ProtocolStep.WAIT_FOR_OFFER_TAKER;
import static bisq.bot.shutdown.ManualShutdown.checkIfShutdownCalled;
import static bisq.cli.TableFormat.formatOfferTable;
import static java.lang.String.format;
import static java.math.RoundingMode.HALF_UP;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static protobuf.OfferPayload.Direction;
import static protobuf.OfferPayload.Direction.BUY;
import static protobuf.OfferPayload.Direction.SELL;



import bisq.bot.BotClient;
import bisq.bot.script.BashScriptGenerator;
import bisq.cli.TradeFormat;

public class MarketMakerBotProtocol extends BotProtocol implements MakerBotProtocol {

    // Don't use @Slf4j annotation to init log and use in Functions w/out IDE warnings.
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MarketMakerBotProtocol.class);

    private static final BigDecimal MAX_BTC_AMOUNT_DEVIATION_SIZE = new BigDecimal("0.000001");
    private static final BigDecimal MAX_BSQ_PRICE_DEVIATION_SIZE = new BigDecimal("0.000001");
    private static final BigDecimal MARGIN_PERCENT_MULTIPLICAND = new BigDecimal("0.01");

    protected final String direction;
    @Getter
    protected final double priceMargin;
    @Getter
    protected final BigDecimal targetBsqPrice;
    @Getter
    protected final BigDecimal targetBtcAmount;
    @Getter
    protected final double securityDepositAsPercent;
    @Getter
    protected final String tradingFeeCurrencyCode;
    @Getter
    protected final int maxCreateOfferFailureLimit;

    protected final Supplier<Long> nextTradeBtcAmount = () -> {
        BigDecimal nextBtcAmountDeviation = new BigDecimal(RANDOM.nextDouble() * MAX_BTC_AMOUNT_DEVIATION_SIZE.doubleValue(),
                new MathContext(2, HALF_UP));
        BigDecimal nextTradeBtcAmount = RANDOM.nextBoolean()
                ? this.getTargetBtcAmount().subtract(nextBtcAmountDeviation)
                : this.getTargetBtcAmount().add(nextBtcAmountDeviation);
        long amountInSatoshis = nextTradeBtcAmount.scaleByPowerOfTen(8).longValue();
        log.info("Calculated next trade's amount: {} BTC ({} sats) using target amount {} and max deviation {}.",
                nextTradeBtcAmount,
                amountInSatoshis,
                this.getTargetBtcAmount(),
                MAX_BTC_AMOUNT_DEVIATION_SIZE);
        return amountInSatoshis;
    };

    protected final Supplier<BigDecimal> nextTradeBsqPrice = () -> {
        BigDecimal nextBsqPriceDeviation = new BigDecimal(RANDOM.nextDouble() * MAX_BSQ_PRICE_DEVIATION_SIZE.doubleValue(),
                new MathContext(2, HALF_UP));
        BigDecimal nextTradeBsqPrice = RANDOM.nextBoolean()
                ? this.getTargetBsqPrice().add(nextBsqPriceDeviation)
                : this.getTargetBsqPrice().subtract(nextBsqPriceDeviation);
        log.info("Calculated next trade's BSQ price: {} BTC using target price {} and max deviation {}.",
                nextTradeBsqPrice.toPlainString(),
                this.getTargetBsqPrice(),
                MAX_BSQ_PRICE_DEVIATION_SIZE.toPlainString());
        return nextTradeBsqPrice;
    };

    protected final Function<BigDecimal, Double> calculatedMargin = (targetSpread) -> {
        BigDecimal margin = targetSpread.divide(new BigDecimal(2));
        return new BigDecimal(margin.doubleValue(), new MathContext(3, HALF_UP))
                .doubleValue();
    };

    protected final Function<Direction, BigDecimal> calculateNextTradeMarginBasedPrice = (direction) -> {
        BigDecimal marginAsPercent = new BigDecimal(this.getPriceMargin());
        BigDecimal marginAsDecimal = marginAsPercent.multiply(MARGIN_PERCENT_MULTIPLICAND);
        BigDecimal basePrice = nextTradeBsqPrice.get();
        BigDecimal delta = basePrice.multiply(marginAsDecimal);
        BigDecimal nextMarginPrice = direction.equals(BUY)
                ? new BigDecimal(basePrice.subtract(delta).doubleValue(), new MathContext(5, HALF_UP))
                : new BigDecimal(basePrice.add(delta).doubleValue(), new MathContext(5, HALF_UP));
        log.info("Calculated next {} trade's BSQ margin based price: {} BTC based on adjusted base price {} {} {}% margin.",
                direction.name(),
                nextMarginPrice.toPlainString(),
                basePrice.toPlainString(),
                direction.equals(BUY) ? "-" : "+",
                this.getPriceMargin());
        return nextMarginPrice;
    };

    protected final Function<Double, Double> roundedSecurityDeposit = (securityDepositAsPercent) ->
            new BigDecimal(securityDepositAsPercent).round(new MathContext(3, HALF_UP)).doubleValue();


    public static void main(String[] args) {
        BigDecimal targetBtcAmount = new BigDecimal("0.05");
        BigDecimal targetPrice = new BigDecimal("0.00010");
        BigDecimal targetSpread = new BigDecimal("10.00");

        BigDecimal nextBtcAmountDeviation = new BigDecimal(RANDOM.nextDouble() * MAX_BTC_AMOUNT_DEVIATION_SIZE.doubleValue(),
                new MathContext(2, HALF_UP));
        BigDecimal nextTradeBtcAmount = RANDOM.nextBoolean()
                ? targetBtcAmount.subtract(nextBtcAmountDeviation)
                : targetBtcAmount.add(nextBtcAmountDeviation);
        long amountInSatoshis = nextTradeBtcAmount.scaleByPowerOfTen(8).longValue();
        log.info("Calculated next trade's amount: {} BTC ({} sats) using target amount {} and max deviation {}.",
                nextTradeBtcAmount,
                amountInSatoshis,
                targetBtcAmount,
                MAX_BTC_AMOUNT_DEVIATION_SIZE);

        BigDecimal nextBsqPriceDeviation = new BigDecimal(RANDOM.nextDouble() * MAX_BSQ_PRICE_DEVIATION_SIZE.doubleValue(),
                new MathContext(2, HALF_UP));
        BigDecimal nextTradeBsqPrice = RANDOM.nextBoolean()
                ? targetPrice.add(nextBsqPriceDeviation)
                : targetPrice.subtract(nextBsqPriceDeviation);
        log.info("Calculated next trade's BSQ price: {} BTC using target price {} and max deviation {}.",
                nextTradeBsqPrice.toPlainString(),
                targetPrice,
                MAX_BSQ_PRICE_DEVIATION_SIZE.toPlainString());


        BigDecimal margin = targetSpread.divide(new BigDecimal(2));
        double marginAsDouble = new BigDecimal(margin.doubleValue(), new MathContext(3, HALF_UP)).doubleValue();
        BigDecimal marginAsPercent = new BigDecimal(marginAsDouble);
        BigDecimal marginAsDecimal = marginAsPercent.multiply(MARGIN_PERCENT_MULTIPLICAND);
        BigDecimal delta = nextTradeBsqPrice.multiply(marginAsDecimal);
        Direction direction = RANDOM.nextBoolean() ? BUY : SELL;
        BigDecimal nextMarginPrice = direction.equals(BUY)
                ? new BigDecimal(nextTradeBsqPrice.subtract(delta).doubleValue(), new MathContext(5, HALF_UP))
                : new BigDecimal(nextTradeBsqPrice.add(delta).doubleValue(), new MathContext(5, HALF_UP));
        log.info("Calculated next {} trade's BSQ margin based price: {} BTC based on adjusted target price {} and {}% margin.",
                direction.name(),
                nextMarginPrice.toPlainString(),
                nextTradeBsqPrice,
                marginAsDouble);
    }


    public MarketMakerBotProtocol(String botDescription,
                                  BotClient botClient,
                                  PaymentAccount paymentAccount,
                                  long protocolStepTimeLimitInMs,
                                  BashScriptGenerator bashScriptGenerator,
                                  String direction,
                                  BigDecimal targetBsqPrice,
                                  BigDecimal targetBtcAmount,
                                  BigDecimal targetSpread,
                                  double securityDepositAsPercent,
                                  String tradingFeeCurrencyCode,
                                  int maxCreateOfferFailureLimit) {
        super(botDescription,
                botClient,
                paymentAccount,
                protocolStepTimeLimitInMs,
                bashScriptGenerator);
        this.direction = direction;
        this.priceMargin = calculatedMargin.apply(targetSpread);
        this.targetBsqPrice = targetBsqPrice;
        this.targetBtcAmount = targetBtcAmount;
        this.securityDepositAsPercent = roundedSecurityDeposit.apply(securityDepositAsPercent);
        this.tradingFeeCurrencyCode = tradingFeeCurrencyCode;
        this.maxCreateOfferFailureLimit = maxCreateOfferFailureLimit;
    }

    @Override
    public void run() {
        checkIsStartStep();

        var isBuy = direction.equalsIgnoreCase(BUY.name());
        Function<Supplier<OfferInfo>, TradeInfo> makeTrade = waitForNewTrade.andThen(waitForTakerFeeTxConfirm);
        var trade = isBuy
                ? makeTrade.apply(createBuyOffer)
                : makeTrade.apply(createSellOffer);

        var makerIsBuyer = trade.getOffer().getDirection().equalsIgnoreCase(BUY.name());

        Function<TradeInfo, TradeInfo> completeFiatTransaction = makerIsBuyer
                ? sendPaymentStartedMessage.andThen(waitForPaymentReceivedConfirmation)
                : waitForPaymentStartedMessage.andThen(sendPaymentReceivedMessage);
        completeFiatTransaction.apply(trade);

        Function<TradeInfo, TradeInfo> closeTrade = waitForPayoutTx.andThen(keepFundsFromTrade);
        closeTrade.apply(trade);

        // TODO track changes in balances and print here.

        currentProtocolStep = DONE;
    }

    protected final Supplier<OfferInfo> createBuyOffer = () -> {
        checkIfShutdownCalled("Interrupted before creating random BUY offer.");
        int attempts;
        for (attempts = 0; attempts < this.getMaxCreateOfferFailureLimit(); attempts++) {
            try {
                var amount = nextTradeBtcAmount.get();
                var isBsqOffer = paymentAccount.getSelectedTradeCurrency().getCode().equals(BSQ);
                OfferInfo offer;
                if (isBsqOffer) {
                    var priceAsString = calculateNextTradeMarginBasedPrice.apply(BUY).toString();
                    offer = botClient.createOfferAtFixedPrice(paymentAccount,
                            SELL.name(),  // This is the Buy BSQ (Sell BTC) bot.
                            currencyCode,
                            amount,
                            amount,
                            priceAsString,
                            this.getSecurityDepositAsPercent(),
                            this.getTradingFeeCurrencyCode());

                } else {
                    offer = botClient.createOfferAtMarketBasedPrice(paymentAccount,
                            BUY.name(),
                            currencyCode,
                            amount,
                            amount,
                            this.getPriceMargin(),
                            this.getSecurityDepositAsPercent(),
                            this.getTradingFeeCurrencyCode());
                    log.info("Created BUY / {} offer at {}% below current market price of {}:\n{}",
                            currencyCode,
                            this.getPriceMargin(),
                            botClient.getCurrentBTCMarketPriceAsString(currencyCode),
                            formatOfferTable(singletonList(offer), currencyCode));
                }
                return offer;
            } catch (Exception ex) {
                log.error("Failed to create BUY offer after attempt #{}.", attempts, ex);
                try {
                    SECONDS.sleep(30);
                } catch (InterruptedException interruptedException) {
                }
            }
        }
        throw new CreateOfferException(format("%s could not create offer after %s attempts.",
                botDescription,
                attempts));
    };

    protected final Supplier<OfferInfo> createSellOffer = () -> {
        checkIfShutdownCalled("Interrupted before creating random SELL offer.");
        int attempts;
        for (attempts = 0; attempts < this.getMaxCreateOfferFailureLimit(); attempts++) {
            try {
                var amount = nextTradeBtcAmount.get();
                var isBsqOffer = paymentAccount.getSelectedTradeCurrency().getCode().equals(BSQ);
                OfferInfo offer;
                if (isBsqOffer) {
                    var priceAsString = calculateNextTradeMarginBasedPrice.apply(SELL).toString();
                    offer = botClient.createOfferAtFixedPrice(paymentAccount,
                            BUY.name(),  // This is the Sell BSQ (Buy BTC) bot.
                            currencyCode,
                            amount,
                            amount,
                            priceAsString,
                            this.getSecurityDepositAsPercent(),
                            this.getTradingFeeCurrencyCode());

                } else {
                    offer = botClient.createOfferAtMarketBasedPrice(paymentAccount,
                            SELL.name(),
                            currencyCode,
                            amount,
                            amount,
                            this.getPriceMargin(),
                            this.getSecurityDepositAsPercent(),
                            this.getTradingFeeCurrencyCode());
                    log.info("Created SELL / {} offer at {}% above current market price of {}:\n{}",
                            currencyCode,
                            this.getPriceMargin(),
                            botClient.getCurrentBTCMarketPriceAsString(currencyCode),
                            formatOfferTable(singletonList(offer), currencyCode));
                }
                return offer;
            } catch (Exception ex) {
                log.error("Failed to create SELL offer after attempt #{}.", attempts, ex);
                try {
                    SECONDS.sleep(30);
                } catch (InterruptedException interruptedException) {
                }
            }
        }
        throw new CreateOfferException(format("%s could not create offer after %s attempts.",
                botDescription,
                attempts));
    };

    protected final Function<Supplier<OfferInfo>, TradeInfo> waitForNewTrade = (latestOffer) -> {
        initProtocolStep.accept(WAIT_FOR_OFFER_TAKER);
        OfferInfo offer = latestOffer.get();
        // TODO ?  ->  createTakeOfferCliScript(offer);
        log.info("Waiting for offer {} to be taken.", offer.getId());
        int numDelays = 0;
        // An offer may never be taken.  There is no protocol step time limit on takers.
        // This loop can keep the bot alive for an indefinite time, but it can
        // still be manually shutdown.
        while (true) {
            checkIfShutdownCalled("Interrupted while waiting for offer to be taken.");
            try {
                var trade = getNewTrade(offer.getId());
                if (trade.isPresent()) {
                    return trade.get();
                } else {
                    if (++numDelays % 15 == 0) {
                        log.warn("Offer {} still waiting to be taken.", offer.getId());
                        String offerCounterCurrencyCode = offer.getCounterCurrencyCode();
                        List<OfferInfo> myCurrentOffers = botClient.getMyOffersSortedByDate(offerCounterCurrencyCode);
                        if (myCurrentOffers.isEmpty()) {
                            log.warn("{} has no current offers at this time, but offer {} should exist.",
                                    botDescription,
                                    offer.getId());
                        } else {
                            log.info("{}'s current offers {} is in the list, or fail):\n{}",
                                    botDescription,
                                    offer.getId(),
                                    formatOfferTable(myCurrentOffers, offerCounterCurrencyCode));
                        }
                    }
                    sleep(MINUTES.toMillis(1));
                }
            } catch (Exception ex) {
                throw new IllegalStateException(botClient.toCleanGrpcExceptionMessage(ex), ex);
            }
        } // end while
    };

    protected Optional<TradeInfo> getNewTrade(String offerId) {
        try {
            var trade = botClient.getTrade(offerId);
            log.info("Offer {} was taken, new trade:\n{}", offerId, TradeFormat.format(trade));
            return Optional.of(trade);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

}
