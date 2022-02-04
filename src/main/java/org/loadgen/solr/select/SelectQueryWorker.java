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

package org.loadgen.solr.select;

/**
 * @author deepakr
 */
import org.loadgen.solr.QueryWorker;
import org.loadgen.solr.QueryWorkerStats;
import org.loadgen.solr.ThroughputController;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.request.QueryRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.util.NamedList;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class SelectQueryWorker implements QueryWorker {
    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(SelectQueryWorker.class.getName());

    private static final Random random = new Random();

    private String solrCollection;
    private boolean isStandaloneSolr;
    private long runDurationInSec;
    private ThroughputController throughputController;
    private List<SolrClient> solrClientList;

    // Select specific vars
    private boolean shouldRunSecondaryQueryOps;
    private int maxSizeOfClientSideQueryCacheForCurrentWorker;
    private double percentageUsableClientSideQueryCache;
    private List<SolrQuery> listOfClientSideQueryCacheForCurrentWorker;
    private QueryWorkerStats queryWorkerStats;

//    private static final int[] queryResultSizeUpperBound = new int[] {
//            0,
//            10,
//            50,
//            100,
//            500,
//            1000,
//            5000,
//            10000,
//            50000,
//            100000
//    };
//    private static final int[] queryResultSizeHistogram = new int[queryResultSizeUpperBound.length + 1];

    public SelectQueryWorker() {
        this.solrClientList = new ArrayList<>();
    }

    @Override
    public void setSolrCollection(String solrCollection) {
        this.solrCollection = solrCollection;
    }

    @Override
    public void setStandaloneSolr(boolean standaloneSolr) {
        isStandaloneSolr = standaloneSolr;
    }

    @Override
    public void setQueryWorkerStats(QueryWorkerStats queryWorkerStats) {
        this.queryWorkerStats = queryWorkerStats;
    }

    @Override
    public QueryWorkerStats getQueryWorkerStat() {
        return queryWorkerStats;
    }

    @Override
    public void setRunDurationInSec(long runDurationInSec) {
        this.runDurationInSec = runDurationInSec;
    }

    @Override
    public void addSolrClient(SolrClient solrClient) {
        this.solrClientList.add(solrClient);
    }

    @Override
    public void setRateLimiter(ThroughputController throughputController) {
        this.throughputController = throughputController;
    }

    @Override
    public ThroughputController getRateLimiter() {
        return this.throughputController;
    }

    @Override
    public void closeClientConnections() {
        for (int i = 0; i < solrClientList.size(); i++) {
            try {
                solrClientList.get(i).close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean shouldRunSecondaryQueryOps() {
        return shouldRunSecondaryQueryOps;
    }

    public void setShouldRunSecondaryQueryOps(boolean shouldRunSecondaryQueryOps) {
        this.shouldRunSecondaryQueryOps = shouldRunSecondaryQueryOps;
    }

    public void setMaxSizeOfClientSideQueryCacheForCurrentWorker(int maxSizeOfClientSideQueryCacheForCurrentWorker) {
        this.maxSizeOfClientSideQueryCacheForCurrentWorker = maxSizeOfClientSideQueryCacheForCurrentWorker;
        listOfClientSideQueryCacheForCurrentWorker = new RingBuffer<>(maxSizeOfClientSideQueryCacheForCurrentWorker);
    }

    public void setPercentageUsableClientSideQueryCache(double percentageUsableClientSideQueryCache) {
        this.percentageUsableClientSideQueryCache = percentageUsableClientSideQueryCache;
    }

    @Override
    public Long call() {
        long now;

        final int numberOfClientsAssignedToCurrentWorker = solrClientList.size();
        final long startTime = System.nanoTime();

        throughputController.markCurrentTimeAsOperationStartTime();
        do {
            try {
                int clientIndex = (int) (throughputController.getTotalOperationsCompletedSoFar() % numberOfClientsAssignedToCurrentWorker);
                SolrClient solrClient = solrClientList.get(clientIndex);

                SolrQuery solrQuery = new SolrQuery();
                solrQuery.setStart(0); // start from the 1st doc
                solrQuery.setRows(10); // limit to only 10 doc

                solrQuery.setFields("title", "username", "sha1", "timestamp", "id");

                if ((random.nextInt(100) > (100 - percentageUsableClientSideQueryCache)) && (listOfClientSideQueryCacheForCurrentWorker.size() >= maxSizeOfClientSideQueryCacheForCurrentWorker)) {
                    solrQuery = listOfClientSideQueryCacheForCurrentWorker.get(Math.max(0, random.nextInt(listOfClientSideQueryCacheForCurrentWorker.size())));
                } else {
                    solrQuery.setQuery(QueryHandler.getQuery()); // get some random query

                    if (shouldRunSecondaryQueryOps()) {
                        int randomPercent = random.nextInt(20);

                        if (QueryHandler.SolrField.TIMESTAMP.isSolrFieldSupported() && randomPercent == 0) {
                            solrQuery.addOrUpdateSort(QueryHandler.SolrField.TIMESTAMP.name().toLowerCase(), SolrQuery.ORDER.asc);
                        } else if (QueryHandler.SolrField.TIMESTAMP.isSolrFieldSupported() && randomPercent == 1) {
                            solrQuery.addOrUpdateSort(QueryHandler.SolrField.TIMESTAMP.name().toLowerCase(), SolrQuery.ORDER.desc);
                        } else if (QueryHandler.SolrField.USERNAME.isSolrFieldSupported() && randomPercent == 2) {
                            solrQuery.addOrUpdateSort(QueryHandler.SolrField.USERNAME.name().toLowerCase(), SolrQuery.ORDER.asc);
                        } else if (QueryHandler.SolrField.USERNAME.isSolrFieldSupported() && randomPercent == 3) {
                            solrQuery.addOrUpdateSort(QueryHandler.SolrField.USERNAME.name().toLowerCase(), SolrQuery.ORDER.desc);
                        } else if (QueryHandler.SolrField.USERNAME.isSolrFieldSupported() && randomPercent == 4) {
                            solrQuery.setFilterQueries(QueryHandler.SolrField.USERNAME.getQuery(QueryHandler.QueryType.FIELD));
                        } else if (QueryHandler.SolrField.TIMESTAMP.isSolrFieldSupported() && randomPercent == 5) {
                            solrQuery.setFilterQueries(QueryHandler.SolrField.TIMESTAMP.getQuery(QueryHandler.QueryType.RANGE));
                        } else if (QueryHandler.SolrField.TIMESTAMP.isSolrFieldSupported() && randomPercent == 6) {
                            solrQuery.setFields(
                                    QueryHandler.SolrField.getRandomValue().name().toLowerCase(),
                                    QueryHandler.SolrField.getRandomValue().name().toLowerCase(),
                                    QueryHandler.SolrField.getRandomValue().name().toLowerCase()
                            );
                        } else if (QueryHandler.SolrField.USERNAME.isSolrFieldSupported() && randomPercent == 7) {
                            solrQuery.setFacetLimit(10);
                            solrQuery.addFacetField(QueryHandler.SolrField.USERNAME.name().toLowerCase());
                        } else {
                            // Do nothing
                        }
                    }

                    if (percentageUsableClientSideQueryCache != 0) {
                        listOfClientSideQueryCacheForCurrentWorker.add(solrQuery);
                    }
                }

                long intendedStartTimeForCurrentQuery = throughputController.blockUntilIntendedStartTimeOfNextOperation();
                if (!isStandaloneSolr) {
                    /*
                    final QueryResponse response = solrClient.query(solrCollection, solrQuery);
                    final long latency = response.getElapsedTime()
                    */

                    // NOTE:
                    // solrClient.query(...) internally does:
                    // 1) created updateRequest
                    // 2) Add the SolrInputDocument
                    // 3) Call the QueryRequest.process() method
                    //    3.1) Create QueryResponse object
                    //    3.2) Call SolrClient.request(...) method
                    //    3.3) Find out the elapsed time of step 3.2)
                    // 4) Send the response back
                    //
                    // We will do the above step ourselves
                    // Why ?
                    // QueryRequest.process() internally does elapsed time calculation and we can use
                    //     QueryResponse.getElapsedTime() method to get 'latency'. But this is in millis
                    // I want the elapsed time in nanoSec

                    final QueryRequest queryRequest = new QueryRequest(solrQuery);

                    final long queryStartTime = System.nanoTime();
                    final NamedList<Object> responseStuff = solrClient.request(queryRequest, solrCollection);
                    final long queryEndTime = System.nanoTime();

                    final long serviceTimeInNanos = queryEndTime - queryStartTime;
                    final long responseTimeInNanos = queryEndTime - intendedStartTimeForCurrentQuery;

                    final QueryResponse queryResponse = new QueryResponse();
                    queryResponse.setResponse(responseStuff);

                    //collectResponseStats(queryResponse);
                    if (queryWorkerStats != null) {
                        queryWorkerStats.collect(responseTimeInNanos, serviceTimeInNanos, queryResponse.getQTime());
                    }
                } else {
                    final long queryStartTime = System.nanoTime();
                    final QueryResponse queryResponse = solrClient.query(solrQuery);
                    final long queryEndTime = System.nanoTime();

                    final long serviceTimeInNanos = queryEndTime - queryStartTime;
                    final long responseTimeInNanos = queryEndTime - intendedStartTimeForCurrentQuery;

                    //collectResponseStats(queryResponse);
                    if (queryWorkerStats != null) {
                        queryWorkerStats.collect(responseTimeInNanos, serviceTimeInNanos, queryResponse.getQTime());
                    }
                }
            } catch (Exception e) {
                log.severe("Cause   : " + e.getCause());
                log.severe("Message : " + e.getMessage());
                e.printStackTrace();
            }
            now = System.nanoTime();
        } while (now - startTime < TimeUnit.SECONDS.toNanos(runDurationInSec));

        queryWorkerStats.setTotalRequestsSent(throughputController.getTotalOperationsCompletedSoFar());
        return throughputController.getTotalOperationsCompletedSoFar();
    }

//    public void collectResponseStats(QueryResponse queryResponse) {
//        for (int i = 0; i < queryResultSizeUpperBound.length; i++) {
//            if (!(queryResponse.getResults().getNumFound() > queryResultSizeUpperBound[i])) {
//                queryResultSizeHistogram[i] += 1;
//                break;
//            }
//
//            // Last bucket
//            if (i == (queryResultSizeUpperBound.length - 1)) {
//                queryResultSizeHistogram[i + 1] += 1;
//            }
//        }
//    }

    private static class RingBuffer<T> extends ArrayList<T> {

        final int maxCapacity;

        public RingBuffer(int initialCapacity) {
            super(initialCapacity);
            this.maxCapacity = initialCapacity;
        }

        @Override
        public boolean add(T t) {
            if (size() >= maxCapacity) {
                super.remove(0);
            }
            return super.add(t);
        }
    }
}
