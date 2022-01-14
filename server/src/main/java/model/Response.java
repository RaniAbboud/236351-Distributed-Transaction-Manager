package model;

import org.springframework.http.HttpStatus;

import java.util.List;

public class Response {

    public HttpStatus statusCode;
    public String reason;

    public Response(HttpStatus statusCode, String reason) {
        this.statusCode = statusCode;
        this.reason = reason;
    }

    public static class TransactionResp extends Response {
        public Transaction transaction;
        public TransactionResp(HttpStatus statusCode, String reason, Transaction transaction) {
            super(statusCode, reason);
            this.transaction = transaction;
        }
    }

    public static class TransactionListResp extends Response {
        public List<Transaction> transactionsList;
        public TransactionListResp(HttpStatus statusCode, String reason, List<Transaction> transactionsList) {
            super(statusCode, reason);
            this.transactionsList = transactionsList;
        }
    }

    public static class UnusedUTxOListResp extends TransactionListResp {
        public List<UTxO> unusedUtxoList;
        public UnusedUTxOListResp(HttpStatus statusCode, String reason, List<UTxO> unusedUtxoList, List<Transaction> transactionsList) {
            super(statusCode, reason, transactionsList);
            this.unusedUtxoList = unusedUtxoList;
        }
    }

}