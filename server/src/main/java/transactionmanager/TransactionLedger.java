package transactionmanager;

import javassist.bytecode.stackmap.TypeData;
import model.Transaction;
import model.Transfer;
import model.UTxO;
import rest_api.exception.TransactionIllegalException;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class TransactionLedger {
    private static final Logger LOGGER = Logger.getLogger(TypeData.ClassName.class.getName());
    /////////////////////////// History ///////////////////////////
    // Maps each TransactionId to a Transaction object for it.
    // It contains transactions that the sender/receiver of them is handled by this shard.
    final private Map<String, Transaction> history = new HashMap<>();

    /////////////////////////// Balances //////////////////////////
    // Maps each User's Address to a list of unused UTxO's that he can use.
    // These are filled from either this shard when processing or from other shards that
    // processed a transaction giving the user new Transfers.
    final private Map<String, Set<UTxO>> balances = new HashMap<>();

    synchronized public void registerTransaction(Transaction transaction) {
        if(history.containsKey(transaction.getTransactionId())){
            LOGGER.log(Level.FINEST,String.format("Transaction %s is already registered.", transaction.getTransactionId()));
            return;
        }
        // Remove used UTxOs from balances
        for(UTxO inputUTxO : transaction.getInputs()){
            Set<UTxO> balance = balances.get(inputUTxO.getAddress());
            if(balance == null){
                // should never get here
                throw new TransactionIllegalException("Bad transaction inputs. We might have a problem.");
            }
            balance.remove(inputUTxO);
        }
        // Add new UTxOs to users' balances
        List<UTxO> newUTxOs = transaction.getOutputs().stream().map(transfer -> new UTxO(transfer.getAddress(), transaction.getTransactionId())).collect(Collectors.toList());
        for (UTxO uTxO : newUTxOs) {
            Set<UTxO> balance = balances.computeIfAbsent(uTxO.getAddress(), k -> new HashSet<>());
            balance.add(uTxO);
        }
        // Add transaction to history
        history.put(transaction.getTransactionId(), transaction);
    }

    public List<Transaction> listTransactionsForAddress(String address, int limit) {
        List<Transaction> userTransactions = history.values().stream()
                // get transactions whose source is *address* or any of their target transfers is *address*
                .filter(t -> t.getSourceAddress().equals(address) || t.getOutputs().stream().anyMatch(transfer -> transfer.getAddress().equals(address)))
                .collect(Collectors.toList());
        userTransactions.sort((t1, t2) -> (int) (t1.getTimestamp() - t2.getTimestamp()));
        return userTransactions.subList(0, limit);
    }

    public Set<UTxO> listUTxOsForAddress(String address) {
        return balances.get(address);
    }

    public void validateTransaction(Transaction transaction) {
        // todo: validate inputs are unique
        if (!balances.get(transaction.getSourceAddress()).containsAll(transaction.getInputs())) {
            throw new TransactionIllegalException("Invalid inputs.");
        }
        long inputCoins = 0;
        for (UTxO utxo : transaction.getInputs()) {
            if (!Objects.equals(utxo.getAddress(), transaction.getSourceAddress())) {
                throw new TransactionIllegalException("Input doesn't match transaction source address.");
            }
            Transaction transactionForUTxO = history.get(utxo.getTransactionId());
            if (transactionForUTxO == null) {
                throw new TransactionIllegalException("Invalid UTxO in inputs.");
            }
            if (transactionForUTxO.getTimestamp() >= transaction.getTimestamp()) {
                throw new TransactionIllegalException("Invalid UTxO in inputs: UTxO belongs to a transaction with a later timestamp.");
            }
            Optional<Transfer> transfer = transactionForUTxO.
                    getOutputs().
                    stream().
                    filter(t -> Objects.equals(t.getAddress(), utxo.getAddress())).findFirst();
            if (transfer.isEmpty()) {
                throw new TransactionIllegalException("Invalid UTxO in inputs.");
            }
            inputCoins += transfer.get().getCoins();
        }
        long outputCoins = transaction.getOutputs().stream().mapToLong(Transfer::getCoins).sum();
        if (outputCoins != inputCoins) {
            throw new TransactionIllegalException("Input coins are not equal to output coins.");
        }
    }

}
