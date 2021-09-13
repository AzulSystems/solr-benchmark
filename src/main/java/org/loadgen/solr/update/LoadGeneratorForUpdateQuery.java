package org.loadgen.solr.update;

/**
 * @author deepakr
 */
import org.loadgen.solr.LoadGenerator;
import org.loadgen.solr.QueryWorker;
import org.loadgen.solr.BenchConfig;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class LoadGeneratorForUpdateQuery extends LoadGenerator {
    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(LoadGeneratorForUpdateQuery.class.getName());

    private String solrDocumentIdFile;
    private List<String> solrDocumentIdList;

    public LoadGeneratorForUpdateQuery setSolrDocumentIdFile(String solrDocumentIdFile) {
        File solrDocumentIdFileObj = new File(solrDocumentIdFile);
        if (!solrDocumentIdFileObj.exists()) {
            log.severe("The document ID file : " + solrDocumentIdFileObj.getAbsolutePath() + " not found");
            System.exit(1);
        }

        this.solrDocumentIdFile = solrDocumentIdFile;
        try {
            this.solrDocumentIdList = Files.readAllLines(new File(solrDocumentIdFile).toPath())
                    .parallelStream()
                    .collect(Collectors.toCollection(ArrayList::new));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return this;
    }

    @Override
    protected String getOperationName() {
        return "update";
    }

    @Override
    public void applyConfig(BenchConfig benchConfig) {
        super.applyConfig(benchConfig);
        if (
                        (benchConfig.writePercent > 0) ||
                        (benchConfig.targetRateForUpdateOpAtWarmup > 0) ||
                        (benchConfig.targetRateForUpdateOp > 0)
        ) {
            this.setSolrDocumentIdFile(benchConfig.documentIdFile);
        }

        if (benchConfig.maxNumberOfUpdateOpThreads != -1) this.setNumberOfThreads(benchConfig.maxNumberOfUpdateOpThreads);
        if (benchConfig.maxNumberOfUpdateOpClients != -1) this.setNumberOfClients(benchConfig.maxNumberOfUpdateOpClients);

        if (getOperationName().contains("warmup")) {
            this.setRunDurationInSec(benchConfig.benchmarkWarmupTime);
            this.setSkipDurationInSec(0);
            if (benchConfig.targetRateForUpdateOpAtWarmup != -1) {
                this.setTargetThroughput(benchConfig.targetRateForUpdateOpAtWarmup);
            }
        } else {
            this.setRunDurationInSec(benchConfig.benchmarkMeasurementTime);
            if (benchConfig.targetRateForUpdateOp != -1) {
                // send 'update' queries at a fixed rate instead of 'targetRate * writePercent'
                this.setTargetThroughput(benchConfig.targetRateForUpdateOp);
            }
        }
    }

    @Override
    protected double getScaleFactor() {
        return updatePercentage / 100.0;
    }

    @Override
    protected QueryWorker getQueryWorkerInstance() {
        UpdateQueryWorker updateQueryWorker = new UpdateQueryWorker();
        updateQueryWorker.setSolrDocumentIdList(solrDocumentIdList);
        return updateQueryWorker;
    }

    public void printConfig() {
        super.printConfig();
        log.info(String.format("%-30s %s %s", "updateQueryPercentage", ":", getScaleFactor() * 100));
        log.info(String.format("%-30s %s %s", "solrDocumentIdFile", ":", solrDocumentIdFile));
    }
}
