package org.loadgen.solr;

/**
 * @author deepakr
 */
import org.HdrHistogram.SingleWriterRecorder;

import java.util.function.Consumer;

public class QueryWorkerStats {

    private final SingleWriterRecorder clientSideViewServiceTimeHistogram;
    private final SingleWriterRecorder serverSideViewServiceTimeHistogram;
    private final SingleWriterRecorder clientSideViewResponseTimeHistogram;

    private long totalRequestsSent;

    private Consumer<Long> statsConsumerExternal;

    public QueryWorkerStats() {
        this.clientSideViewServiceTimeHistogram  = new SingleWriterRecorder(2);
        this.serverSideViewServiceTimeHistogram  = new SingleWriterRecorder(2);
        this.clientSideViewResponseTimeHistogram = new SingleWriterRecorder(2);
    }

    public SingleWriterRecorder getClientSideViewServiceTimeHistogram() {
        return clientSideViewServiceTimeHistogram;
    }

    public SingleWriterRecorder getServerSideViewServiceTimeHistogram() {
        return serverSideViewServiceTimeHistogram;
    }

    public SingleWriterRecorder getClientSideViewResponseTimeHistogram() {
        return clientSideViewResponseTimeHistogram;
    }

    public long getTotalRequestsSent() {
        return totalRequestsSent;
    }

    public void setTotalRequestsSent(long totalRequestsSent) {
        this.totalRequestsSent = totalRequestsSent;
    }

    public void reset() {
        clientSideViewResponseTimeHistogram.reset();
        clientSideViewServiceTimeHistogram.reset();
        serverSideViewServiceTimeHistogram.reset();
        totalRequestsSent = 0;
    }

    public void collect(long responseTimeInNanos, long serviceTimeInNanos, int qTime) {
        clientSideViewResponseTimeHistogram.recordValue(responseTimeInNanos);
        clientSideViewServiceTimeHistogram.recordValue(serviceTimeInNanos);
        serverSideViewServiceTimeHistogram.recordValue(qTime);

        if (statsConsumerExternal != null) {
            statsConsumerExternal.accept(responseTimeInNanos);
        }
    }

    public void setupExternalMetricsConsumer(Consumer<Long> statsConsumerExternal) {
        this.statsConsumerExternal = statsConsumerExternal;
    }
}
