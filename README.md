# 236351-Distributed-Transaction-Manager

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
