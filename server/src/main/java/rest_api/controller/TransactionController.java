package rest_api.controller;

import model.Transaction;
import org.springframework.web.bind.annotation.*;
import rest_api.service.TransactionManager;

import java.util.List;

/**
 * Controller for REST API endpoints
 */
@RestController
public class TransactionController {
    private final TransactionManager transactionManager = new TransactionManager();
    @GetMapping("/transactions")
    public List<Transaction> getAllTransactions(){
        return transactionManager.getAllTransactions();
    }

    @GetMapping("/users/{id}/transactions")
    public List<Transaction> getAllTransactionsForUser(@PathVariable String id){
        return transactionManager.getAllTransactionsForUser(id);
    }

    @PostMapping("/transactions")
    public void createTransaction(@RequestBody Transaction transaction){
        transactionManager.createTransaction(transaction.getSourceAddress(), transaction.getInputs(), transaction.getOutputs());
    }
}
