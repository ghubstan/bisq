# webapp-in-daemon-poc

This Bisq branch shows how to embed a `Ratpack` https://ratpack.io webapp in a Bisq Daemon.

## Requirements

### Build Bisq

`$ ./gradlew clean build -x test`

### Start the Bisq Daemon

`$ ./bisq-daemon --apiPassword=xyz`

### Verify Web Server Running

http://localhost:5050

## Call CoreApi#getVersion from Browser

Ratpack can support many types of HTTP clients, including REST clients, e.g.,

http://localhost:5050/version

The same `CoreApi` instance used by the Bisq Daemon is injected into the `Ratpack` webapp. 
See https://github.com/ghubstan/bisq/blob/webapp-in-daemon-poc/daemon/src/main/java/bisq/daemon/app/BisqDaemonMain.java

_The `Ratpack` webapp is embedded in `bisq.daemon.app.BisqDaemonMain` for demo purposes only;  the gRPC server and web server would be decoupled in a production app._

## Ratpack Demos

The branch also contains some Ratpack webapp demos, including unidirectional and bidirectional streaming examples.

`RatpackStreamingObserverApp` demonstrates web streaming with a `java.net.http.HttpClient`

See https://github.com/ghubstan/bisq/blob/webapp-in-daemon-poc/daemon/src/main/java/bisq/daemon/web/server/RatpackStreamingObserverApp.java

And https://github.com/ghubstan/bisq/blob/webapp-in-daemon-poc/daemon/src/main/java/bisq/daemon/web/client/HttpClientApplication.java

## Streaming Between gRPC Server and Web Server

Unidirectional and bidirectional streaming between gRPC server and a gRPC CLI is demonstrated in https://github.com/ghubstan/bisq/blob/grpc-streaming-examples/README.md.  A Ratpack web server could also make streaming requests to a gRPC server, but the investigation was put aside before I implemented gRPC < > webapp streaming example.


