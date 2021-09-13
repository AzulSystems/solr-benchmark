package org.loadgen.solr.select;

/**
 * @author deepakr
 */
import org.loadgen.solr.BenchConfig;
import org.loadgen.solr.LoadGenerator;
import org.loadgen.solr.QueryWorker;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class LoadGeneratorForSelectQuery extends LoadGenerator {
    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(LoadGeneratorForSelectQuery.class.getName());

    private boolean shouldRunSecondaryQueryOps;
    private int maxUsableSelectQueries;
    private int maxSizeOfClientSideCacheableSelectQueries;
    private double percentageUsableClientSideQueryCache;
    private List<File> listOfQueryFiles;

    public LoadGeneratorForSelectQuery setShouldRunSecondaryQueryOps(boolean shouldRunSecondaryQueryOps) {
        this.shouldRunSecondaryQueryOps = shouldRunSecondaryQueryOps;
        return this;
    }

    public LoadGeneratorForSelectQuery setMaxUsableSelectQueries(int maxUsableSelectQueries) {
        this.maxUsableSelectQueries = maxUsableSelectQueries;
        return this;
    }

    public LoadGeneratorForSelectQuery setMaxSizeOFClientSideCacheableSelectQueries(int maxCachableSelectQueries) {
        this.maxSizeOfClientSideCacheableSelectQueries = maxCachableSelectQueries;
        return this;
    }

    public LoadGeneratorForSelectQuery setPercentageUsableClientSideQueryCache(double percentageUsableClientSideQueryCache) {
        this.percentageUsableClientSideQueryCache = percentageUsableClientSideQueryCache;
        return this;
    }

    public LoadGeneratorForSelectQuery registerQueryFiles(String selectQueryFiles) {
        if (numberOfThreads == 0) return this;

        listOfQueryFiles = Arrays.stream(selectQueryFiles.split("\\s+"))
                .filter(x -> !x.equals(""))
                .filter(fileName -> fileName.toLowerCase().contains(System.getProperty("queryType", ""))) // Temporary filter
                .map(File::new)
                .collect(Collectors.toList());

        listOfQueryFiles.stream().forEach(file -> {
            try {
                QueryHandler.registerQueryFile(
                        file,
                        maxUsableSelectQueries != -1 ? (maxUsableSelectQueries / listOfQueryFiles.size()) : -1
                );
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        return this;
    }

    @Override
    protected String getOperationName() {
        return "select";
    }

    @Override
    public void applyConfig(BenchConfig benchConfig) {
        super.applyConfig(benchConfig);
        this.setMaxUsableSelectQueries(benchConfig.maxUsableSelectQueries)
                .registerQueryFiles(benchConfig.selectQueryFiles);

        if (benchConfig.maxNumberOfSelectOpThreads != -1) this.setNumberOfThreads(benchConfig.maxNumberOfSelectOpThreads);
        if (benchConfig.maxNumberOfSelectOpClients != -1) this.setNumberOfClients(benchConfig.maxNumberOfSelectOpClients);

        if (getOperationName().contains("warmup")) {
            this.setRunDurationInSec(benchConfig.benchmarkWarmupTime);
            this.setSkipDurationInSec(0);
            if (benchConfig.targetRateForSelectOpAtWarmup != -1) {
                this.setTargetThroughput(benchConfig.targetRateForSelectOpAtWarmup);
            }
        } else {
            this.setRunDurationInSec(benchConfig.benchmarkMeasurementTime);
            if (benchConfig.targetRateForSelectOp != -1) {
                // send 'select' queries at a fixed rate instead of 'targetRate * readPercent'
                this.setTargetThroughput(benchConfig.targetRateForSelectOp);
            }
        }
    }

    @Override
    protected double getScaleFactor() {
        return (1.0 - (updatePercentage / 100.0));
    }

    @Override
    protected QueryWorker getQueryWorkerInstance() {
        SelectQueryWorker selectQueryWorker = new SelectQueryWorker();
        selectQueryWorker.setShouldRunSecondaryQueryOps(shouldRunSecondaryQueryOps);
        selectQueryWorker.setMaxSizeOfClientSideQueryCacheForCurrentWorker(maxSizeOfClientSideCacheableSelectQueries / numberOfThreads);
        selectQueryWorker.setPercentageUsableClientSideQueryCache(percentageUsableClientSideQueryCache);
        return selectQueryWorker;
    }

    public void printConfig() {
        super.printConfig();
        log.info(String.format("%-30s %s %s", "selectQueryPercentage", ":", getScaleFactor() * 100));
        log.info(String.format("%-30s %s %s", "maxUsableSelectQueries", ":", maxUsableSelectQueries));
        log.info(String.format("%-30s %s %s", "listOfQueryFiles", ":", listOfQueryFiles.stream().map(file -> file.getName()).collect(Collectors.toList())));
        if (shouldRunSecondaryQueryOps) {
            log.info(String.format("%-30s %s %s", "shouldRunSecondaryQueryOps", ":", shouldRunSecondaryQueryOps));
        }
        if (percentageUsableClientSideQueryCache != 0) {
            log.info(String.format("%-30s %s %s", "percentageUsableClientSideQueryCache", ":", percentageUsableClientSideQueryCache));
        }
        if (maxSizeOfClientSideCacheableSelectQueries != 0) {
            log.info(String.format("%-30s %s %s", "maxSizeOfClientSideCacheableSelectQueries", ":", maxSizeOfClientSideCacheableSelectQueries));
        }
    }
}
