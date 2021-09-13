package org.loadgen.solr;

/**
 * @author deepakr
 */
import org.HdrHistogram.Histogram;
import org.HdrHistogram.HistogramLogWriter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

class MetricsCollector {
    private static final String DEFAULT_LOG_DIR = Paths.get(".").toAbsolutePath().normalize().toString();
    private final String LOG_DIR;

    private HistogramLogWriter histogramLogWriterForClientSideViewServiceTimeHistogram;
    private HistogramLogWriter histogramLogWriterForServerSideViewServiceTimeHistogram;
    private HistogramLogWriter histogramLogWriterForClientSideViewResponseTimeHistogram;

    private Timer metricsCollectionTimer;

    final private LoadGenerator loadGenerator;
    public MetricsCollector(LoadGenerator loadGenerator) {
        this.loadGenerator = loadGenerator;

//        String logDirectoryName = "hdr_histogram-logs-" + new SimpleDateFormat("dd-MMMM-yyyy-HH:MM:ss:SSSSS-z").format(new Date());
        String logDirectoryName = "hdr_histogram-logs-" + loadGenerator.getOperationName();
        LOG_DIR = System.getProperty("logDir", DEFAULT_LOG_DIR) + File.separator + logDirectoryName;

        try {
            Files.createDirectories(Paths.get(LOG_DIR));
        } catch (IOException e) {
            System.err.println("FATAL: Failed to create log directory : " + LOG_DIR);
            e.printStackTrace();
            System.exit(3);
        }
    }

    public void setupHistogramLogs() {
        String SERVER_SERVICE_TIME_HLOG_FILE_NAME  = LOG_DIR + File.separator + "hlog_ttpt" +
                loadGenerator.targetThroughput + "_" + loadGenerator.getOperationName() + "_server_st.log";
        String CLIENT_SERVICE_TIME_HLOG_FILE_NAME  = LOG_DIR + File.separator + "hlog_ttpt" +
                loadGenerator.targetThroughput + "_" + loadGenerator.getOperationName() + "_client_st.log";
        String CLIENT_RESPONSE_TIME_HLOG_FILE_NAME = LOG_DIR + File.separator + "hlog_ttpt" +
                loadGenerator.targetThroughput + "_" + loadGenerator.getOperationName() + "_client_rt.log";

        SERVER_SERVICE_TIME_HLOG_FILE_NAME  = getNonDuplicateLogName(SERVER_SERVICE_TIME_HLOG_FILE_NAME);
        CLIENT_SERVICE_TIME_HLOG_FILE_NAME  = getNonDuplicateLogName(CLIENT_SERVICE_TIME_HLOG_FILE_NAME);
        CLIENT_RESPONSE_TIME_HLOG_FILE_NAME = getNonDuplicateLogName(CLIENT_RESPONSE_TIME_HLOG_FILE_NAME);

        try {
            histogramLogWriterForServerSideViewServiceTimeHistogram  = new HistogramLogWriter(SERVER_SERVICE_TIME_HLOG_FILE_NAME);
            histogramLogWriterForClientSideViewServiceTimeHistogram  = new HistogramLogWriter(CLIENT_SERVICE_TIME_HLOG_FILE_NAME);
            histogramLogWriterForClientSideViewResponseTimeHistogram = new HistogramLogWriter(CLIENT_RESPONSE_TIME_HLOG_FILE_NAME);
        } catch (FileNotFoundException e) {
            System.err.println("FATAL : Failed while setting up HDRHistogram logs");
            e.printStackTrace();
            System.exit(3);
        }
    }

    public void takeHistogramSnapshot () {
        final Histogram serverSideViewServiceTimeHistogram_intervalHistogram  = new Histogram(2);
        final Histogram clientSideViewServiceTimeHistogram_intervalHistogram  = new Histogram(2);
        final Histogram clientSideViewResponseTimeHistogram_intervalHistogram = new Histogram(2);

        for (int i = 0; i < loadGenerator.numberOfThreads; i++) {

            serverSideViewServiceTimeHistogram_intervalHistogram.add(loadGenerator.arrayOfQueryWorkers[i].getQueryWorkerStat()
                    .getServerSideViewServiceTimeHistogram().getIntervalHistogram(null));
            clientSideViewServiceTimeHistogram_intervalHistogram.add(loadGenerator.arrayOfQueryWorkers[i].getQueryWorkerStat()
                    .getClientSideViewServiceTimeHistogram().getIntervalHistogram(null));
            clientSideViewResponseTimeHistogram_intervalHistogram.add(loadGenerator.arrayOfQueryWorkers[i].getQueryWorkerStat()
                    .getClientSideViewResponseTimeHistogram().getIntervalHistogram(null));
        }// for loop ends

        histogramLogWriterForClientSideViewServiceTimeHistogram.outputIntervalHistogram(
                clientSideViewServiceTimeHistogram_intervalHistogram
        );

        histogramLogWriterForServerSideViewServiceTimeHistogram.outputIntervalHistogram(
                serverSideViewServiceTimeHistogram_intervalHistogram
        );

        histogramLogWriterForClientSideViewResponseTimeHistogram.outputIntervalHistogram(
                clientSideViewResponseTimeHistogram_intervalHistogram
        );
    }

    private String getNonDuplicateLogName(final String baseLogName) {
        String uniqueLogFileName = baseLogName;

        File baseLogFile = new File(baseLogName);
        int counter = 0;

        while (baseLogFile.exists()) {
            uniqueLogFileName = baseLogName + "." + counter++;
            baseLogFile = new File(uniqueLogFileName);
        }
        return uniqueLogFileName;
    }

    public void start() {
        setupHistogramLogs();

        // start a timer to collect latency metrics every 1 sec
        metricsCollectionTimer = new Timer(true);
        metricsCollectionTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                takeHistogramSnapshot();
            }// run method
        }, 0, TimeUnit.SECONDS.toMillis(1));


        // Experimental
        metricsCollectionTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                for (int i = 0; i < loadGenerator.arrayOfQueryWorkers.length; i++) {
                    loadGenerator.arrayOfQueryWorkers[i].getRateLimiter().reset();

                    // TODO: Re-work the code
                    //  This is a dirty hack: Passing 'externalMetricsConsumer' AKA TUSLA recorder to each
                    //  worker after the 'skipDurationInSec'
                    loadGenerator.arrayOfQueryWorkers[i].getQueryWorkerStat().
                            setupExternalMetricsConsumer(loadGenerator.externalMetricsConsumer);
                }
            }
        }, TimeUnit.SECONDS.toMillis(loadGenerator.skipDurationInSec));
    }

    public void stop() {
        // Cancel the metric collection
        metricsCollectionTimer.cancel();

        // Take one last snapshot of the histogram to collect any residue buckets
        takeHistogramSnapshot();
    }
}

