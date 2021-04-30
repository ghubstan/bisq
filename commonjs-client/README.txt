------------------------
Grpc-Web Node-JS Example
------------------------

System Requrirements:  npm and envoy (proxy)


To clone branch, build, and start regtest test harness:

$ git clone -b grpc-streaming-examples https://github.com/ghubstan/bisq.git grpc-streaming-examples
$ cd grpc-streaming-examples
$ ./gradlew clean build :apitest:installDaoSetup
$ ./bisq-apitest --apiPassword=xyz --supportingApps=bitcoind,seednode,arbdaemon,alicedaemon  --shutdownAfterTests=false



To build the node-js client and start the envoy proxy:

$ cd commonjs-client/build-node-client-n-start-proxy.sh
$ ./build-node-client-n-start-proxy.sh


To run html/js streaming example:

Open index.html in browser and watch console



To run CLI uni and bi directional streaming example:

Create IDE launcher for NotificationsClient

Run with program argument  --password=xyz 		to see uni directional streaming (push from server)

Run with program arguments --password=xyz bidi 	to see bi directional streaming (push from server, push from CLI)



