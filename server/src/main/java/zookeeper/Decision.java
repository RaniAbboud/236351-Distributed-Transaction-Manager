package zookeeper;

import java.io.Serializable;

public class Decision implements Serializable {
    public Boolean decision;
    public long timestamp;

    public Decision(Boolean decision, long timestamp) {
        this.decision = decision;
        this.timestamp = timestamp;
    }

    public Decision(Boolean decision) {
        this.decision = decision;
        this.timestamp = -1;
    }
}
