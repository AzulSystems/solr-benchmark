import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonStreamParser;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.common.SolrInputDocument;

import java.io.*;
import java.util.Map;

/**
 * @author deepakr
 */
public class Upload {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static InputStream inputStream;
    private static JsonStreamParser jsonStreamParser;

    private static final String solrCollection = "test";
    private static final int commitWithinInMillis = 10000;
    private static final String hostnamePortList = System.getProperty("hp", "localhost:8983");

    private static final boolean isStandaloneSolr = hostnamePortList.contains("8983");
    private static final int NUM_OF_THREADS = Integer.getInteger("t", 5);
    private static final SolrClient[] solrClients = new SolrClient[NUM_OF_THREADS];

    private static int count = 0;

    static {
        if (!isStandaloneSolr) {
            for (int i = 0; i < NUM_OF_THREADS; i++) {
                solrClients[i] = new CloudSolrClient.Builder()
                        .withZkHost(hostnamePortList)
                        .build();
            }
        } else {
            for (int i = 0; i < NUM_OF_THREADS; i++) {
                solrClients[i] = new HttpSolrClient
                        .Builder("http://" + hostnamePortList + "/solr/" + solrCollection)
                        .build();
            }
        }
    }

    public static void main(String[] args) throws IOException, SolrServerException, InterruptedException {
        if (args.length != 1) {
            System.err.println("First argument must be json file to be indexed in Solr");
            System.exit(3);
        }
        final File inputFile = new File(args[0]);
        inputStream = new FileInputStream(inputFile);
        jsonStreamParser = new JsonStreamParser(new InputStreamReader(inputStream));

        Thread[] threadsArray = new Thread[NUM_OF_THREADS];
        for (int i = 0; i < NUM_OF_THREADS; i++) {
            final int clientIndex = i;
            threadsArray[i] = new Thread(() -> {
                while (true) {
                    SolrInputDocument solrInputDocument = getMeTheNextSolrInputDoc();
                    if (solrInputDocument == null) break;
                    if (!isStandaloneSolr) {
                        UpdateRequest updateRequest = new UpdateRequest();
                        updateRequest.setCommitWithin(commitWithinInMillis);
                        updateRequest.add(solrInputDocument);
                        try {
                            solrClients[clientIndex].request(updateRequest, solrCollection);
                        } catch (SolrServerException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    } else {
                        try {
                            solrClients[clientIndex].add(solrInputDocument, commitWithinInMillis);
                        } catch (SolrServerException e) {
                            System.out.println(e.getCause());
                        } catch (IOException e) {
                            System.out.println(e.getCause());
                        }
                    }
                }
            });

            threadsArray[i].start();
        }

        for (int j = 0; j < NUM_OF_THREADS; j++) {
            threadsArray[j].join();
        }
    }

    private static Map<String, String> getMeTheNextJsonDoc() {
        synchronized (jsonStreamParser) {
            if (((count ++) % 10000) == 0) {
                System.out.println("Number of docs indexed so far : " + count + " (out of ~12.8M docs)");
            }
            SolrInputDocument solrInputDocument = new SolrInputDocument();
            while (jsonStreamParser.hasNext()) {
                JsonElement e = jsonStreamParser.next();
                if (e.isJsonObject()) {
                    return gson.fromJson(e, Map.class);
                }
            }
            return null;
        }
    }

    private static SolrInputDocument getMeTheNextSolrInputDoc() {
        Map<String, String> jsonDoc = getMeTheNextJsonDoc();
        if (jsonDoc == null) return null;

        SolrInputDocument solrInputDocument = new SolrInputDocument();
        for (String key : jsonDoc.keySet()) {
            solrInputDocument.addField(key, jsonDoc.get(key));
        }
        return solrInputDocument;
    }
}
