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
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

class QueryHandler {
    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(QueryHandler.class.getName());

    private static final Random random = new Random();

    private static final Map<String, Map<String, QueryFileData>> queryDetailsList = new HashMap<>();
    private static final boolean useListInsteadOfFile = Boolean.valueOf(System.getProperty("useQueryList", "true"));

    enum SolrField {
        TITLE,
        USERNAME,
        COMMENT,
        TEXT,
        NS,
        SHA1,
        TIMESTAMP,
        ID,
        MODEL;

        // Static fields
        private static final int length = SolrField.values().length;
        private static final List<SolrField> supportedSolrFields = new ArrayList<>();

        // Instances fields
        private final List<QueryType> supportedQueryTypes = new ArrayList<>();

        // Static methods
        static SolrField getRandomValue() {
            return SolrField.values()[random.nextInt(length)];
        }

        public static void addSupportedSolrFields(SolrField solrField) {
            supportedSolrFields.add(solrField);
        }

        private static SolrField getRandomSupportedSolrField() {
            return supportedSolrFields.get(random.nextInt(supportedSolrFields.size()));
        }

        // Instance methods

        // Use this method to add all the QueryType's that 'this' SolrField supports
        private void addSupportedQueryType(QueryType queryType) {
            this.supportedQueryTypes.add(queryType);
        }

        // Return all the QueryTypes supported by 'this' SolrField
        private QueryType getRandomSupportedQueryType() {
            return this.supportedQueryTypes.get(random.nextInt(supportedQueryTypes.size()));
        }

        private String getQuery() {
            return QueryHandler.getQuery(this.name(), this.getRandomSupportedQueryType().name());
        }

        public String getQuery(QueryType queryType) {
            return QueryHandler.getQuery(this.name(), queryType.name());
        }

        public boolean isSolrFieldSupported() {
            return supportedSolrFields.contains(this);
        }
    }

    enum QueryType {
        FIELD,
        PHRASE,
        PROXIMITY,
        WILDCARD,
        FUZZY,
        RANGE;

        // Static fields
        private static final int length = QueryType.values().length;
        private static final List<QueryType> supportedQueryTypes = new ArrayList<>();

        // Instances fields
        private final List<SolrField> supportedSolrFields = new ArrayList<>();

        // Static methods
        private static QueryType getRandomValue() {
            return QueryType.values()[random.nextInt(length)];
        }

        private static void addSupportedQueryType(QueryType queryType) {
            supportedQueryTypes.add(queryType);
        }

        private static QueryType getRandomSupportedQueryType() {
            return supportedQueryTypes.get(random.nextInt(supportedQueryTypes.size()));
        }

        // Instance methods

        // Use this method to add all the SolrField's that 'this' QueryType supports
        private void addSupportedSolrFields(SolrField solrField) {
            this.supportedSolrFields.add(solrField);
        }

        // Return all the SolrField supported by 'this' QueryType
        private SolrField getRandomSupportedSolrField() {
            return this.supportedSolrFields.get(random.nextInt(supportedSolrFields.size()));
        }

        private String getQuery() {
            return QueryHandler.getQuery(this.getRandomSupportedSolrField().name(), this.name());
        }

        public String getQuery(SolrField solrField) {
            return QueryHandler.getQuery(solrField.name(), this.name());
        }

        public boolean isQueryTypeSupported() {
            return supportedQueryTypes.contains(this);
        }
    }

    private static class QueryFileData {
        final File file;
        LineIterator lineIterator;
        final long maxUsableQueries;
        List<String> queryList;
        AtomicLong incrementingLongCounter = new AtomicLong(0);

        long queryCount;

        public QueryFileData(File file, LineIterator lineIterator, long maxUsableQueries) {
            this.file = file;
            this.lineIterator = lineIterator;
            this.maxUsableQueries = maxUsableQueries;

            this.queryList = new ArrayList<>();
            populateQueryList();

            queryCount = 0;
        }

        private void populateQueryList() {
            long localCount = 0;
            while (lineIterator.hasNext()) {
                queryList.add(lineIterator.next());
                localCount++;

                if ((maxUsableQueries != -1) && (localCount >= maxUsableQueries)) {
                    break;
                }
            }

            log.info(queryList.size() + " queries from queryFile : '" + file.getAbsolutePath() + "' will be used");
        }
    }

    protected static void registerQueryFile(File queryFile, long maxUsableQueries) throws IOException {
        if (!queryFile.exists()) {
            log.severe("The query file : " + queryFile.getAbsolutePath() + " not found");
            System.exit(1);
        }
        // I have named the files like this: <fieldName>_<queryType>.txt
        String[] fileNameTokens = queryFile.getName().replace(".txt", "").trim().split("_");
        String fieldName = fileNameTokens[0];
        String queryType = fileNameTokens[1];

        if (
                (!Arrays.asList(SolrField.values()).stream().map(x -> x.name()).collect(Collectors.toList()).contains(fieldName)) ||
                        (!Arrays.asList(QueryType.values()).stream().map(x -> x.name()).collect(Collectors.toList()).contains(queryType))
        ) {
            return;
        }

        if (!SolrField.supportedSolrFields.contains(SolrField.valueOf(fieldName))) {
            SolrField.valueOf(fieldName).addSupportedSolrFields(SolrField.valueOf(fieldName));
        }

        if (!QueryType.supportedQueryTypes.contains(QueryType.valueOf(queryType))) {
            QueryType.valueOf(queryType).addSupportedQueryType(QueryType.valueOf(queryType));
        }

        SolrField.valueOf(fieldName).addSupportedQueryType(QueryType.valueOf(queryType));
        QueryType.valueOf(queryType).addSupportedSolrFields(SolrField.valueOf(fieldName));


        if (!queryDetailsList.containsKey(fieldName)) {
            queryDetailsList.put(fieldName, new HashMap<>());
        }

        queryDetailsList.get(fieldName).put(queryType, new QueryFileData(
                        queryFile,
                        FileUtils.lineIterator(queryFile, "UTF-8"),
                        maxUsableQueries
                )
        );
    }

    static String getQuery() {
        // Select a random 'queryType', and extract a random 'solrField' that it supports
        // Note: Don't do the reverse. WHY ?
        //     Given a 'queryType', we definitely have at least 1 associated 'solrField'
        //     The same cannot be said about 'solrField', Ex: 'sha1' (AS OF NOW), is not used in any queries and
        //     hence this SolrField does not have any supported 'queryType'
        QueryType queryType = QueryType.getRandomSupportedQueryType();
        SolrField solrField = queryType.getRandomSupportedSolrField();

        return getQuery(solrField.name(), queryType.name());
    }

    private static String getQuery(String fieldName, String queryType) {

        QueryFileData queryFileData = queryDetailsList.get(fieldName).get(queryType);
        if (useListInsteadOfFile) {
            return queryFileData.queryList.get((int) (queryFileData.incrementingLongCounter.incrementAndGet() % queryFileData.queryList.size()));
        } else {
            LineIterator lineIterator = queryFileData.lineIterator;

            // If we have read all the lines in the query file or if the hit the limit on the number of query lines
            // that we are allowed to read in the given file, read the query file from the beginning
            if (
                    (!lineIterator.hasNext()) ||
                            (
                                    (queryFileData.maxUsableQueries != -1) && // First check if we have set maxUsableQueries limit
                                            (queryFileData.queryCount++ >= queryFileData.maxUsableQueries)
                            )
            ) {
                try {
                    synchronized (queryDetailsList) {
                        lineIterator.close();
                        lineIterator = FileUtils.lineIterator(queryFileData.file, "UTF-8");
                        queryDetailsList.get(fieldName).get(queryType).lineIterator = lineIterator;
                    }
                    queryFileData.queryCount = 0; // reset the counter
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return lineIterator.nextLine();
        }

    }
}
