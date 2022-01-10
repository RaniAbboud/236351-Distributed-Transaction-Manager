package rest_api.service;

import model.Transaction;
import model.Transfer;
import model.UTxO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TransactionManager {
    public void createTransaction(String sourceAddress, UTxO[] inputs, Transfer[] outputs) {
        return;
    }

    public List<Transaction> getAllTransactionsForUser(String id) {
        return null;
    }

    public List<Transaction> getAllTransactions() {
        return null;
    }
}
