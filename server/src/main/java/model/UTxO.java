package model;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.util.Objects;

@Embeddable
public class UTxO {


    @JsonProperty("address")
    private String address;
    @JsonProperty("transaction_id")
    private String transactionId;

    public UTxO(String address, String transactionId) {
        this.address = address;
        this.transactionId = transactionId;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UTxO uTxO = (UTxO) o;
        return Objects.equals(address, uTxO.address) && Objects.equals(transactionId, uTxO.transactionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, transactionId);
    }

    @Override
    public String toString() {
        return "UTxO{" +
                "address='" + address + '\'' +
                ", transactionId='" + transactionId + '\'' +
                '}';
    }
}
