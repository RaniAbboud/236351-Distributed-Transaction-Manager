package rest_api.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import javassist.bytecode.stackmap.TypeData;
import model.Request;
import model.Response;
import model.Transaction;
import model.UTxO;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.web.bind.annotation.*;
import rest_api.exception.BadRequestException;
import rest_api.exception.ConflictException;
import rest_api.exception.NotFoundException;
import transactionmanager.TransactionManager;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for REST API endpoints
 */
@RestController
@EntityScan("model")
public class TransactionController {
    private static final Logger LOGGER = Logger.getLogger(TypeData.ClassName.class.getName());

    TransactionController(){
        try {
            this.transactionManager = new TransactionManager();
            this.transactionManager.setup();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE,"Failed to initialize TransactionManager", e);
        }
    }

    private TransactionManager transactionManager = null;
    private static final String limitParamDefault = "-1";

    /** Handle error responses */
    public void handleErrors(Response resp) {
        switch (resp.statusCode) {
            case OK: case CREATED: break;
            case BAD_REQUEST: throw new BadRequestException(resp.reason);
            case CONFLICT: throw new ConflictException(resp.reason);
            case NOT_FOUND: throw new NotFoundException(resp.reason);
            default: throw new RuntimeException(String.format("Unknown exception type %s", resp.statusCode.toString()));
        }
    }

    /**
     * The POST /transactions endpoint allows submitting either:
     * 1. a single transaction.
     * 2. an atomic list of multiple transactions.
     * Therefore, the endpoint receives a body containing an array of transactions. This array will contain a single
     * transaction for the case that the user wishes to submit a singe transaction.
     *
     * @param transactions
     */
    @PostMapping("/transactions")
    public @ResponseBody Transaction createTransaction(@RequestBody List<Request.TransactionRequest> transactions) {
        if (transactions.size() == 1) {
            Request.TransactionRequest transactionReq = transactions.get(0);
            Response.TransactionResp resp = transactionManager.handleTransaction(transactionReq);
            handleErrors(resp);
            return resp.transaction;
        } else {
            // FIXME
        }
        return null;
    }

    @PostMapping("/send_coins")
    public @ResponseBody Transaction sendCoins(@RequestBody SendCoinsRequestBody body) {
        Response.TransactionResp resp = transactionManager.handleCoinTransfer(body.sourceAddress, body.targetAddress, body.coins, body.reqId);
        handleErrors(resp);
        return resp.transaction;
    }

    @GetMapping("/users/{address}/transactions")
    public @ResponseBody List<Transaction> getAllTransactionsForUser(@PathVariable String address,
                        @RequestParam(required = false, defaultValue = limitParamDefault) int limit) {
        // FIXME
        // return transactionManager.getAllTransactionsForUser(address, limit);
        return null;
    }

    @GetMapping("/users/{address}/utxos")
    public @ResponseBody List<UTxO> getAllUtxosForUser(@PathVariable String address) {
        // FIXME
        // return transactionManager.getAllUtxosForUser(address);
        return null;
    }

    @GetMapping("/transactions")
    public @ResponseBody List<Transaction> getAllTransactions(@RequestParam(required = false, defaultValue = limitParamDefault) int limit) {
        // FIXME
        // return transactionManager.getAllTransactions(limit);
        return null;
    }

    private static class SendCoinsRequestBody {
        @JsonProperty("request_id")
        public String reqId;
        @JsonProperty("source_address")
        public String sourceAddress;
        @JsonProperty("target_address")
        public String targetAddress;
        @JsonProperty("coins")
        public long coins;
    }

}
