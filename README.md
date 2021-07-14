# webapp-in-daemon-poc

This Bisq branch contains TDOD

## Requirements

### Build Bisq with the API test harness' DAO setup data.

`$ ./gradlew clean build :apitest:installDaoSetup -x test`

### Start the API test harness.

`$ ./bisq-apitest --apiPassword=xyz --supportingApps=bitcoind,seednode,alicedaemon  --shutdownAfterTests=false`

## gRPC Server < > gRPC CLI Streaming Demos

### Bidirectional streaming between gRPC server and CLI
