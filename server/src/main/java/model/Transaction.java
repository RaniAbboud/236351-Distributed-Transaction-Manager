package model;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.persistence.Embeddable;
import javax.persistence.OneToMany;
import javax.xml.bind.DatatypeConverter;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;
import java.security.MessageDigest;
import java.util.Objects;

@Embeddable
public class Transaction {

    @JsonProperty("transaction_id")
    private String transactionId;
    @JsonProperty("timestamp")
    private long timestamp;
    @JsonProperty("source_address")
    private String sourceAddress;
    @OneToMany
    @JsonProperty("inputs")
    private List<UTxO> inputs;
    @OneToMany
    @JsonProperty("outputs")
    private List<Transfer> outputs;

    public Transaction(String transactionId, long timestamp, String sourceAddress, List<UTxO> inputs, List<Transfer> outputs) {
        this.timestamp = timestamp;
        this.inputs = (inputs != null) ? inputs : Collections.emptyList();
        this.outputs = (outputs != null) ? outputs : Collections.emptyList();
        this.sourceAddress = (sourceAddress != null) ? sourceAddress : computeSourceAddress(this.inputs);
        this.transactionId = (transactionId != null) ? transactionId : computeTransactionId(this.sourceAddress, this.inputs, this.outputs);
    }

    public Transaction(String transactionId, List<UTxO> inputs, List<Transfer> outputs) {
        this(transactionId, -1, null, inputs, outputs);
    }

    public Transaction(List<UTxO> inputs, List<Transfer> outputs) {
        this(null, inputs, outputs);
    }


    public String getSourceAddress() {
        return sourceAddress;
    }

    public void setSourceAddress(String sourceAddress) {
        this.sourceAddress = sourceAddress;
    }

    public List<UTxO> getInputs() {
        return inputs;
    }

    public void setInputs(List<UTxO> inputs) {
        this.inputs = inputs;
    }

    public List<Transfer> getOutputs() {
        return outputs;
    }

    public void setOutputs(List<Transfer> outputs) {
        this.outputs = outputs;
    }

    public long getTimestamp() { return timestamp; }

    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getTransactionId() { return transactionId; }

    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    /** transactionId is an MD5 hash of sourceAddress, inputs and outputs */
    public static String computeTransactionId(String sourceAddress, List<UTxO> inputs, List<Transfer> outputs) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("MD5");
            md.update(sourceAddress.getBytes());
            for (UTxO in : inputs) {
                md.update(in.getAddress().getBytes());
                md.update(in.getTransactionId().getBytes());
            }
            for (Transfer out : outputs) {
                md.update(out.getAddress().getBytes());
                md.update(Long.toUnsignedString(out.getCoins()).getBytes());
            }
            return DatatypeConverter.printHexBinary(md.digest()).toUpperCase();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }

    /** Returns the sourceAddress of all UTxO's if unique and existing, otherwise null */
    public static String computeSourceAddress(List<UTxO> inputs) {
        String temp = null;
        for (UTxO utxo : inputs) {
            if (temp == null) {
                temp = utxo.getAddress();
            } else if (!temp.equals(utxo.getAddress())) {
                return null;
            }
        }
        return temp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Transaction that = (Transaction) o;
        return timestamp == that.timestamp && Objects.equals(transactionId, that.transactionId) && Objects.equals(sourceAddress, that.sourceAddress) && Objects.equals(inputs, that.inputs) && Objects.equals(outputs, that.outputs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(transactionId, timestamp, sourceAddress, inputs, outputs);
    }
}
