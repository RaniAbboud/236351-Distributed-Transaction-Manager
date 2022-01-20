package grpcservice;

import cs236351.grpcservice.*;
import io.grpc.StatusRuntimeException;
import model.*;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class RequestHandlerUtils {
    private static final Logger logger = Logger.getLogger(RequestHandlerUtils.class.getName());

    /** Try call one of the servers and stop when one of them succeeds and return */
    public static <ReqT, RespT> RespT tryCallServer(String requester, List<String> servers, ReqT req, Map<String, Function<ReqT, RespT>> func) {
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

    /** Conversion for Requests from regular to gRPC */
    public static ReqTransactionMsg createReqTransactionMsg(Request.TransactionRequest transaction) {
        return ReqTransactionMsg.newBuilder()
                .addAllInputs(transaction.inputs.stream().map(input ->
                        UTxOMsg.newBuilder()
                                .setTransactionId(input.getTransactionId())
                                .setAddress(input.getAddress())
                                .build()).collect(Collectors.toList()))
                .addAllOutputs(transaction.outputs.stream().map(output ->
                        TransferMsg.newBuilder()
                                .setAddress(output.getAddress())
                                .setCoins(output.getCoins())
                                .build()).collect(Collectors.toList()))
                .build();
    }
    public static ReqCoinTransferMsg createReqCoinTransferMsg(String sourceAddress, String targetAddress, long coins, String reqId) {
        return ReqCoinTransferMsg.newBuilder()
                .setReqId(reqId)
                .setSourceAddress(sourceAddress)
                .setTargetAddress(targetAddress)
                .setCoins(coins)
                .build();
    }
    public static ReqListAddrTransactionsMsg createReqListAddrTransactionsMsg(String sourceAddress, int limit) {
        return ReqListAddrTransactionsMsg.newBuilder()
                .setSourceAddress(sourceAddress)
                .setLimit(limit)
                .build();
    }
    public static ReqListEntireHistoryMsg createReqListEntireHistoryMsg(int limit) {
        return ReqListEntireHistoryMsg.newBuilder()
                .setLimit(limit)
                .build();
    }
    public static ReqAtomicTxListMsg createReqAtomicTxListMsg(List<Request.TransactionRequest> atomicList) {
        return ReqAtomicTxListMsg.newBuilder()
                .addAllTransactions(atomicList.stream().map(req -> createReqTransactionMsg(req)).collect(Collectors.toList()))
                .build();
    }
    public static ReqListAddrUTxOMsg createReqListAddrUTxOMsg(String sourceAddress) {
        return ReqListAddrUTxOMsg.newBuilder()
                .setSourceAddress(sourceAddress)
                .build();
    }

    /** Conversions for responses from gRPC to regular */
    public static Response.TransactionResp createTransactionResp(RespTransactionMsg resp) {
        return new Response.TransactionResp(
            HttpStatus.resolve(resp.getHttpResp().getStatusCode()),
            resp.getHttpResp().getReason(),
            (resp.getTransactionCount() != 0) ? createTransaction(resp.getTransaction(0)) : null
        );
    }
    public static Response.TransactionListResp createTransactionListResp(RespTransactionListMsg resp) {
        return new Response.TransactionListResp(
                HttpStatus.resolve(resp.getHttpResp().getStatusCode()),
                resp.getHttpResp().getReason(),
                (resp.getTransactionsCount() != 0) ? resp.getTransactionsList().stream()
                        .map(RequestHandlerUtils::createTransaction).collect(Collectors.toList()) : null
        );
    }
    public static Response.UnusedUTxOListResp createUnusedUTxOListResp(RespUnusedUTxOListMsg resp) {
        return new Response.UnusedUTxOListResp(
                HttpStatus.resolve(resp.getHttpResp().getStatusCode()),
                resp.getHttpResp().getReason(),
                (resp.getUtxosCount() != 0) ? resp.getUtxosList().stream()
                        .map(RequestHandlerUtils::createUTxO).collect(Collectors.toList()) : null
//                ,(resp.getTransactionsCount() != 0) ? resp.getTransactionsList().stream()
//                        .map(RequestHandlerUtils::createTransaction).collect(Collectors.toList()) : null
        );
    }

    /** General Conversions from gRPC to regular */
    public static UTxO createUTxO(UTxOMsg utxoMsg) {
        return new UTxO(utxoMsg.getAddress(), utxoMsg.getTransactionId());
    }
    public static Transfer createTransfer(TransferMsg transMsg) {
        return new Transfer(transMsg.getAddress(), transMsg.getCoins());
    }
    public static Transaction createTransaction(TransactionMsg transMsg) {
        return new Transaction(
            transMsg.getTransactionId(),
            transMsg.getTimestamp(),
            transMsg.getSourceAddress(),
            transMsg.getInputsList().stream().map(RequestHandlerUtils::createUTxO).collect(Collectors.toList()),
            transMsg.getOutputsList().stream().map(RequestHandlerUtils::createTransfer).collect(Collectors.toList())
        );
    }

    /** Conversions for responses from regular to gRPC */
    public static HttpResponse createHttpResponse(Response resp) {
        return HttpResponse.newBuilder()
                .setStatusCode(resp.statusCode.value())
                .setReason(resp.reason)
                .build();
    }
    public static RespTransactionMsg createRespTransactionMsg (Response.TransactionResp resp) {
        RespTransactionMsg.Builder builder = RespTransactionMsg.newBuilder().setHttpResp(createHttpResponse(resp));
        if (resp.transaction != null) {
            builder.addTransaction(createTransactionMsg(resp.transaction));
        }
        return builder.build();
    }
    public static RespTransactionListMsg createRespTransactionListMsg(Response.TransactionListResp resp) {
        RespTransactionListMsg.Builder builder = RespTransactionListMsg.newBuilder().setHttpResp(createHttpResponse(resp));
        if (resp.transactionsList != null) {
            builder.addAllTransactions(resp.transactionsList.stream().map(RequestHandlerUtils::createTransactionMsg).collect(Collectors.toList()));
        }
        return builder.build();
    }
    public static RespUnusedUTxOListMsg createUnusedUTxOListResp(Response.UnusedUTxOListResp resp) {
        RespUnusedUTxOListMsg.Builder builder = RespUnusedUTxOListMsg.newBuilder().setHttpResp(createHttpResponse(resp));
        // if (resp.transactionsList != null) {
        //     builder.addAllTransactions(resp.transactionsList.stream().map(RequestHandlerUtils::createTransactionMsg).collect(Collectors.toList()));
        // }
        if (resp.unusedUtxoList != null) {
            builder.addAllUtxos(resp.unusedUtxoList.stream().map(RequestHandlerUtils::createUTxOMsg).collect(Collectors.toList()));
        }
        return builder.build();
    }

    /** General Conversions from regular to gRPC */
    public static UTxOMsg createUTxOMsg(UTxO utxo) {
        return UTxOMsg.newBuilder()
                .setTransactionId(utxo.getTransactionId())
                .setAddress(utxo.getAddress())
                .build();
    }
    public static TransferMsg createTransferMsg(Transfer transfer) {
        return TransferMsg.newBuilder()
                .setCoins(transfer.getCoins())
                .setAddress(transfer.getAddress())
                .build();
    }
    public static TransactionMsg createTransactionMsg(Transaction transaction) {
        return TransactionMsg.newBuilder()
                .setTransactionId(transaction.getTransactionId())
                .setTimestamp(transaction.getTimestamp())
                .setSourceAddress(transaction.getSourceAddress())
                .addAllInputs(transaction.getInputs().stream().map(RequestHandlerUtils::createUTxOMsg).collect(Collectors.toList()))
                .addAllOutputs(transaction.getOutputs().stream().map(RequestHandlerUtils::createTransferMsg).collect(Collectors.toList()))
                .build();
    }

}
