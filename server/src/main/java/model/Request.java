package model;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.persistence.Embeddable;
import javax.persistence.OneToMany;
import java.util.List;

abstract public class Request {

    @Embeddable
    public static class TransactionRequest extends Request {
        @OneToMany
        @JsonProperty("inputs")
        public List<UTxO> inputs;
        @OneToMany
        @JsonProperty("outputs")
        public List<Transfer> outputs;
        public TransactionRequest(List<UTxO> inputs, List<Transfer> outputs) {
            this.inputs = inputs;
            this.outputs = outputs;
        }
    }

}