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

package bisq.cli;

import bisq.proto.grpc.PollOfferStatusRequest;

import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

import com.google.common.util.concurrent.Uninterruptibles;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import static bisq.cli.opts.OptLabel.OPT_HOST;
import static bisq.cli.opts.OptLabel.OPT_PASSWORD;
import static bisq.cli.opts.OptLabel.OPT_PORT;
import static java.lang.System.err;
import static java.lang.System.exit;
import static java.lang.System.out;
import static java.util.concurrent.TimeUnit.SECONDS;



import bisq.cli.opts.ArgumentList;

/**
 * Start test harness:
 * ./bisq-apitest --apiPassword=xyz --supportingApps=bitcoind,seednode,alicedaemon  --shutdownAfterTests=false
 * To make bidi streaming request, use program args --password=xyz bidi
 * To make uni stream request, use program arg --password=xyz
 */
@Slf4j
public class NotificationsClient {

    public static void main(String[] args) {
        try {
            run(args);
        } catch (Throwable t) {
            err.println("Error: " + t.getMessage());
            exit(1);
        }
    }

    public static void run(String[] args) {
        var parser = new OptionParser();

        var hostOpt = parser.accepts(OPT_HOST, "rpc server hostname or ip")
                .withRequiredArg()
                .defaultsTo("localhost");

        var portOpt = parser.accepts(OPT_PORT, "rpc server port")
                .withRequiredArg()
                .ofType(Integer.class)
                .defaultsTo(9998);

        var passwordOpt = parser.accepts(OPT_PASSWORD, "rpc server password")
                .withRequiredArg();

        // Parse the CLI opts host, port, password, method name, and help.  The help opt
        // may indicate the user is asking for method level help, and will be excluded
        // from the parsed options if a method opt is present in String[] args.
        OptionSet options = parser.parse(new ArgumentList(args).getCLIArguments());
        @SuppressWarnings("unchecked")
        var nonOptionArgs = (List<String>) options.nonOptionArguments();

        var host = options.valueOf(hostOpt);
        var port = options.valueOf(portOpt);
        var password = options.valueOf(passwordOpt);
        if (password == null)
            throw new IllegalArgumentException("missing required 'password' option");

        GrpcStubs clientStubs = new GrpcStubs(host, port, password);
        try {

            var isBidiRequest = nonOptionArgs.size() > 0 && nonOptionArgs.get(0).equals("bidi");
            if (isBidiRequest) {
                out.println("Sending bi-directional streaming request.");
                BidiPollOfferStatusObserver responseStreamObserver = new BidiPollOfferStatusObserver();
                StreamObserver<PollOfferStatusRequest> requestStreamObserver =
                        clientStubs.notificationsService.bidiPollOfferStatus(responseStreamObserver);
                responseStreamObserver.startPolling(requestStreamObserver);
                Uninterruptibles.sleepUninterruptibly(120, SECONDS);
            } else {
                out.println("Sending uni-directional streaming request.");
                PollOfferStatusRequest request = PollOfferStatusRequest.newBuilder().setOfferId("1").build();
                clientStubs.notificationsService.pollOfferStatus(request, new PollOfferStatusObserver());
                Uninterruptibles.sleepUninterruptibly(30, SECONDS);
            }

        } catch (StatusRuntimeException ex) {
            // Remove the leading gRPC status code (e.g. "UNKNOWN: ") from the message
            String message = ex.getMessage().replaceFirst("^[A-Z_]+: ", "");
            if (message.equals("io exception"))
                throw new RuntimeException(message + ", server may not be running", ex);
            else
                throw new RuntimeException(message, ex);
        }
    }
}
