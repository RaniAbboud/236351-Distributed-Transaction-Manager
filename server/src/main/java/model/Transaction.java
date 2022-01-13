package model;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import java.util.List;
import java.util.Random;

@Entity
public class Transaction {
    private static Random rand = new Random(); // to generate random ids
    @Id
    private int id;
    @JsonProperty("source_address") private String sourceAddress;
    @OneToMany
    @JsonProperty("inputs") private List<UTxO> inputs;
    @OneToMany
    @JsonProperty("outputs") private List<Transfer> outputs;
    private int timestamp; // we will get this one from ZooKeeper

    public Transaction(String sourceAddress, List<UTxO> inputs, List<Transfer> outputs) {
        this.id = rand.nextInt(Integer.MAX_VALUE); // generate a random id (between 0 and MAXINT)
        this.sourceAddress = sourceAddress;
        this.inputs = inputs;
        this.outputs = outputs;
    }

    public Transaction() {

    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
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

    public int getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }
}
