package grpcservice;

import com.google.protobuf.Empty;
import cs236351.grpcservice.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import cs236351.grpcservice.TransactionManagerRPCServiceGrpc.TransactionManagerRPCServiceBlockingStub;
import cs236351.grpcservice.TransactionManagerRPCServiceGrpc;
import io.grpc.StatusRuntimeException;
import model.Request;
import model.Response;
import model.Transaction;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static grpcservice.RequestHandlerUtils.tryCallServer;

public class RPCServiceClient {
    private static final Logger logger = Logger.getLogger(grpcservice.RequestHandlerClient.class.getName());

    /** Need a function for each type for each server in the system */
    private Map<String, Function<TransactionMsg, Empty>> recordSubmittedTransactionStubs = new HashMap<>();
    private Map<String, Function<ReqListEntireHistoryMsg, TransactionHistoryMsg>> getEntireHistoryStubs = new HashMap<>();

    /**  Needs a mapping of all servers in the system and their addresses, so we can create a stub */
    public void setup(Map<String, String> serversAddresses) {
        for (Map.Entry<String,String> entry : serversAddresses.entrySet()) {
            logger.log(Level.INFO, String.format("Creating a blocking stub to server %s at address %s",
                    entry.getKey(), entry.getValue()));
            ManagedChannel channel = ManagedChannelBuilder.forTarget(entry.getValue()).usePlaintext().build();
            TransactionManagerRPCServiceBlockingStub stub = TransactionManagerRPCServiceGrpc.newBlockingStub(channel);
            this.recordSubmittedTransactionStubs.put(entry.getKey(), stub::recordSubmittedTransaction);
            this.getEntireHistoryStubs.put(entry.getKey(), stub::getEntireHistory);
        }
    }

    /** Records a submitted transaction, called on each server of the list */
    public void recordSubmittedTransaction(List<String> servers, Transaction transaction) {
        int cnt = 0;
        TransactionMsg msg = RequestHandlerUtils.createTransactionMsg(transaction);
        logger.log(Level.FINEST, String.format("recordSubmittedTransaction: Sending %s to each of the servers %s", msg.toString(), servers.toString()));
        for (String currServer : servers) {
            try {
                logger.log(Level.FINEST, String.format("recordSubmittedTransaction: Trying server %s", currServer));
                Empty resp = this.recordSubmittedTransactionStubs.get(currServer).apply(msg);
                logger.log(Level.FINEST, String.format("recordSubmittedTransaction: RPC to %s succeeded", currServer));
                cnt++;
            } catch (StatusRuntimeException e) {
                logger.log(Level.FINEST, String.format("recordSubmittedTransaction: RPC to %s failed", currServer));
            }
        }
        logger.log(Level.FINEST, String.format("recordSubmittedTransaction: RPC succeeded to %d servers", cnt));
    }

    /** Gets the entire history from one of the servers in the list, stops when one succeeds */
    public List<Transaction> getEntireHistory(List<String> servers, int limit) {
        ReqListEntireHistoryMsg req = RequestHandlerUtils.createReqListEntireHistoryMsg(limit);
        TransactionHistoryMsg resp = tryCallServer("getEntireHistory", servers, req, this.getEntireHistoryStubs);
        return resp.getTransactionsList().stream().map(RequestHandlerUtils::createTransaction).collect(Collectors.toList());
    }

}