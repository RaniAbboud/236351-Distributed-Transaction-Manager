package service;

import model.Transaction;
import model.Transfer;
import model.UTxO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TransactionManager {
    public void createTransaction(String sourceAddress, List<UTxO> inputs, List<Transfer> outputs) {
        return;
    }

    public void sendCoins(String sourceAddress, String targetAddress, int coins) {
        return;
    }

    public List<UTxO> getAllUtxosForUser(String address) {
        return null;
    }

    public List<Transaction> getAllTransactionsForUser(String address, int limit) {
        return null;
    }

    public List<Transaction> getAllTransactions(int limit) {
        List<Transaction> transactions = new ArrayList<>();
        transactions.add(new Transaction("FAKE.IP.ADDRESS", null, null));
        return transactions;
    }
}
