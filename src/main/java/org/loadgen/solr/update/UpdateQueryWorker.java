package org.loadgen.solr.update;

/**
 * @author deepakr
 */
import org.loadgen.solr.QueryWorkerStats;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.util.NamedList;
import org.loadgen.solr.QueryWorker;
import org.loadgen.solr.ThroughputController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class UpdateQueryWorker implements QueryWorker {
    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(UpdateQueryWorker.class.getName());

    private static final Random random = new Random();

    private String solrCollection;
    private boolean isStandaloneSolr;
    private long runDurationInSec;
    private ThroughputController throughputController;
    private List<SolrClient> solrClientList;
    private QueryWorkerStats queryWorkerStats;
    // Update specific vars
    private List<String> solrDocumentIdList;

    public UpdateQueryWorker() {
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

    public void setSolrDocumentIdList(List<String> solrDocumentIdList) {
        this.solrDocumentIdList = solrDocumentIdList;
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

                SolrInputDocument solrInputDocument = getMeNewDocumentToUpdate();

                long intendedStartTimeForCurrentQuery = throughputController.blockUntilIntendedStartTimeOfNextOperation();
                if (!isStandaloneSolr) {
                    /*
                    UpdateResponse updateResponse = solrClient.add(solrCollection, solrInputDocument, 60000);
                    updateResponse.getElapsedTime();
                    */

                    // NOTE:
                    // solrClient.add(...) internally does:
                    // 1) create updateRequest
                    // 2) Add the SolrInputDocument
                    // 3) Call the UpdateRequest.process() method
                    //    3.1) Create UpdateResponse object
                    //    3.2) Call SolrClient.request(...) method
                    //    3.3) Find out the elapsed time of step 3.2)
                    // 4) Send the response back
                    //
                    // We will do the above step ourselves
                    // Why ?
                    // I want the elapsed time in nanoSec, but the API returns in milliSec

                    UpdateRequest updateRequest = new UpdateRequest();
                    //updateRequest.setCommitWithin(1);
                    updateRequest.setCommitWithin((int) TimeUnit.SECONDS.toMillis(30));
                    updateRequest.add(solrInputDocument);

                    final long queryStartTime = System.nanoTime();
                    final NamedList<Object> responseStuff = solrClient.request(updateRequest, solrCollection);
                    final long queryEndTime = System.nanoTime();

                    final long serviceTimeInNanos = queryEndTime - queryStartTime;
                    final long responseTimeInNanos = queryEndTime - intendedStartTimeForCurrentQuery;

                    final UpdateResponse updateResponse = new UpdateResponse();
                    updateResponse.setResponse(responseStuff);

                    if (queryWorkerStats != null) {
                        queryWorkerStats.collect(responseTimeInNanos, serviceTimeInNanos, updateResponse.getQTime());
                    }
                } else {
                    final long queryStartTime = System.nanoTime();
                    final UpdateResponse updateResponse = solrClient.add(solrInputDocument);
                    final long queryEndTime = System.nanoTime();

                    final long serviceTimeInNanos = queryEndTime - queryStartTime;
                    final long responseTimeInNanos = queryEndTime - intendedStartTimeForCurrentQuery;

                    if (queryWorkerStats != null) {
                        queryWorkerStats.collect(responseTimeInNanos, serviceTimeInNanos, updateResponse.getQTime());
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

    private SolrInputDocument getMeNewDocumentToUpdate() {
        String documentId = solrDocumentIdList.get(random.nextInt(solrDocumentIdList.size()));
        SolrInputDocument solrInputDocument = new SolrInputDocument();
        solrInputDocument.setField("sha1", Collections.singletonMap("set", System.nanoTime() + ""));
        solrInputDocument.setField("id", documentId);
        return solrInputDocument;
    }
}
