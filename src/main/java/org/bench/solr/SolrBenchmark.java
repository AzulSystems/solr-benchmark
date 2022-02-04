/*
 * Copyright (c) 2021, Azul Systems
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of [project] nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

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
