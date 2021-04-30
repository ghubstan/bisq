package bisq.cli;

import bisq.proto.grpc.PollOfferStatusReply;
import bisq.proto.grpc.PollOfferStatusRequest;

import io.grpc.stub.StreamObserver;

import java.time.LocalDateTime;

import static bisq.proto.grpc.OfferStatus.DONE;
import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static java.lang.System.out;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Bi-directional response observer.  Streams offer ids to server, server responds with
 * streamed offer status changes.
 */
public class BidiPollOfferStatusObserver implements StreamObserver<PollOfferStatusReply> {

    private StreamObserver<PollOfferStatusRequest> requestStreamObserver;

    public void startPolling(StreamObserver<PollOfferStatusRequest> requestStreamObserver) {
        this.requestStreamObserver = requestStreamObserver;


        poll("1");
        sleepUninterruptibly(2, SECONDS);

        poll("2");
        sleepUninterruptibly(2, SECONDS);

        poll("3");
    }

    private void poll(String offerId) {
        var request = PollOfferStatusRequest.newBuilder().setOfferId(offerId).build();
        requestStreamObserver.onNext(request);
        sleepUninterruptibly(5, SECONDS);
    }

    @Override
    public void onNext(PollOfferStatusReply reply) {
        if (reply.getOfferId().equals("3") && reply.getOfferStatus().equals(DONE)) {
            // The final offer (3) is 'done'.  Now we can close the stream.
            out.println(LocalDateTime.now() + " : Offer " + reply.getOfferId() + " Status " + reply.getOfferStatus());
            this.requestStreamObserver.onCompleted();
        } else {
            out.println(LocalDateTime.now() + " : Offer " + reply.getOfferId() + " Status " + reply.getOfferStatus());
            var isDone = reply.getOfferStatus().equals(DONE);
            if (!isDone)
                this.poll(reply.getOfferId());
        }
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
