package org.loadgen.solr;

/**
 * @author deepakr
 */
import org.apache.solr.client.solrj.SolrClient;

import java.util.concurrent.Callable;

public interface QueryWorker extends Callable {
    void setRunDurationInSec(long runDurationInSec);
    void addSolrClient(SolrClient solrClient);
    void setRateLimiter(ThroughputController rateLimiter);
    ThroughputController getRateLimiter();
    void setSolrCollection(String solrCollection);
    void setStandaloneSolr(boolean isStandaloneSolr);
    void setQueryWorkerStats(QueryWorkerStats queryWorkerStats);
    QueryWorkerStats getQueryWorkerStat();
    void closeClientConnections();
}
