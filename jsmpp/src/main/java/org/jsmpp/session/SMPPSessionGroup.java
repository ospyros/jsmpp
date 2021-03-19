package org.jsmpp.session;

import org.jsmpp.*;
import org.jsmpp.session.connection.ConnectionFactory;
import org.jsmpp.util.DefaultComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A group of {@link SMPPSession}s sharing the same ThreadPoolExecutor for
 * handling {@link PDUProcessTask}s.
 *
 * @author ospyros
 */
public class SMPPSessionGroup {
    private static final Logger logger = LoggerFactory.getLogger(SMPPSessionGroup.class);
    private static final int DEFAULT_SINGLE_TASK_TIMEOUT_MILLIS = 500;
    private static final int DEFAULT_PDU_PROCESSOR_CORE_DEGREE = 1;
    private static final int DEFAULT_PDU_PROCESSOR_MAX_DEGREE = 8;
    private static final long DEFAULT_PDU_PROCESSOR_KEPP_ALIVE_MILLIS = 200;
    private static final int DEFAULT_PDU_PROCESSOR_QUEUE_CAPACITY = 50;

    private String id;
    private Object metadata;
    private int pduProcessorCoreDegree = DEFAULT_PDU_PROCESSOR_CORE_DEGREE;
    private int pduProcessorMaxDegree = DEFAULT_PDU_PROCESSOR_MAX_DEGREE;
    private long pduProcessorKeepAliveMillis = DEFAULT_PDU_PROCESSOR_KEPP_ALIVE_MILLIS;
    private int pduProccessorQueueCapacity = DEFAULT_PDU_PROCESSOR_QUEUE_CAPACITY;
    private final ThreadPoolExecutor pduExecutor;
    private final StatsLogger statsLogger = new StatsLogger();
    private final Thread statsThread = new Thread(statsLogger);

    private final class StatsLogger implements Runnable {
        private AtomicBoolean stop = new AtomicBoolean(false);
        private ThreadPoolExecutor executor;

        @Override
        public void run() {
            do {
                try {
                    Thread.sleep(10000);
                    logger.info("Executor active count is {}, completedTaskCount is {}, queue size is {}",
                            executor.getActiveCount(),
                            executor.getCompletedTaskCount(),
                            executor.getQueue().size());
                } catch(InterruptedException ex) {
                    logger.warn("Interrupted");
                }
            } while (!stop.get());
        }
    };

    /**
     * Create a new session group with a shared {@link PDUProcessTask} executor
     * sized according to the default (really modest) parameters.
     * <p>
     * This constructor is mostly used as a fallback option when no configuration
     * is found for a group and in general should be avoided.
     */
    public SMPPSessionGroup() {
        this.pduExecutor = new ThreadPoolExecutor(
                DEFAULT_PDU_PROCESSOR_CORE_DEGREE,
                DEFAULT_PDU_PROCESSOR_MAX_DEGREE,
                DEFAULT_PDU_PROCESSOR_KEPP_ALIVE_MILLIS,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(DEFAULT_PDU_PROCESSOR_QUEUE_CAPACITY),
                PDUProcessTask.defaultRejectedExecutionHandler(DEFAULT_PDU_PROCESSOR_QUEUE_CAPACITY));
        if (logger.isDebugEnabled()) {
            statsLogger.executor = pduExecutor;
            statsThread.setDaemon(true);
            statsThread.start();
        }
    }

    /**
     * Create a new session group with a shared {@link PDUProcessTask} executor
     * sized according to the supplied parameters.
     */
    public SMPPSessionGroup(int pduProcessorCoreDegree, int pduProcessorMaxDegree,
                            int pduProccessorQueueCapacity) {
        this.pduProcessorCoreDegree = pduProcessorCoreDegree;
        this.pduProcessorMaxDegree = pduProcessorMaxDegree;
        this.pduProccessorQueueCapacity = pduProccessorQueueCapacity;

        this.pduExecutor = new ThreadPoolExecutor(
                pduProcessorCoreDegree,
                pduProcessorMaxDegree,
                pduProcessorKeepAliveMillis,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(pduProccessorQueueCapacity),
                PDUProcessTask.defaultRejectedExecutionHandler(pduProccessorQueueCapacity));
        if (logger.isDebugEnabled()) {
            statsLogger.executor = pduExecutor;
            statsThread.setDaemon(true);
            statsThread.start();
        }
    }

    public SMPPSession createSession(ConnectionFactory connFactory) {
        return new SMPPSession(
                new SynchronizedPDUSender(new DefaultPDUSender(new DefaultComposer())),
                new DefaultPDUReader(),
                this.pduExecutor,
                connFactory);
    }

    public SMPPSession createSession(PDUSender pduSender, PDUReader pduReader,
                                     ConnectionFactory connFactory) {
        return new SMPPSession(pduSender, pduReader, this.pduExecutor, connFactory);
    }

    public SMPPSession createSession(String host, int port, BindParameter bindParam,
                                     PDUSender pduSender, PDUReader pduReader,
                                     ConnectionFactory connFactory) throws IOException {
        return new SMPPSession(host, port, bindParam, pduSender,
                pduReader, this.pduExecutor, connFactory);
    }

    /**
     * Shutdown this group (i.e. its task executor) waiting at most
     * 1000 + 500 *  (queued tasks count / core pool size) milliseconds.
     */
    public void shutdown() {
        shutdown(DEFAULT_SINGLE_TASK_TIMEOUT_MILLIS);
    }

    /**
     * Shutdown this group (i.e. its task executor) waiting at most
     * singleTaskTimeoutMillis *  (queued tasks count / core pool size) + 1000
     * milliseconds.
     *
     * @param singleTaskTimeoutMillis
     */
    public void shutdown(long singleTaskTimeoutMillis) {
        logger.debug("Shutting down session group executor");
        statsLogger.stop.set(true);
        pduExecutor.shutdown();
        try {
            pduExecutor.awaitTermination(1000 +
                            singleTaskTimeoutMillis *
                                    (pduExecutor.getQueue().size() / Math.max(pduExecutor.getCorePoolSize(), 1)),
                    TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            logger.warn("Session group " + id + " interrupted while waiting for PDU executor pool to finish");
            Thread.currentThread().interrupt();
        }
    }

    public Object getMetadata() {
        return metadata;
    }

    public void setMetadata(Object metadata) {
        this.metadata = metadata;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
