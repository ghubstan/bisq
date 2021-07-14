# webapp-in-daemon-poc

This Bisq branch shows how to embed a `Ratpack` https://ratpack.io webapp in a Bisq Daemon.

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

## Notes

The `Ratpack` webapp is embedded in `bisq.daemon.app.BisqDaemonMain` for demo purposes only;  the gRPC server and web server should be decoupled.

The same `CoreApi` instance used by the Bisq Daemon is injected into the `Ratpack` webapp. 
See https://github.com/ghubstan/bisq/blob/webapp-in-daemon-poc/daemon/src/main/java/bisq/daemon/app/BisqDaemonMain.java

## Ratpack Demos

The branch also contains some Ratpack webapp demos, including unidirectional and bidirectional streaming examples.
See https://github.com/ghubstan/bisq/blob/webapp-in-daemon-poc/daemon/src/main/java/bisq/daemon/web/server/RatpackStreamingObserverApp.java



