package transactionmanager;

import grpcservice.RPCService;
import grpcservice.RequestHandler;
import grpcservice.RequestHandlerUtils;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.StatusRuntimeException;
import javassist.bytecode.stackmap.TypeData;
import model.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import zookeeper.ZooKeeperClient;
import zookeeper.ZooKeeperClientImpl;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.Thread.sleep;

@Service
public class TransactionManager {
    private static final Logger LOGGER = Logger.getLogger(TypeData.ClassName.class.getName());

    // ZooKeeper Client
    final private ZooKeeperClient zk;

    // Request Handler Delegate - Used to forward requests to other servers
    final private RequestHandler delegate;

    // RPC Service is responsible for issuing gRPC requests to other servers
    final private RPCService rpcService;

    // The Transaction Ledger (db)
    final private TransactionLedger ledger;

    public TransactionManager() {
        this.zk = new ZooKeeperClientImpl();
        this.ledger = new TransactionLedger();
        this.delegate = new RequestHandler(this);
        this.rpcService = new RPCService(this);
    }

    /** Setup Stage for all the subcomponents */
    public void setup() throws IOException {
        // Setup Zookeeper - Will wait until all servers are registered as needed
        // Environment variables needed:
        //   - zk_connection : list of ZooKeeper server addresses
        //   - num_shards : Num shards
        //   - num_servers_per_shard : Number of servers in shard - should be odd
        //   - grpc_address : The IP:Port address to be used by the gRPC
        //   - rest_port: The Port of the REST server to be used by Spring
        // FIXME: Implement the setup
        // this.zk.setup();

        // FIXME: This information should come from Zookeeper !!
        String FIXME_myServerAddress = System.getenv("grpc_address");
        Map<String, String> FIXME_serversAddresses = Map.of(
                "1", "localhost:8980",
                "2", "localhost:8981",
                "3", "localhost:8982"
        );

        // Same serverBuilder will be used by all services
        ServerBuilder<?> serverBuilder = ServerBuilder.forPort(Integer.parseInt(
                FIXME_myServerAddress.split(":")[1])
        );

        // Add services to the serverBuilder before continuing
        this.delegate.addServices(serverBuilder);
        this.rpcService.addServices(serverBuilder);

        // Run the gRPC server in the background
        Server grpcServer = serverBuilder.build();
        try {
            grpcServer.start();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // FIXME: MUST wait for setup to finish in ALL servers before finishing
        try { sleep(3000); } catch (InterruptedException e) { e.printStackTrace(); }

        // Setup services
        this.delegate.setup(FIXME_serversAddresses);
        this.rpcService.setup(FIXME_serversAddresses);

        // FIXME: MUST wait for setup to finish in ALL servers before finishing
        try { sleep(3000); } catch (InterruptedException e) { e.printStackTrace(); }

    }

    ////////////////////// Pending Requests ///////////////////////
    final private List<PendingRequest> pendingRequests = new ArrayList<>();
    private class PendingRequest {
        private Integer mutex;
        private String requestId;
        private int localRequestId; // for Sajy
    }

    /**
     *  Request Handling:
     *  Functions are called from either the REST server or from another Server using the RequestHandler service.
     *  They either perform locally or post a request to the atomic broadcast and wait until the request is completed.
     *  Functions should return the response - not exception so calling server using gRPC can handle them correctly.
     */
    public Response.TransactionResp handleTransaction(Request.TransactionRequest req , String reqId) {
        // if (!"STOP".equals(reqId)) {
        //     rpcService.client.getEntireHistory(List.of("2", "3"), 100, "STOP");
        //     return delegate.client.delegateHandleTransaction(List.of("1", "2", "3"), req, "STOP");
        // }
        return null;
    }
    public Response.TransactionResp handleCoinTransfer(String sourceAddress, String targetAddress, long coins, String reqId) {
        return null;
    }
    public Response.UnusedUTxOListResp handleListAddrUTxO(String sourceAddress) {
        return null;
    }
    public Response.TransactionListResp handleListAddrTransactions(String sourceAddress, int limit) {
        return null;
    }
    public Response.TransactionListResp handleListEntireHistory(int limit, String reqId) {
        return null;
    }
    public Response.TransactionListResp handleAtomicTxList(List<Request.TransactionRequest> atomicList, String reqId) {
        return null;
    }


    /**
     *  RPC services:
     *  Functions are called using gRPC from other servers. Used for submitting a transaction,
     *  checking if an atomic list can be submitted or giving the entire history.
     */
    public void recordSubmittedTransaction(Transaction transaction) {
        return;
    }
    public List<Response> canProcessAtomicTxListStubs(List<Request.TransactionRequest> atomicList, String reqId) {
        return null;
    }
    public List<Transaction> getEntireHistory(int limit, String reqId) {
        return null;
    }


    /**
     *  Request Processing:
     *  Functions are called on requests that were broadcast using AtomicBroadcast to multiple servers.
     *  Functions should do the processing logic here for each of the functions.
     */

}
