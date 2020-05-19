package bisq.core.grpc;

import bisq.proto.grpc.BisqReply;
import bisq.proto.grpc.BisqRequest;
import bisq.proto.grpc.BisqServiceGrpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import javax.inject.Inject;

class GrpcBisqService extends BisqServiceGrpc.BisqServiceImplBase {

    private final CoreBisqService bisqService;

    @Inject
    public GrpcBisqService(CoreBisqService bisqService) {
        this.bisqService = bisqService;
    }

    @Override
    public void call(BisqRequest req, StreamObserver<BisqReply> responseObserver) {
        try {
            String response = bisqService.call(req.getUri());
            var reply = BisqReply.newBuilder().setResponse(response).build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        } catch (IllegalStateException cause) {
            var ex = new StatusRuntimeException(Status.UNKNOWN.withDescription(cause.getMessage()));
            responseObserver.onError(ex);
            throw ex;
        }
    }
}
