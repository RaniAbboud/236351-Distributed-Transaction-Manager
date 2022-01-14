package grpcservice;

import io.grpc.ServerBuilder;
import transactionmanager.TransactionManager;

import java.util.Map;
import java.util.logging.Logger;

public class RPCService {
    private static final Logger logger = Logger.getLogger(RPCService.class.getName());

    /** The client and server parts of the RPC Service */
    public RPCServiceClient client;
    RPCServiceServer server;

    public RPCService(TransactionManager mngr) {
        this.client = new RPCServiceClient();
        this.server = new RPCServiceServer(mngr);
    }

    public void addServices(ServerBuilder<?> serverBuilder) {
        serverBuilder.addService(this.server);
    }

    public void setup(Map<String, String> serversAddresses) {
        this.client.setup(serversAddresses);
    }

}