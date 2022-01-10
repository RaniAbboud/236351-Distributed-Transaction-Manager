package model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class UTxO {
    @JsonProperty("transaction_id")
    private int transactionId;
    @JsonProperty("target_address")
    private String targetAddress;

    public int getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(int transactionId) {
        this.transactionId = transactionId;
    }

    public String getTargetAddress() {
        return targetAddress;
    }

    public void setTargetAddress(String targetAddress) {
        this.targetAddress = targetAddress;
    }
}
