#!/bin/bash

#################################################################
# Builds grpc-web plugin, node-js client, and started envoy proxy.
#
# System Requirements:  Node (npm) 6.14+
#
# Run this script from the parent commonjs-client directory.
#################################################################

# Build the grpc-web plugin.
git clone https://github.com/grpc/grpc-web.git
git checkout tags/1.2.1
cd grpc-web
sudo make install-plugin

cd ..

# Generate JavaScript protobuf messages from pb.proto and grpc.proto.
protoc --proto_path=../proto/src/main/proto pb.proto grpc.proto --js_out=import_style=commonjs:.

# Generate JavaScript Service Client Stub from pb.proto and grpc.proto.
protoc --proto_path=../proto/src/main/proto pb.proto grpc.proto --grpc-web_out=import_style=commonjs,mode=grpcwebtext:.

# Install npm modules
npm install

# Build client
npx webpack client.js

# Start Envoy proxy
envoy -c bisq-envoy.yaml

