package grpcservice;

import com.google.protobuf.Empty;
import cs236351.grpcservice.*;
import io.grpc.stub.StreamObserver;
import model.Request;
import model.Response;
import model.Transaction;
import transactionmanager.TransactionManager;

import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class RPCServiceServer extends TransactionManagerRPCServiceGrpc.TransactionManagerRPCServiceImplBase {
    private static final Logger logger = Logger.getLogger(RPCServiceServer.class.getName());

    /** The TransactionManager Handling these services */
    TransactionManager mngr;

    public RPCServiceServer(TransactionManager mngr) {
        this.mngr = mngr;
    }

    /** The services */
    @Override
    public void recordSubmittedTransaction(TransactionMsg request, StreamObserver<Empty> responseObserver) {
        mngr.recordSubmittedTransaction(RequestHandlerUtils.createTransaction(request));
        responseObserver.onNext(Empty.newBuilder().build());
        responseObserver.onCompleted();
    }

    @Override
    public void getEntireHistory(ReqListEntireHistoryMsg request, StreamObserver<TransactionHistoryMsg> responseObserver) {
        List<Transaction> resp = mngr.getEntireHistory(request.getLimit(), request.getReqId());
        responseObserver.onNext(TransactionHistoryMsg.newBuilder().addAllTransactions(
                resp.stream().map(RequestHandlerUtils::createTransactionMsg).collect(Collectors.toList())).build());
        responseObserver.onCompleted();
    }

    @Override
    public void canProcessAtomicTxList(ReqAtomicTxListMsg request, StreamObserver<DecisionMsg> responseObserver) {
        List<Response> responses = mngr.canProcessAtomicTxListStubs(
                request.getTransactionsList().stream().map(trans -> new Request.TransactionRequest(
                        trans.getInputsList().stream().map(RequestHandlerUtils::createUTxO).collect(Collectors.toList()),
                        trans.getOutputsList().stream().map(RequestHandlerUtils::createTransfer).collect(Collectors.toList())
                )).collect(Collectors.toList()), request.getReqId());
        responseObserver.onNext(DecisionMsg.newBuilder().addAllHttpResp(responses.stream()
                .map(resp -> RequestHandlerUtils.createHttpResponse(resp)).collect(Collectors.toList())).build());
        responseObserver.onCompleted();
    }


}