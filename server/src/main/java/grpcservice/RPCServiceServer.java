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
        mngr.gRPCRecordSubmittedTransaction(RequestHandlerUtils.createTransaction(request));
        responseObserver.onNext(Empty.newBuilder().build());
        responseObserver.onCompleted();
    }

    @Override
    public void getEntireHistory(ReqListEntireHistoryMsg request, StreamObserver<TransactionHistoryMsg> responseObserver) {
        List<Transaction> resp = mngr.gRPCGetEntireHistory(request.getLimit());
        responseObserver.onNext(TransactionHistoryMsg.newBuilder().addAllTransactions(
                resp.stream().map(RequestHandlerUtils::createTransactionMsg).collect(Collectors.toList())).build());
        responseObserver.onCompleted();
    }

}