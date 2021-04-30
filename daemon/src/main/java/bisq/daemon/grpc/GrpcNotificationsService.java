package bisq.daemon.grpc;

import bisq.core.api.CoreApi;

import bisq.proto.grpc.PollOfferStatusReply;
import bisq.proto.grpc.PollOfferStatusRequest;

import io.grpc.stub.StreamObserver;

import javax.inject.Inject;

import com.google.common.util.concurrent.Uninterruptibles;

import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;

import static bisq.proto.grpc.NotificationsGrpc.NotificationsImplBase;
import static bisq.proto.grpc.OfferStatus.*;
import static java.util.concurrent.TimeUnit.SECONDS;

@Slf4j
class GrpcNotificationsService extends NotificationsImplBase {

    private final CoreApi coreApi;
    private final GrpcExceptionHandler exceptionHandler;

    @Inject
    public GrpcNotificationsService(CoreApi coreApi, GrpcExceptionHandler exceptionHandler) {
        this.coreApi = coreApi;
        this.exceptionHandler = exceptionHandler;
    }

    @Override
    public void pollOfferStatus(PollOfferStatusRequest req,
                                StreamObserver<PollOfferStatusReply> responseObserver) {
        try {
            String offerId = req.getOfferId();
            handlePrepareOffer(offerId, responseObserver);
            handlePrepareTrade(offerId, responseObserver);
            responseObserver.onCompleted();
        } catch (Throwable cause) {
            exceptionHandler.handleException(log, cause, responseObserver);
        }
    }

    @Override
    public StreamObserver<PollOfferStatusRequest> bidiPollOfferStatus(StreamObserver<PollOfferStatusReply> responseObserver) {
        return new OfferStatusRequestObserver(responseObserver);
    }

    private void handlePrepareOffer(String offerId, StreamObserver<PollOfferStatusReply> responseObserver) {
        log.info("Preparing offer with id {}: Sending status PREPARE_OFFER, PUBLISH_MAKER_FEE_TX, CONFIRM_MAKER_FEE_TX to client.", offerId);
        Stream.of(PREPARE_OFFER, PUBLISH_MAKER_FEE_TX, CONFIRM_MAKER_FEE_TX)
                .map(status -> PollOfferStatusReply.newBuilder()
                        .setOfferId(offerId)
                        .setOfferStatus(status)
                        .setOfferStatusDescription(status.name()))
                .map(PollOfferStatusReply.Builder::build)
                .peek(i -> Uninterruptibles.sleepUninterruptibly(3, SECONDS))
                .forEach(responseObserver::onNext);
    }

    private void handlePrepareTrade(String offerId, StreamObserver<PollOfferStatusReply> responseObserver) {
        log.info("Preparing trade with id {}: Sending status PREPARE_TRADE, PUBLISH_TAKER_FEE_TX, CONFIRM_TAKER_FEE_TX, DONE to client.", offerId);
        Stream.of(PREPARE_TRADE, PUBLISH_TAKER_FEE_TX, CONFIRM_TAKER_FEE_TX, DONE)
                .map(status -> PollOfferStatusReply.newBuilder()
                        .setOfferId(offerId)
                        .setOfferStatus(status)
                        .setOfferStatusDescription(status.name()))
                .map(PollOfferStatusReply.Builder::build)
                .peek(i -> Uninterruptibles.sleepUninterruptibly(3, SECONDS))
                .forEach(responseObserver::onNext);
    }
}
