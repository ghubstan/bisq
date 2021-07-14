# grpc-streaming-examples

This Bisq branch contains simple gRPC streaming demonstrations.

## Requirements

### Build Bisq with the API test harness' DAO setup data.

`$ ./gradlew clean build :apitest:installDaoSetup -x test`

### Start the API test harness.

`$ ./bisq-apitest --apiPassword=xyz --supportingApps=bitcoind,seednode,alicedaemon  --shutdownAfterTests=false`

## gRPC Server < > gRPC CLI Streaming Demos

### Bidirectional streaming between gRPC server and CLI

The `bisq.daemon.grpc.GrpcNotificationsService` handles two types of requests from the `bisq.cli.NotificationsClient`:
one for demonstrating unidirectional gRPC streaming, another demonstrating bidirectional gRPC streaming.

#### Unidirectional gRPC Streaming Demo

Create an Intellij application launcher for `NotificationsClient`, and add the API test harness' server password
as a CLI program argument:  `--password=xyz`

Run `NotificationsClient --password=xyz`

Alice's Bisq Daemon log can be viewed during the demo.
`$ tail -n 500 -f  apitest/build/resources/main/bisq-BTC_REGTEST_Alice_dao/bisq.log`

#### Bidirectional gRPC Streaming Demo

Clone the Intellij launcher created in the previous section, and add one more CLI argument:  `bidi`

Run `NotificationsClient --password=xyz bidi`

Alice's Bisq Daemon log can be viewed during the demo.
`$ tail -n 500 -f  apitest/build/resources/main/bisq-BTC_REGTEST_Alice_dao/bisq.log`

### gRPC < > HTML Streaming Demos

This demo shows how node-js (commonjs-client) can make gRPC requests directly to a Bisq Daemon via an Envoy HTTP Proxy,
from the `grpc-web` framework (https://github.com/grpc/grpc-web.git).

The steps taken to build a node-js client and set up an Envoy proxy are in:

https://github.com/ghubstan/bisq/blob/grpc-streaming-examples/commonjs-client/README.txt

https://github.com/ghubstan/bisq/blob/grpc-streaming-examples/commonjs-client/build-node-client-n-start-proxy.sh

Note: `grpc-web` does not yet support bidirectional streaming.  It may be better to use an embedded webapp/microservice for serving web clients, injecting a `CoreApi` into both the gRPC server and webapp.
