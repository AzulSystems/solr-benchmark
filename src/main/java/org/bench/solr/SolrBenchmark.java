package org.bench.solr;

/**
 * @author deepakr
 */
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.loadgen.solr.BenchConfig;
import org.loadgen.solr.LoadGenerator;
import org.loadgen.solr.select.LoadGeneratorForSelectQuery;
import org.loadgen.solr.update.LoadGeneratorForUpdateQuery;

import java.io.FileInputStream;

public class SolrBenchmark {
    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(SolrBenchmark.class.getName());

    private final BenchConfig benchConfig;
    public SolrBenchmark(String configFile) throws Exception {
        Yaml yaml = new Yaml(new Constructor(BenchConfig.class));
        try (FileInputStream inputStream = new FileInputStream(configFile)) {
            benchConfig = yaml.load(inputStream);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new Exception("USAGE: java " + SolrBenchmark.class.getName() + " config-file");
        }

        SolrBenchmark solrBenchmark = new SolrBenchmark(args[0]);
        solrBenchmark.warmup();
        solrBenchmark.run();
    }

    public void warmup() {
        log.info("\nStarting warmup phase ...");
        // Select
        // -----------------------------------
        final LoadGenerator selectLoadGenerator = new LoadGeneratorForSelectQuery() {
            @Override
            protected String getOperationName() {
                return "warmup_" + super.getOperationName();
            }
        };
        selectLoadGenerator.applyConfig(benchConfig);

        // Update
        // -----------------------------------
        final LoadGenerator updateLoadGenerator = new LoadGeneratorForUpdateQuery() {
            @Override
            protected String getOperationName() {
                return "warmup_" + super.getOperationName();
            }
        };
        updateLoadGenerator.applyConfig(benchConfig);

        selectLoadGenerator.startBenchmark();
        updateLoadGenerator.startBenchmark();

        try {
            selectLoadGenerator.waitForBenchmarkRunToFinish();
            updateLoadGenerator.waitForBenchmarkRunToFinish();
        } catch (Exception e) {
            e.printStackTrace();
        }
        log.info("\nWarmup phase completed");
    }

    public void run() {
        log.info("\nStarting measurement phase ...");
        // Select
        // -----------------------------------
        final LoadGenerator selectLoadGenerator = new LoadGeneratorForSelectQuery();
        selectLoadGenerator.applyConfig(benchConfig);


        // Update
        // -----------------------------------
        final LoadGenerator updateLoadGenerator = new LoadGeneratorForUpdateQuery();
        updateLoadGenerator.applyConfig(benchConfig);

        selectLoadGenerator.startBenchmark();
        updateLoadGenerator.startBenchmark();

        try {
            selectLoadGenerator.waitForBenchmarkRunToFinish();
            updateLoadGenerator.waitForBenchmarkRunToFinish();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // (track results for only 'Select' operations)
        final long totalRequestsSent = selectLoadGenerator.getTotalRequestsSentFromAllWorker();
        final long totalRunDurationInSec = benchConfig.getBenchmarkMeasurementTime();

        log.info(String.format("Requested rate = %d req/sec | Actual rate = %d req/sec (%d queries in %d sec)",
                benchConfig.getTargetRateForSelectOp(),
                (totalRequestsSent / totalRunDurationInSec),
                totalRequestsSent,
                totalRunDurationInSec)
        );
        log.info("\nMeasurement phase completed");
    }
}
