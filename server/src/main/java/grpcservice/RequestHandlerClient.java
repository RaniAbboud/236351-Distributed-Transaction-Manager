package grpcservice;

import cs236351.grpcservice.*;
import cs236351.grpcservice.TransactionManagerRequestHandlerServiceGrpc.TransactionManagerRequestHandlerServiceBlockingStub;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import model.Request;
import model.Response;
import model.Transfer;
import model.UTxO;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.function.Function;

public class RequestHandlerClient {
    private static final Logger logger = Logger.getLogger(RequestHandlerClient.class.getName());

    /** Need a function for each type for each server in the system */
    private Map<String, Function<ReqTransactionMsg, RespTransactionMsg>> handleTransactionStubs = new HashMap<>();
    private Map<String, Function<ReqCoinTransferMsg, RespTransactionMsg>> handleCoinTransferStubs = new HashMap<>();
    private Map<String, Function<ReqListAddrUTxOMsg, RespUnusedUTxOListMsg>> handleListAddrUTxOStubs = new HashMap<>();
    private Map<String, Function<ReqListAddrTransactionsMsg, RespTransactionListMsg>> handleListAddrTransactionsStubs = new HashMap<>();
    private Map<String, Function<ReqListEntireHistoryMsg, RespTransactionListMsg>> handleListEntireHistoryStubs = new HashMap<>();
    private Map<String, Function<ReqAtomicTxListMsg, RespTransactionListMsg>> handleAtomicTxListStubs = new HashMap<>();

    /**  Needs a mapping of all servers in the system and their addresses so we can create a stub */
    public void setup(Map<String, String> serversAddresses) {
        for (Map.Entry<String,String> entry : serversAddresses.entrySet()) {
            logger.log(Level.INFO, String.format("Creating a blocking stub to server %s at address %s",
                    entry.getKey(), entry.getValue()));
            ManagedChannel channel = ManagedChannelBuilder.forTarget(entry.getValue()).usePlaintext().build();
            TransactionManagerRequestHandlerServiceBlockingStub stub = TransactionManagerRequestHandlerServiceGrpc.newBlockingStub(channel);
            this.handleTransactionStubs.put(entry.getKey(), stub::handleTransaction);
            this.handleCoinTransferStubs.put(entry.getKey(), stub::handleCoinTransfer);
            this.handleListAddrUTxOStubs.put(entry.getKey(), stub::handleListAddrUTxO);
            this.handleListAddrTransactionsStubs.put(entry.getKey(), stub::handleListAddrTransactions);
            this.handleListEntireHistoryStubs.put(entry.getKey(), stub::handleListEntireHistory);
            this.handleAtomicTxListStubs.put(entry.getKey(), stub::handleAtomicTxList);
        }
    }

    /** Delegate Requests to Other Servers
     *  Requests receive in addition to the regular inputs a list of servers to try sending the transaction to */
    private <ReqT, RespT> RespT delegateRequest(String requester, List<String> servers, ReqT req, Map<String, Function<ReqT, RespT>> func) {
        logger.log(Level.INFO, String.format("%s: Sending %s to servers %s", requester, req.toString(), servers.toString()));
        for (String currServer : servers) {
            try {
                logger.log(Level.INFO, String.format("%s: Trying server %s", requester, currServer));
                RespT resp = func.get(currServer).apply(req);
                logger.log(Level.INFO, String.format("%s: RPC to %s succeeded with %s", requester, currServer, resp.toString()));
                return resp;
            } catch (StatusRuntimeException e) {
                logger.log(Level.WARNING, String.format("%s: RPC to %s failed, will retry if any left", requester, currServer));
            }
        }
        logger.log(Level.SEVERE, String.format("%s: Sending %s failed on all servers: %s !!", requester, req.toString(), servers.toString()));
        return null;
    }

    public Response.TransactionResp delegateHandleTransaction(List<String> servers, Request.TransactionRequest transaction, String reqId) {
        ReqTransactionMsg req = RequestHandlerUtils.createReqTransactionMsg(transaction, reqId);
        RespTransactionMsg resp = delegateRequest("delegateHandleTransaction", servers, req, this.handleTransactionStubs);
        return RequestHandlerUtils.createTransactionResp(resp);
    }

    public Response.TransactionResp delegateHandleCoinTransfer(List<String> servers, String sourceAddress, String targetAddress, long coins, String reqId) {
        ReqCoinTransferMsg req = RequestHandlerUtils.createReqCoinTransferMsg(sourceAddress, targetAddress, coins, reqId);
        RespTransactionMsg resp = delegateRequest("delegateHandleCoinTransfer", servers, req, this.handleCoinTransferStubs);
        return RequestHandlerUtils.createTransactionResp(resp);
    }

    public Response.UnusedUTxOListResp delegateHandleListAddrUTxO(List<String> servers, String sourceAddress) {
        ReqListAddrUTxOMsg req = RequestHandlerUtils.createReqListAddrUTxOMsg(sourceAddress);
        RespUnusedUTxOListMsg resp = delegateRequest("delegateHandleListAddrUTxO", servers, req, this.handleListAddrUTxOStubs);
        return RequestHandlerUtils.createUnusedUTxOListResp(resp);
    }

    public Response.TransactionListResp delegateHandleListAddrTransactions(List<String> servers, String sourceAddress, int limit) {
        ReqListAddrTransactionsMsg req = RequestHandlerUtils.createReqListAddrTransactionsMsg(sourceAddress, limit);
        RespTransactionListMsg resp = delegateRequest("delegateHandleListAddrTransactions", servers, req, this.handleListAddrTransactionsStubs);
        return RequestHandlerUtils.createTransactionListResp(resp);
    }

    public Response.TransactionListResp delegateHandleListEntireHistory(List<String> servers, int limit, String reqId) {
        ReqListEntireHistoryMsg req = RequestHandlerUtils.createReqListEntireHistoryMsg(limit, reqId);
        RespTransactionListMsg resp = delegateRequest("delegateHandleListEntireHistory", servers, req, this.handleListEntireHistoryStubs);
        return RequestHandlerUtils.createTransactionListResp(resp);
    }

    public Response.TransactionListResp delegateHandleAtomicTxList(List<String> servers, List<Request.TransactionRequest> atomicList, String reqId) {
        ReqAtomicTxListMsg req = RequestHandlerUtils.createReqAtomicTxListMsg(atomicList, reqId);
        RespTransactionListMsg resp = delegateRequest("delegateHandleAtomicTxList", servers, req, this.handleAtomicTxListStubs);
        return RequestHandlerUtils.createTransactionListResp(resp);
    }

}
