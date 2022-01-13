package service;

import javassist.bytecode.stackmap.TypeData;
import model.Transaction;
import model.Transfer;
import model.UTxO;
import org.springframework.stereotype.Service;
import rest_api.exception.TransactionIllegalException;
import zookeeper.ZooKeeperClient;
import zookeeper.ZooKeeperClientImpl;

import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

@Service
public class TransactionManager {
    private static final Logger LOGGER = Logger.getLogger(TypeData.ClassName.class.getName());
    // ZooKeeper Client
    final private ZooKeeperClient zk;
    /////////////////////////// History ///////////////////////////
    // TransactionId -> Transaction
    final private HashMap<String, Transaction> history = new HashMap<>();
    /////////////////////////// Balances //////////////////////////
    // User's Address -> User's Balance
    final private HashMap<String, List<UTxO>> balances = new HashMap<>();
    ///////////////////////////////////////////////////////////////
    final private List<PendingRequest> pendingRequests = new ArrayList<>();
    private class PendingRequest {
        private Integer mutex;
        private String requestId;
        private int localRequestId; // for Sajy
    }

    public TransactionManager(String zkConnection) throws IOException {
        this.zk = new ZooKeeperClientImpl(zkConnection);
    }

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

    private void validateTransaction(Transaction transaction) {
        // todo: validate inputs are unique
        if (!balances.get(transaction.getSourceAddress()).containsAll(transaction.getInputs())){
            throw new TransactionIllegalException("Invalid inputs.");
        }
        int inputCoins = 0;
        for (UTxO utxo : transaction.getInputs()){
            if (!Objects.equals(utxo.getTargetAddress(), transaction.getSourceAddress())) {
                throw new TransactionIllegalException("Input doesn't match transaction source address.");
            }
            Transaction transactionForUTxO = history.get(utxo.getTransactionId());
            if (transactionForUTxO == null){
                throw new TransactionIllegalException("Invalid UTxO in inputs.");
            }
            if (transactionForUTxO.getTimestamp() >= transaction.getTimestamp()){
                throw new TransactionIllegalException("Invalid UTxO in inputs: UTxO belongs to a transaction with a later timestamp.");
            }
            Optional<Transfer> transfer = transactionForUTxO.
                    getOutputs().
                    stream().
                    filter(t -> Objects.equals(t.getTargetAddress(), utxo.getTargetAddress())).findFirst();
            if (transfer.isEmpty()){
                throw new TransactionIllegalException("Invalid UTxO in inputs.");
            }
            inputCoins += transfer.get().getCoins();
        }
        int outputCoins = transaction.getOutputs().stream().mapToInt(Transfer::getCoins).sum();
        if (outputCoins != inputCoins) {
            throw new TransactionIllegalException("Input coins are not equal to output coins.");
        }
    }
}
