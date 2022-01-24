package transactionmanager;

import javassist.bytecode.stackmap.TypeData;
import model.Response;
import model.Transaction;
import model.Transfer;
import model.UTxO;
import org.springframework.http.HttpStatus;
import zookeeper.ZooKeeperClient;

import java.math.BigInteger;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static constants.Constants.GENESIS_ADDRESS;
import static constants.Constants.GENESIS_TRANSACTION_ID;

public class TransactionLedger {
    private static final Logger LOGGER = Logger.getLogger(TypeData.ClassName.class.getName());
    final private ZooKeeperClient zk;

    /////////////////////////// History ///////////////////////////
    // Maps each TransactionId to a Transaction object for it.
    // It contains transactions that the sender/receiver of them is handled by this shard.
    final private Map<String, Transaction> history = new HashMap<>();

    /////////////////////////// Balances //////////////////////////
    // Maps each User's Address to a list of unused UTxO's that he can use.
    // These are filled from either this shard when processing or from other shards that
    // processed a transaction giving the user new Transfers.
    final private Map<String, Set<UTxO>> balances = new HashMap<>();

    public TransactionLedger(ZooKeeperClient zk) {
        this.zk = zk;
    }

    public void addGenesisBlockToLedger() {
        List<UTxO> inputs = new ArrayList<>();
        List<Transfer> outputs = new ArrayList<>();
        outputs.add(new Transfer(GENESIS_ADDRESS, 1000000)); // FIXME - Maximum coins
        Transaction genesisTransaction = new Transaction(GENESIS_TRANSACTION_ID, 0, "", inputs, outputs);
        history.put(genesisTransaction.getTransactionId(), genesisTransaction);
        HashSet<UTxO> currSet = new HashSet<>();
        currSet.add(new UTxO(GENESIS_ADDRESS, GENESIS_TRANSACTION_ID));
        balances.put(GENESIS_ADDRESS, currSet);
    }

    synchronized public void performTransaction(Transaction transaction) {
        if(history.containsKey(transaction.getTransactionId())){
            LOGGER.log(Level.FINEST,String.format("Transaction %s is already registered.", transaction.getTransactionId()));
            return;
        }
        // Remove used UTxOs from balances
        for(UTxO inputUTxO : transaction.getInputs()){
            Set<UTxO> balance = balances.get(inputUTxO.getAddress());
            if(balance == null){
                // should never get here
                throw new RuntimeException("Bad transaction inputs. We might have a problem.");
            }
            balance.remove(inputUTxO);
        }
        // Add new UTxOs to users' balances
        List<UTxO> newUTxOs = transaction.getOutputs().stream()
                .filter(t -> zk.isResponsibleForAddress(t.getAddress()))
                .map(transfer -> new UTxO(transfer.getAddress(), transaction.getTransactionId()))
                .collect(Collectors.toList());
        for (UTxO uTxO : newUTxOs) {
            Set<UTxO> balance = balances.computeIfAbsent(uTxO.getAddress(), k -> new HashSet<>());
            balance.add(uTxO);
        }
        // Add transaction to history
        history.put(transaction.getTransactionId(), transaction);
    }

    synchronized public void recordTransaction(Transaction transaction) {
        // Add transaction to history
        if (history.containsKey(transaction.getTransactionId())){
            LOGGER.log(Level.FINEST,String.format("Transaction %s is already registered.", transaction.getTransactionId()));
            return;
        } else {
            history.put(transaction.getTransactionId(), transaction);
        }
        // Add new UTxOs to users' balances
        List<UTxO> newUTxOs = transaction.getOutputs().stream()
                .filter(t -> zk.isResponsibleForAddress(t.getAddress()))
                .map(transfer -> new UTxO(transfer.getAddress(), transaction.getTransactionId()))
                .collect(Collectors.toList());
        for (UTxO uTxO : newUTxOs) {
            Set<UTxO> balance = balances.computeIfAbsent(uTxO.getAddress(), k -> new HashSet<>());
            balance.add(uTxO);
        }
    }

    public List<Transaction> listTransactionsForAddress(String address, int limit) {
        List<Transaction> userTransactions = history.values().stream()
                // get transactions whose source is *address* or any of their target transfers is *address*
                .filter(t -> t.getSourceAddress().equals(address) || t.getOutputs().stream().anyMatch(transfer -> transfer.getAddress().equals(address)))
                .collect(Collectors.toList());
        userTransactions.sort((t1, t2) -> (int) (t1.getTimestamp() - t2.getTimestamp()));
        if ((limit != -1) && (limit < userTransactions.size())) {
            userTransactions = userTransactions.subList(0, limit);
        }
        return userTransactions;
    }

    public Set<UTxO> listUTxOsForAddress(String address) {
        Set<UTxO> ret = balances.get(address);
        if (ret == null) {
            return Set.of();
        }
        return ret;
    }

    public Response canProcessTransaction(Transaction transaction, boolean checkTimestamps) {
        if (history.containsKey(transaction.getSourceAddress())) {
            return new Response(HttpStatus.CONFLICT, String.format("Transaction already exists."));
        }
        if (transaction.getSourceAddress() == null) {
            return new Response(HttpStatus.BAD_REQUEST, String.format("Can't resolve sourceAddress."));
        }
        if (transaction.getInputs().size() != new HashSet<>(transaction.getInputs()).size()) {
            return new Response(HttpStatus.BAD_REQUEST, String.format("Input UTxOs aren't unique."));
        }
        if (transaction.getOutputs().size() != new HashSet<>(transaction.getOutputs()).size()) {
            return new Response(HttpStatus.BAD_REQUEST, String.format("Output transfers aren't unique."));
        }
        if (balances.get(transaction.getSourceAddress()) == null) {
            return new Response(HttpStatus.BAD_REQUEST, String.format("Don't have any UTxOs for address %s", transaction.getSourceAddress()));
        }
        if (!balances.get(transaction.getSourceAddress()).containsAll(transaction.getInputs())){
            return new Response(HttpStatus.BAD_REQUEST, String.format("Don't have all UTxOs used in inputs."));
        }
        if (transaction.getOutputs().stream().map(t -> t.getAddress()).collect(Collectors.toSet()).size() != transaction.getOutputs().size()) {
            return new Response(HttpStatus.BAD_REQUEST, String.format("Transfers are not to unique destinations."));
        }
        BigInteger inputCoins = BigInteger.valueOf(0);
        for (UTxO utxo : transaction.getInputs()){
            Transaction transactionForUTxO = history.get(utxo.getTransactionId());
            if (transactionForUTxO == null) {
                return new Response(HttpStatus.BAD_REQUEST, String.format("Invalid UTxO in inputs: UTxO belongs to a transaction that wasn't processed."));
            }
            if (checkTimestamps) { // Can't always check - in AtomicList for example we don't have timestamps when checking the first time yet.
                if (transactionForUTxO.getTimestamp() >= transaction.getTimestamp()){
                    return new Response(HttpStatus.BAD_REQUEST, String.format("Invalid UTxO in inputs: UTxO belongs to a transaction with a later timestamp."));
                }
            }
            Optional<Transfer> transfer = transactionForUTxO.
                    getOutputs().
                    stream().
                    filter(t -> Objects.equals(t.getAddress(), utxo.getAddress())).findFirst();
            if (transfer.isEmpty()){
                return new Response(HttpStatus.BAD_REQUEST, String.format("Invalid UTxO in inputs. Isn't an output of the given transaction."));
            }
            inputCoins = inputCoins.add(BigInteger.valueOf(transfer.get().getCoins()));
        }
        BigInteger outputCoins = transaction.getOutputs().stream().map(t -> BigInteger.valueOf(t.getCoins())).reduce(BigInteger.ZERO, BigInteger::add);
        if (!outputCoins.equals(inputCoins)) {
            return new Response(HttpStatus.BAD_REQUEST, String.format("Input coins (%d) are not equal to Output coins (%d).", inputCoins, outputCoins));
        }
        return new Response(HttpStatus.CREATED, String.format("Transaction can be processed"));
    }

    public Response.TransactionResp createTransactionForCoinTransfer(String sourceAddress, String targetAddress, long coins) {
        if (coins < 0) {
            return new Response.TransactionResp(HttpStatus.BAD_REQUEST, String.format("Illegal coins value %d", coins), null);
        }
        Set<UTxO> unusedUTxO = this.listUTxOsForAddress(sourceAddress);
        if (unusedUTxO.size() == 0) {
            return new Response.TransactionResp(HttpStatus.BAD_REQUEST, String.format("Source %s has no UTxOs", sourceAddress), null);
        }
        long currSum = 0;
        List<UTxO> inputs = new ArrayList<>();
        for (UTxO currUTxO : unusedUTxO) {
            if (currSum >= coins) {
                break;
            }
            inputs.add(currUTxO);
            currSum += history.get(currUTxO.getTransactionId()).getOutputs().stream().
                    filter(t -> Objects.equals(t.getAddress(), currUTxO.getAddress())).findFirst().get().getCoins();
        }
        if (currSum >= coins) {
            List<Transfer> outputs = new ArrayList<>();
            outputs.add(new Transfer(targetAddress, coins));
            if (currSum - coins > 0) {
                outputs.add(new Transfer(sourceAddress, currSum - coins));
            }
            Transaction transaction = new Transaction(inputs, outputs);
            return new Response.TransactionResp(HttpStatus.OK, String.format("OK"), transaction);
        } else {
            return new Response.TransactionResp(HttpStatus.BAD_REQUEST, String.format("Source %s has only %d coins out of needed %d",
                    sourceAddress, currSum, coins), null);
        }
    }

    public List<Transaction> getEntireHistory(int limit) {
        return history.values().stream()
                .filter(t -> zk.isResponsibleForAddress(t.getSourceAddress()) || t.getTimestamp() == 0)
                .sorted((t1,t2) -> (int) (t1.getTimestamp() - t2.getTimestamp()))
                .limit(limit != -1 ? limit : history.size())
                .collect(Collectors.toList());
    }
}
