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
