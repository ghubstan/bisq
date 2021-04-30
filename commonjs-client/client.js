const SERVER_URL = 'http://localhost:8080';

const {GetVersionRequest, GetVersionReply} = require('./grpc_pb.js');
const {GetVersionClient} = require('./grpc_grpc_web_pb.js');
var versionService = new GetVersionClient(SERVER_URL);
var request = new GetVersionRequest();
versionService.getVersion(request, {'password':'xyz'}, function(err, response) {
	console.log('Bisq Version: ' + response.getVersion())
});

const {GetPaymentAccountsRequest, GetPaymentAccountsReply} = require('./grpc_pb.js');
const {PaymentAccountsClient} = require('./grpc_grpc_web_pb.js');
var paymentAccountsService = new PaymentAccountsClient(SERVER_URL);
var request = new GetPaymentAccountsRequest();
paymentAccountsService.getPaymentAccounts(request, {'password':'xyz'}, function(err, response) {
  console.log('Account: ' + response.array[0][0]);
  console.log('Account: ' + response.array[0][1]);
});

const {MarketPriceRequest, MarketPriceReply} = require('./grpc_pb.js');
const {PriceClient} = require('./grpc_grpc_web_pb.js');
var priceService = new PriceClient(SERVER_URL);
var request = new MarketPriceRequest();
request.setCurrencycode('USD');
priceService.getMarketPrice(request, {'password':'xyz'}, function(err, response) {
    console.log('Current BTC Price: ' + response.getPrice() + ' USD');
});

const {GetBalancesRequest, GetBalancesReply, BalancesInfo} = require('./grpc_pb.js');
const {WalletsClient} = require('./grpc_grpc_web_pb.js');
var walletsService = new WalletsClient(SERVER_URL);
var request = new GetBalancesRequest();
walletsService.getBalances(request, {'password':'xyz'}, function(err, response) {
    var balances = response.getBalances();
	console.log('BTC Balances: ' + balances.getBtc());
	console.log('BSQ Balances: ' + balances.getBsq());
});



async function streamingStatus() {
    const stream = require('stream');
    const {PollOfferStatusRequest, PollOfferStatusReply, OfferStatus} = require('./grpc_pb.js');
    const {NotificationsClient} = require('./grpc_grpc_web_pb.js');
    await new Promise((resolve, reject) => {
        var notificationsService = new NotificationsClient(SERVER_URL);
        var request = new PollOfferStatusRequest();
        request.setOfferid('ABC-XYZ');
        const statusStream = notificationsService.pollOfferStatus(request, {'password':'xyz'});
        statusStream.on('status', status => {
            console.log('stream status.code: ' + status.code);
        });
        statusStream.on('data', response => {
            console.log(response.toObject());
        });
        statusStream.on('end', function() {
            console.log('stream finished');
        });
        statusStream.on('error', error => {
            console.log(error);
        });
    });
}
streamingStatus().catch(err => console.log(err));
