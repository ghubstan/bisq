package bisq.cli;

import bisq.proto.grpc.PollOfferStatusReply;

import io.grpc.stub.StreamObserver;

import java.time.LocalDateTime;

import static java.lang.System.out;

public class PollOfferStatusObserver implements StreamObserver<PollOfferStatusReply> {

    @Override
    public void onNext(PollOfferStatusReply reply) {
        out.println(LocalDateTime.now() + " : Offer " + reply.getOfferId() + " Status " + reply.getOfferStatus());
    }

    @Override
    public void onError(Throwable t) {
        t.printStackTrace();
    }

    @Override
    public void onCompleted() {
        out.println(LocalDateTime.now() + " : done");
    }
}
