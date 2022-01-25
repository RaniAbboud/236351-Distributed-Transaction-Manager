package constants;

public final class Constants {

    private Constants() {
        // restrict instantiation
    }

    public static final String ENV_NUM_SHARDS = "NUM_SHARDS";
    public static final String ENV_NUM_SERVERS_PER_SHARD = "NUM_SERVERS_PER_SHARD";
    public static final String ENV_ZK_CONNECTION = "ZK_CONNECTION";
    public static final String ENV_GRPC_PORT = "GRPC_PORT";
    public static final String ENV_HTTP_PORT = "HTTP_PORT";
    public static final String ENV_HOST_NAME = "HOST_NAME";

    public static final String GENESIS_ADDRESS = "GenesisAddress";
    public static final String GENESIS_TRANSACTION_ID = "GenesisTxId";
}