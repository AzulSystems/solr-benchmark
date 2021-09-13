package org.loadgen.solr;

/**
 * @author deepakr
 */
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

public class ThroughputController {

    private final AtomicLong operationCounter = new AtomicLong();
    private final int throughputToAchieve;
    private final long intendedTimeBetweenSuccessiveOpsInNanos;

    private long operationStartTimeInNanos;

    private ThroughputController(int throughputToAchieve) {
        this.throughputToAchieve = throughputToAchieve;
        this.operationStartTimeInNanos = System.nanoTime();
        intendedTimeBetweenSuccessiveOpsInNanos = TimeUnit.SECONDS.toNanos(1) / throughputToAchieve;
    }

    public static ThroughputController getInstance(int throughputToAchieve) {
        return new ThroughputController(throughputToAchieve);
    }

    public int getThroughputToAchieve() {
        return throughputToAchieve;
    }

    public void markCurrentTimeAsOperationStartTime() {
        // by default, the start time is set at the instance creation time (in the constructor)
        // use this method to reset it
        this.operationStartTimeInNanos = System.nanoTime();
    }

    public long getOperationStartTimeInNanos() {
        return operationStartTimeInNanos;
    }

    public long getIntededStartTimeOfNthOperation(int n) {
        return operationStartTimeInNanos + n * intendedTimeBetweenSuccessiveOpsInNanos;
    }

    public long getTotalOperationsCompletedSoFar() {
        return operationCounter.get();
    }

    public long blockUntilIntendedStartTimeOfNextOperation() {
        long totalRequestsCompletedSoFar = operationCounter.getAndIncrement();
        long intendedStartTimeOfNextOperationInNanos = operationStartTimeInNanos + totalRequestsCompletedSoFar * intendedTimeBetweenSuccessiveOpsInNanos;

//        while(intendedStartTimeOfNextOperationInNanos < System.nanoTime()) {
//            totalRequestsCompletedSoFar = operationCounter.getAndIncrement();
//            intendedStartTimeOfNextOperationInNanos = operationStartTimeInNanos + totalRequestsCompletedSoFar * intendedTimeBetweenSuccessiveOpsInNanos;
//        }

        long now;
        while ((now = System.nanoTime()) < intendedStartTimeOfNextOperationInNanos) {
            LockSupport.parkNanos(intendedStartTimeOfNextOperationInNanos - now);
        }
        return intendedStartTimeOfNextOperationInNanos;
    }

    public void reset() {
        markCurrentTimeAsOperationStartTime();
        operationCounter.set(0);
    }
}
