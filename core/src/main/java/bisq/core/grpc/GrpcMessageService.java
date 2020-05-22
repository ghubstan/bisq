package bisq.core.grpc;

import bisq.proto.grpc.Command;
import bisq.proto.grpc.MessageServiceGrpc;
import bisq.proto.grpc.Result;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import javax.inject.Inject;

class GrpcMessageService extends MessageServiceGrpc.MessageServiceImplBase {

    private final CoreMessageService messageService;

    @Inject
    public GrpcMessageService(CoreMessageService messageService) {
        this.messageService = messageService;
    }

    @Override
    public void call(Command req, StreamObserver<Result> responseObserver) {
        try {
            String response = messageService.call(req.getParams());
            var reply = Result.newBuilder().setData(response).build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        } catch (IllegalStateException cause) {
            var ex = new StatusRuntimeException(Status.UNKNOWN.withDescription(cause.getMessage()));
            responseObserver.onError(ex);
            throw ex;
        }
    }
}
