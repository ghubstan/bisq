# webapp-in-daemon-poc

This Bisq branch shows how to embed a `Ratpack` webapp in a Bisq Daemon.

## Requirements

### Build Bisq

`$ ./gradlew clean build -x test`

### Start the Bisq Daemon

`$ ./bisq-daemon --apiPassword=xyz`

### Verify Web Server Running

http://localhost:5050

## Call gRPC GetVersion from Browser

http://localhost:5050/version

### Bidirectional streaming between gRPC server and CLI
