package org.loadgen.solr;

/**
 * @author deepakr
 */
import lombok.Data;
import lombok.ToString;

@Data
@ToString(callSuper = true)
public class BenchConfig {

    public String hostnamePortList = "zoo-node-1:2181,zoo-node-2:2181,zoo-node-3:2181";
    public String solrCollection = "test";

    public int benchmarkWarmupTime = 60;
    public int benchmarkMeasurementTime = 60;
    public int benchmarkMeasurementSkipDuration = 0;

    public int maxNumberOfThreads = 1;
    public int maxNumberOfClients = 1;;
    public int targetRate = 1000;
    public double writePercent = 0;

    public String selectQueryFiles;
    public String documentIdFile;

    // Overriding params
    // Depending upon writePercent, the resources (maxNumberOfThreads, maxNumberOfClients, targetRate) will be split
    // between the 'Select' and 'Update' operations
    // Set the below parameters to do the final overriding
    public int maxNumberOfSelectOpThreads = -1;
    public int maxNumberOfSelectOpClients = -1;

    public int maxNumberOfUpdateOpThreads = -1;
    public int maxNumberOfUpdateOpClients = -1;

    public int targetRateForSelectOpAtWarmup = -1;
    public int targetRateForUpdateOpAtWarmup = -1;

    public int targetRateForSelectOp = -1;
    public int targetRateForUpdateOp = -1;

    public boolean recordingLatency = false;
    public int maxUsableSelectQueries = 10000;


    // Experimental
    public boolean shouldRunSecondaryQueryOps = false;
    public int maxSizeOfClientSideCacheableSelectQueries = 1000;
    public int percentageUsableClientSideQueryCache = 0;
}
