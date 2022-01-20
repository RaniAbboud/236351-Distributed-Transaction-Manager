package constants;

public final class Constants {

    private Constants() {
        // restrict instantiation
    }

    public static final String ENV_NUM_SHARDS = "num_shards";
    public static final String ENV_NUM_SERVERS_PER_SHARD = "num_servers_per_shard";
    public static final String ENV_ZK_CONNECTION = "zk_connection";
    public static final String ENV_GRPC_ADDRESS = "grpc_address";
    public static final String ENV_REST_PORT = "rest_port";

    public static final String GENESIS_ADDRESS = "GenesisAddress";
    public static final String GENESIS_TRANSACTION_ID = "GenesisTxId";
}