Contents
========
* [Overview](#overview)
* [Benchmark setup](#benchmark-setup)
* [What does the benchmark do ?](#what-does-the-benchmark-do-)
* [Dataset used in the benchmark](#details-of-dataset-used-in-benchmarking)
* [How to run the benchmark](#how-to-run-the-benchmark-)
    * [Prepare the benchmarking setup](#prepare-the-benchmarking-setup)
        * [Provision the necessary AWS nodes](#provision-the-necessary-aws-nodes)
        * [Configure provisioned nodes](#configure-provisioned-nodes)
    * [Running the benchmark](#starting-the-benchmark)

 
## Overview
A simple to use, benchmark setup that takes care of setting up a Solr cluster on AWS and benchmarks 
it against different query types

## Benchmark setup

Setup includes:
- 4 Solr nodes 
    - solr-node-1
    - solr-node-2
    - solr-node-3
    - solr-node-4
- 3 Zookeeper nodes
    - zoo-node-1
    - zoo-node-2
    - zoo-node-3
- 1 Client node `[Load Generator]`
    - solrj-client-1

All the above components (including the **client** `[Load Generator]` will run on individual AWS nodes)
<br/><br/>
The current benchmark setup uses: 
* Solr version: solr-7.7.3
* Zookeeper version: zookeeper-3.4.13

In the current state, this benchmark setup can be used to benchmark Solr on the following JDK's:
* Zing: zing21.07.0.0-3-ca-jdk11.0.12
* Zulu: zulu11.50.19-ca-jdk11.0.12

The `solr version`, `zookeeper version` and `JDK of choice` can be easily changed by modifying [init.sh](scripts/init.sh) file 

## What does the benchmark do ?
The benchmark setup includes a client/load generator built using SolrJ library <br/>
It is run on a dedicated AWS node to benchmark the Solr cluster

It currently supports benchmarking Solr cluster with 5 types of _search/select queries_:
* [field/term](https://solr.apache.org/guide/7_0/the-standard-query-parser.html#specifying-terms-for-the-standard-query-parser)
* [phrase](https://solr.apache.org/guide/7_0/the-standard-query-parser.html#specifying-terms-for-the-standard-query-parser) 
* [proximity](https://solr.apache.org/guide/7_7/the-standard-query-parser.html#proximity-searches)
* [range](https://solr.apache.org/guide/7_7/the-standard-query-parser.html#range-searches)
* [fuzzy](https://solr.apache.org/guide/7_7/the-standard-query-parser.html#fuzzy-searches)

In addition to the search requests, a typical Solr application also needs to deal with update requests, and 
the performance of the search requests are affected depending on how the update requests are handled.  

In order to study this effect, the current benchmark setup also has the ability to run 
_update operations/queries_ ([atomic updates](https://solr.apache.org/guide/7_7/updating-parts-of-documents.html#atomic-updates)) 
along side the search/select queries mentioned above <br/>
But these update operations are run as a background task and the performance of only the search/select operations are measured

The search/select queries that are used are stored in text files <br/>
Depending on the type of query chosen for benchmarking, the relevant query files are read by the client and requests are 
continuously submitted to Solr cluster<br/>

The benchmark allows sending the requests at a fixed _target rate_<br/>
But in order to measure the _peak throughput_ that can be achieved, the _target rate_ (`targetRateForSelectOpAtWarmup`, `targetRateForSelectOp`) 
is deliberately set to a very high value (see [config file](bench-config.yaml)) and the _actual rate achieved_ is recorded.

The background _update operations_ are run at a fixed rate of `1000 requests/sec`

## Details of dataset used in benchmarking
A ~50GB wikimedia dump ([link](https://cdn.azul.com/blogs/datasets/solr/wiki.json.gz)) is indexed into the Solr cluster 
against which the benchmark is run <br/>
It is a derivative of [pages-articles-multistream.xml.bz2](https://dumps.wikimedia.org/enwiki/latest/enwiki-latest-pages-articles-multistream.xml.bz2) by the **Wikimedia Foundation**, 
used under [`CC BY-SA 3.0`](https://creativecommons.org/licenses/by-sa/3.0/) 
<br/><br/>
This data dump is licensed under `CC BY-SA 3.0 by Azul Systems, Inc.` 

## How to run the benchmark ?
###  Prepare the benchmarking setup
Only 3 steps are necessary to prepare the setup for benchmarking:<br/>
* First, clone this repo
* [Provision the necessary AWS nodes](#provision-the-necessary-aws-nodes)
* [Configure provisioned nodes](#configure-provisioned-nodes)

Since the entire cluster (3 node zookeeper ensemble + 4 Solr nodes) and the client/load generator runs on AWS instances, 
a light-weight instance is sufficient to act as a central coordinator or leader to take care of _running the above 3 steps_, 
_starting the benchmark runs on the cluster_, _collecting the results of the benchmark runs_ etc ... <br/>
This light-weight instance can either be user's laptops (with linux/mac OS) or a separate small AWS instance can be used


#### Provision the necessary AWS nodes
To provision the necessary AWS instances, follow the instructions [here](terraform/README.md)

#### Configure provisioned nodes

Run the below command to configure the nodes, install necessary tools, download the necessary artifacts etc ...
```bash
bash scripts/setup.sh all
```
NOTE: Make sure `JAVA_HOME` env (`pointing to JDK11`) is set on the host which runs this script
<br/><br/>
The above command takes care of the following:
* prepares 3 node Zookeeper ensemble (_zoo-node-1, zoo-node-2, zoo-node-3_)
* prepares 4 node Solr cluster (_solr-node-1, solr-node-2, solr-node-3, solr-node-4_)
* prepares a client node (_solrj-client-1_)
* wikimedia dump is indexed into the Solr cluster 

#### Starting the benchmark
##### General command to run the benchmark against a given query type:
```
QUERY_TYPE=<QUERY TYPE> JAVA_HOME=<ABS_PATH_TO_JAVA_HOME_ON_AWS> bash scripts/main.sh startBenchmark
```
NOTE: To pass additional JVM args to the Solr cluster, `SOLR_JAVA_MEM` and `GC_TUNE` env variables can be used:
```
GC_TUNE='-XX:-UseZST -XX:+PrintGCDetails' SOLR_JAVA_MEM='-Xms55g -Xmx70g' QUERY_TYPE=<QUERY TYPE> JAVA_HOME=<ABS_PATH_TO_JAVA_HOME_ON_AWS> bash scripts/main.sh startBenchmark
```
##### Sample commands to launch/start the benchmark
* To benchmark Solr with phrase queries, on Zing:
    ```
    COMMON_LOG_DIR=phrase-queries-on-zing QUERY_TYPE=phrase JAVA_HOME=/home/centos/zing21.07.0.0-3-ca-jdk11.0.12-linux_x64/ bash scripts/main.sh startBenchmark
    ```

* To benchmark Solr with term/field queries, on Zulu:
    ```
    COMMON_LOG_DIR=field-queries-on-zulu QUERY_TYPE=field JAVA_HOME=/home/centos/zulu11.50.19-ca-jdk11.0.12-linux_x64/ bash scripts/main.sh startBenchmark
    ``` 

NOTE:   
If `QUERY_TYPE=<QUERY_TYPE>` is omitted, the benchmark will run against a mix of all the above listed query types
 
 
##### Where to find the results of the benchmark run ?
The results of the benchmark runs are captured in the `benchmark.log` under `COMMON_LOG_DIR`<br/>
For the 2 sample launches shown above, the final result can be found under:
* `${WORKING_DIR}/phrase-queries-on-zing/benchmark.log`
* `${WORKING_DIR}/field-queries-on-zulu/benchmark.log`

The result is simply reported in a single line in the following format: <br/>
````
Requested rate = <requested_rate> req/sec | Actual rate = <actual_rate_achieved> req/sec (<nubmer requests of submmitted by the client to the Solr cluster> queries in `<duration of benchmark run>` sec)
````
Sample results:
```
Requested rate = 100000 req/sec | Actual rate = 47821 req/sec (43039651 queries in 900 sec)
Requested rate = 100000 req/sec | Actual rate = 31619 req/sec (28457667 queries in 900 sec)
```
 
In addition to the `benchmark.log`, the Solr logs, GC logs etc are also collected and stored under `COMMON_LOG_DIR` 
after the benchmark run 
 
##### A simple script to run all the queries on Zing and Zulu multiple times:
```
for queryType in "field" "phrase" "proximity" "range" "fuzzy"
do
    for i in 1 2 3
    do
        HEADER=zing-${queryType}-run${i}
        COMMON_LOG_DIR=${HEADER} QUERY_TYPE=${queryType} JAVA_HOME=/home/centos/zing21.07.0.0-3-ca-jdk11.0.12-linux_x64/ bash scripts/main.sh startBenchmark

        HEADER=zulu-${queryType}-run${i}
        COMMON_LOG_DIR=${HEADER} QUERY_TYPE=${queryType} JAVA_HOME=/home/centos/zulu11.50.19-ca-jdk11.0.12-linux_x64/ bash scripts/main.sh startBenchmark
    done
done
```