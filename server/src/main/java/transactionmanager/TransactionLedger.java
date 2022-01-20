package transactionmanager;

import model.Transaction;
import model.Transfer;
import model.UTxO;
import rest_api.exception.GeneralException;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class TransactionLedger {

    /////////////////////////// History ///////////////////////////
    // Maps each TransactionId to a Transaction object for it.
    // It contains transactions that the sender/receiver of them is handled by this shard.
    final private HashMap<String, Transaction> history = new HashMap<>();

    /////////////////////////// Balances //////////////////////////
    // Maps each User's Address to a list of unused UTxO's that he can use.
    // These are filled from either this shard when processing or from other shards that
    // processed a transaction giving the user new Transfers.
    final private HashMap<String, List<UTxO>> balances = new HashMap<>();


    public void validateTransaction(Transaction transaction) {
        // todo: validate inputs are unique
        if (!balances.get(transaction.getSourceAddress()).containsAll(transaction.getInputs())){
            // throw new GeneralException("Invalid inputs.");
        }
        long inputCoins = 0;
        for (UTxO utxo : transaction.getInputs()){
            if (!Objects.equals(utxo.getAddress(), transaction.getSourceAddress())) {
                // throw new GeneralException("Input doesn't match transaction source address.");
            }
            Transaction transactionForUTxO = history.get(utxo.getTransactionId());
            if (transactionForUTxO == null){
                // throw new GeneralException("Invalid UTxO in inputs.");
            }
            if (transactionForUTxO.getTimestamp() >= transaction.getTimestamp()){
                // throw new GeneralException("Invalid UTxO in inputs: UTxO belongs to a transaction with a later timestamp.");
            }
            Optional<Transfer> transfer = transactionForUTxO.
                    getOutputs().
                    stream().
                    filter(t -> Objects.equals(t.getAddress(), utxo.getAddress())).findFirst();
            if (transfer.isEmpty()){
                // throw new GeneralException("Invalid UTxO in inputs.");
            }
            inputCoins += transfer.get().getCoins();
        }
        long outputCoins = transaction.getOutputs().stream().mapToLong(Transfer::getCoins).sum();
        if (outputCoins != inputCoins) {
            // throw new GeneralException("Input coins are not equal to output coins.");
        }
    }

}
