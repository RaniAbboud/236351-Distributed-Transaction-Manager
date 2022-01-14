package grpcservice;

import io.grpc.ServerBuilder;
import transactionmanager.TransactionManager;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RequestHandler {
    private static final Logger logger = Logger.getLogger(RequestHandler.class.getName());

    /** The client and server parts of the Request Handler */
    public RequestHandlerClient client;
    RequestHandlerServer server;

    public RequestHandler(TransactionManager mngr) {
        this.client = new RequestHandlerClient();
        this.server = new RequestHandlerServer(mngr);
    }

    public void addServices(ServerBuilder<?> serverBuilder) {
        serverBuilder.addService(this.server);
    }

    public void setup(Map<String, String> serversAddresses) {
        this.client.setup(serversAddresses);
    }
}