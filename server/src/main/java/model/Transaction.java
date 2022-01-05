package model;

import java.util.Random;

public class Transaction {
    private static Random rand = new Random(); // to generate random ids

    private int id;
    private String sourceAddress;
    private UTxO[] inputs;
    private Transfer[] outputs;
    private int timestamp; // we will get this one from ZooKeeper

    public Transaction(String sourceAddress, UTxO[] inputs, Transfer[] outputs) {
        this.id = rand.nextInt(Integer.MAX_VALUE); // generate a random id (between 0 and MAXINT)
        this.sourceAddress = sourceAddress;
        this.inputs = inputs;
        this.outputs = outputs;
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

    public UTxO[] getInputs() {
        return inputs;
    }

    public void setInputs(UTxO[] inputs) {
        this.inputs = inputs;
    }

    public Transfer[] getOutputs() {
        return outputs;
    }

    public void setOutputs(Transfer[] outputs) {
        this.outputs = outputs;
    }

    public int getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }
}
