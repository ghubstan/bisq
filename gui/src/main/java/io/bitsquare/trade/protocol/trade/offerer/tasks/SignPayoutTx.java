/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.trade.protocol.trade.offerer.tasks;

import io.bitsquare.btc.WalletService;
import io.bitsquare.util.handlers.ExceptionHandler;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;

import javafx.util.Pair;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SignPayoutTx {
    private static final Logger log = LoggerFactory.getLogger(SignPayoutTx.class);

    public static void run(ResultHandler resultHandler,
                           ExceptionHandler exceptionHandler,
                           WalletService walletService,
                           String tradeId,
                           String takerPayoutAddress,
                           String offererPayoutAddress,
                           String depositTransactionId,
                           Coin securityDeposit,
                           Coin tradeAmount) {
        log.trace("Run SignPayoutTx task");
        try {
            Coin offererPaybackAmount = tradeAmount.add(securityDeposit);
            @SuppressWarnings("UnnecessaryLocalVariable") Coin takerPaybackAmount = securityDeposit;

            Pair<ECKey.ECDSASignature, String> result = walletService.offererCreatesAndSignsPayoutTx(
                    depositTransactionId, offererPaybackAmount, takerPaybackAmount, takerPayoutAddress, tradeId);

            ECKey.ECDSASignature offererSignature = result.getKey();
            String offererSignatureR = offererSignature.r.toString();
            String offererSignatureS = offererSignature.s.toString();
            String depositTxAsHex = result.getValue();

            resultHandler.handleResult(depositTxAsHex, offererSignatureR, offererSignatureS, offererPaybackAmount, takerPaybackAmount, offererPayoutAddress);
        } catch (Exception e) {
            exceptionHandler.handleException(e);
        }
    }

    public interface ResultHandler {
        void handleResult(String depositTxAsHex,
                          String offererSignatureR,
                          String offererSignatureS,
                          Coin offererPaybackAmount,
                          Coin takerPaybackAmount,
                          String offererPayoutAddress);
    }

}

