package model;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.persistence.Embeddable;


@Embeddable
public class Transfer {

    @JsonProperty("address")
    private String address;
    @JsonProperty("coins")
    private long coins;

    public Transfer(String address, long coins) {
        this.address = address;
        this.coins = coins;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public long getCoins() {
        return coins;
    }

    public void setCoins(long coins) {
        this.coins = coins;
    }

}
