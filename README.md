# 236351-Distributed-Transaction-Manager

## Overview
TrustCoin is a high throughput, low latency and fault-tolerant sharded Transaction Manager. It implements a distributed ledger of transactions following the UTxO model, enabling users to send transactions containing TCoins to each other. It exposes a RESTful API to the clients, and provides the ability to send coins, tailor specific transactions or create a list of transactions that is processed atomically. In addition, it provides sophisticated queries to the ledger, with ranging levels of consistency. The name TrustCoin stems from the fact that our transaction manager assumes honest members, thus doesn’t handle Byzantine members, but instead trusts they do the right thing.

## Running The Project
To run the docker image perform 
```
docker-compose -f docker/docker-compose-cluster.yml up
```
You can change the number of shards or the number of servers in each shard in the `docker/variables.env` file.

Note: make sure that Docker has a high enough memory limit on your machine.
## Sanity Tests
To run the Python testing client perform 
```
python3 client/client.py -s 8081:8089 -t RandomTest
```
