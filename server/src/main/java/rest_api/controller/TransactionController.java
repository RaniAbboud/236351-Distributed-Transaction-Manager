package rest_api.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import javassist.bytecode.stackmap.TypeData;
import model.Transaction;
import model.UTxO;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.web.bind.annotation.*;
import service.TransactionManager;

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
            this.transactionManager = new TransactionManager("");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE,"Failed to initialize TransactionManager", e);
        }
    }

    private TransactionManager transactionManager = null;
    private static final String limitParamDefault = "100000";

    @GetMapping("/transactions")
    public List<Transaction> getAllTransactions(@RequestParam(required = false, defaultValue = limitParamDefault) Integer limit) {
        return transactionManager.getAllTransactions(limit);
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
    public void createTransaction(@RequestBody List<Transaction> transactions) {
        if (transactions.size() == 1) {
            Transaction transaction = transactions.get(0);
            transactionManager.createTransaction(transaction.getSourceAddress(), transaction.getInputs(), transaction.getOutputs());
        }
    }

    @PostMapping("/send_coins")
    public void sendCoins(@RequestBody SendCoinsRequestBody body) {
        transactionManager.sendCoins(body.sourceAddress, body.targetAddress, body.coins);
    }

    @GetMapping("/users/{address}/transactions")
    public List<Transaction> getAllTransactionsForUser(@PathVariable String address, @RequestParam(required = false, defaultValue = limitParamDefault) int limit) {
        return transactionManager.getAllTransactionsForUser(address, limit);
    }

    @GetMapping("/users/{address}/utxos")
    public List<UTxO> getAllUtxosForUser(@PathVariable String address) {
        return transactionManager.getAllUtxosForUser(address);
    }

    private static class SendCoinsRequestBody {
        public int coins;
        @JsonProperty("target_address")
        public String targetAddress;
        @JsonProperty("source_address")
        public String sourceAddress;
    }
}
