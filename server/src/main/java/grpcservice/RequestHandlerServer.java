package grpcservice;

import cs236351.grpcservice.*;
import io.grpc.stub.StreamObserver;
import model.Request;
import transactionmanager.TransactionManager;

import java.util.logging.Logger;
import java.util.stream.Collectors;

public class RequestHandlerServer extends TransactionManagerRequestHandlerServiceGrpc.TransactionManagerRequestHandlerServiceImplBase {
    private static final Logger logger = Logger.getLogger(RequestHandlerServer.class.getName());

    /** The TransactionManager Handling these services */
    TransactionManager mngr;

    public RequestHandlerServer(TransactionManager mngr) {
        this.mngr = mngr;
    }

    /** The services */
    @Override
    public void handleTransaction(ReqTransactionMsg request, StreamObserver<RespTransactionMsg> responseObserver) {
        responseObserver.onNext(RequestHandlerUtils.createRespTransactionMsg(mngr.handleTransaction(new Request.TransactionRequest(
                request.getInputsList().stream().map(RequestHandlerUtils::createUTxO).collect(Collectors.toList()),
                request.getOutputsList().stream().map(RequestHandlerUtils::createTransfer).collect(Collectors.toList()))
        )));
        responseObserver.onCompleted();
    }

    @Override
    public void handleCoinTransfer(ReqCoinTransferMsg request, StreamObserver<RespTransactionMsg> responseObserver) {
        responseObserver.onNext(RequestHandlerUtils.createRespTransactionMsg(mngr.handleCoinTransfer(
                request.getSourceAddress(),
                request.getTargetAddress(),
                request.getCoins(),
                request.getReqId()
        )));
        responseObserver.onCompleted();
    }

    @Override
    public void handleListAddrUTxO(ReqListAddrUTxOMsg request, StreamObserver<RespUnusedUTxOListMsg> responseObserver) {
        responseObserver.onNext(RequestHandlerUtils.createUnusedUTxOListResp(mngr.handleListAddrUTxO(
                request.getSourceAddress()
        )));
        responseObserver.onCompleted();
    }

    @Override
    public void handleListAddrTransactions(ReqListAddrTransactionsMsg request, StreamObserver<RespTransactionListMsg> responseObserver) {
        responseObserver.onNext(RequestHandlerUtils.createRespTransactionListMsg((mngr.handleListAddrTransactions(
                request.getSourceAddress(),
                request.getLimit()
        ))));
        responseObserver.onCompleted();
    }

    @Override
    public void handleListEntireHistory(ReqListEntireHistoryMsg request, StreamObserver<RespTransactionListMsg> responseObserver) {
        responseObserver.onNext(RequestHandlerUtils.createRespTransactionListMsg((mngr.handleListEntireHistory(
                request.getLimit()
        ))));
        responseObserver.onCompleted();
    }

    @Override
    public void handleAtomicTxList(ReqAtomicTxListMsg request, StreamObserver<RespTransactionListMsg> responseObserver) {
        responseObserver.onNext(RequestHandlerUtils.createRespTransactionListMsg((mngr.handleAtomicTxList(
                request.getTransactionsList().stream().map(trans -> new Request.TransactionRequest(
                        trans.getInputsList().stream().map(RequestHandlerUtils::createUTxO).collect(Collectors.toList()),
                        trans.getOutputsList().stream().map(RequestHandlerUtils::createTransfer).collect(Collectors.toList())
                        )).collect(Collectors.toList())
        ))));
        responseObserver.onCompleted();
    }

}