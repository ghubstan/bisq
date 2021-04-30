package bisq.daemon.grpc;

import bisq.proto.grpc.OfferStatus;
import bisq.proto.grpc.PollOfferStatusReply;
import bisq.proto.grpc.PollOfferStatusRequest;

import io.grpc.stub.StreamObserver;

import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import static bisq.proto.grpc.OfferStatus.DONE;
import static bisq.proto.grpc.OfferStatus.PREPARE_OFFER;

/**
 * Bi-directional response observer.  Streams changing offer status to client.
 */
@Slf4j
public class OfferStatusRequestObserver implements StreamObserver<PollOfferStatusRequest> {

    private final Map<String, OfferStatus> offerStatusMap = new HashMap<>() {{
        put("1", null);
        put("2", null);
        put("3", null);
    }};

    private final StreamObserver<PollOfferStatusReply> pollOfferStatusResponseObserver;

    public OfferStatusRequestObserver(StreamObserver<PollOfferStatusReply> pollOfferStatusResponseObserver) {
        this.pollOfferStatusResponseObserver = pollOfferStatusResponseObserver;
    }

    @Override
    public void onNext(PollOfferStatusRequest request) {
        var offerId = request.getOfferId();
        var offerStatus = incrementOfferStatus(offerId);
        log.info("Offer/Trade with id {} status {}", offerId, offerStatus.name());
        var reply = PollOfferStatusReply.newBuilder()
                .setOfferId(offerId)
                .setOfferStatus(offerStatus)
                .setOfferStatusDescription(offerStatus.name())
                .build();
        this.pollOfferStatusResponseObserver.onNext(reply);
    }

    @Override
    public void onError(Throwable t) {
        log.error("Bi-directional Streaming Error", t);
    }

    @Override
    public void onCompleted() {
        this.pollOfferStatusResponseObserver.onCompleted();
    }

    private OfferStatus incrementOfferStatus(String offerId) {
        var currentOfferStatus = offerStatusMap.get(offerId);
        if (currentOfferStatus == null) {
            offerStatusMap.put(offerId, PREPARE_OFFER);
        } else if (!currentOfferStatus.equals(DONE)) {
            offerStatusMap.put(offerId, OfferStatus.forNumber(currentOfferStatus.getNumber() + 1));
        }
        return offerStatusMap.get(offerId);
    }
}
