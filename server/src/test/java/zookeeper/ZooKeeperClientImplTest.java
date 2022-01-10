package zookeeper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

import constants.Constants;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooKeeper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

//@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(CONCURRENT)
public class ZooKeeperClientImplTest {
//    private ZooKeeperClient zk;
//
//    @BeforeAll
//    private void setUp() {
//        try {
//            zk = new ZooKeeperClientImpl("localhost:2181");
//        } catch (IOException e) {
//            Assertions.fail(e);
//        }
//        Assertions.assertNotNull(zk);
//        try {
//            zk.setupInitialStructures();
//        } catch (InterruptedException | KeeperException e) {
//            Assertions.fail(e);
//        }
//    }

//    @RepeatedTest(9)
    @Test
    @DisplayName("Test Registration")
    void testRegister() throws InterruptedException {
        ZooKeeperClient zk = null;
        try {
            zk = new ZooKeeperClientImpl("localhost:2181");
        } catch (IOException e) {
            Assertions.fail(e);
        }
        Assertions.assertNotNull(zk);
        try {
            zk.setupInitialStructures();
        } catch (InterruptedException | KeeperException e) {
            Assertions.fail(e);
        }
        int numShards = Integer.parseInt(System.getenv(Constants.ENV_NUM_SHARDS));
        String[] shards = new String[numShards];
        for (int i=0; i<numShards; i++){
            shards[i] = "shard-" + i;
        }
        String barrierId = "barrier";
        try {
            zk.enterBarrier(barrierId, shards);
        } catch (KeeperException e) {
            Assertions.fail(e);
        }
        final String address = "localhost:1";
        String serverId = "";
        try {
            serverId = zk.registerServer(address);
        } catch (InterruptedException | KeeperException e) {
            Assertions.fail(e);
        }
        Assertions.assertTrue(serverId.startsWith("server-"));
        try {
            zk.leaveBarrier(barrierId, shards);
        } catch (KeeperException e) {
            Assertions.fail(e);
        }
//        TimeUnit.SECONDS.sleep(10);
    }
}

