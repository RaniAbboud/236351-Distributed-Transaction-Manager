package model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Transfer {
    @JsonProperty("source_address")
    private String sourceAddress; // todo: remove? it might be useful when sending Transfer objects to other shards
    @JsonProperty("target_address")
    private String targetAddress;
    @JsonProperty("coins")
    private int coins;

    public String getSourceAddress() {
        return sourceAddress;
    }

    public void setSourceAddress(String sourceAddress) {
        this.sourceAddress = sourceAddress;
    }

    public String getTargetAddress() {
        return targetAddress;
    }

    public void setTargetAddress(String targetAddress) {
        this.targetAddress = targetAddress;
    }

    public int getCoins() {
        return coins;
    }

    public void setCoins(int coins) {
        this.coins = coins;
    }
}
