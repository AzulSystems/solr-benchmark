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
