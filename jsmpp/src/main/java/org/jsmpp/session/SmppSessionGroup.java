package org.jsmpp.session;

import org.jsmpp.PDUReader;
import org.jsmpp.PDUSender;
import org.jsmpp.session.connection.ConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A group of {@link SMPPSession}s sharing the same ThreadPoolExecutor for
 * handling {@link PDUProcessTask}s.
 *
 * @author ospyros
 */
public class SmppSessionGroup {
    private static final Logger logger = LoggerFactory.getLogger(SmppSessionGroup.class);

    private Object metadata;
    private String name;
    private int pduProcessorCoreDegree;
    private int pduProcessorMaxDegree;
    private int pduProcessorKeepAliveMillis;
    private int pduProccessorQueueCapacity;
    private final ThreadPoolExecutor pduExecutor;


    /**
     * Create a new session group with a shared {@link PDUProcessTask} executor
     * sized according to the supplied parameters.
     */
    public SmppSessionGroup(int pduProcessorCoreDegree, int pduProcessorMaxDegree,
                            int pduProccessorQueueCapacity) {
        this.pduExecutor = new ThreadPoolExecutor(
                pduProcessorCoreDegree,
                pduProcessorMaxDegree,
                pduProcessorKeepAliveMillis,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(pduProccessorQueueCapacity),
                PDUProcessTask.defaultRejectedExecutionHandler(pduProccessorQueueCapacity));
    }

    public SMPPSession createSession(String host, int port, BindParameter bindParam,
                                     PDUSender pduSender, PDUReader pduReader,
                                     ConnectionFactory connFactory) throws IOException {
        return new SMPPSession(host, port, bindParam, pduSender,
                pduReader, this.pduExecutor, connFactory);
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
        pduExecutor.shutdown();
        try {
            pduExecutor.awaitTermination( 1000 +
                    singleTaskTimeoutMillis *
                    (pduExecutor.getQueue().size() / Math.max(pduExecutor.getCorePoolSize(), 1)),
                    TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            logger.warn("Session group " + name + " interrupted while waiting for PDU executor pool to finish");
            Thread.currentThread().interrupt();
        }
    }

    public Object getMetadata() {
        return metadata;
    }

    public void setMetadata(Object metadata) {
        this.metadata = metadata;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
