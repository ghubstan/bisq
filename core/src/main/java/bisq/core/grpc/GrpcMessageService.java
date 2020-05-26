package bisq.core.grpc;

import bisq.proto.grpc.Command;
import bisq.proto.grpc.MessageServiceGrpc;
import bisq.proto.grpc.Response;

import io.grpc.Context;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import javax.inject.Inject;

import static bisq.core.grpc.PasswordAuthInterceptor.HTTP1_REQUEST_CTX_KEY;

class GrpcMessageService extends MessageServiceGrpc.MessageServiceImplBase {

    private final GrpcCoreBridge bridge;

    @Inject
    public GrpcMessageService(GrpcCoreBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public void call(Command req, StreamObserver<Response> responseObserver) {
        try {
            boolean isGatewayRequest = HTTP1_REQUEST_CTX_KEY.get(Context.current());
            String result = bridge.call(req.getParams(), isGatewayRequest);
            var reply = Response.newBuilder().setResult(result).build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        } catch (StatusRuntimeException ex) {
            responseObserver.onError(ex);
            throw ex;
        } catch (RuntimeException cause) {
            var ex = new StatusRuntimeException(Status.UNKNOWN.withDescription(cause.getMessage()));
            responseObserver.onError(ex);
            throw ex;
        }
    }
}
