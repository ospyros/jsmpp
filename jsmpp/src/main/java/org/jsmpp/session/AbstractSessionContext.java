/*
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */
package org.jsmpp.session;

import org.jsmpp.bean.BindType;
import org.jsmpp.extra.SessionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author uudashr
 *
 */
public abstract class AbstractSessionContext implements SessionContext {
    private static final Logger logger = LoggerFactory.getLogger(AbstractSessionContext.class);
    private long lastActivityTimestamp;
    private List<SessionStateListener> sessionStateListeners = new CopyOnWriteArrayList<SessionStateListener>();
    private final ReentrantLock lock = new ReentrantLock();

    public AbstractSessionContext() {
    }
    
    public AbstractSessionContext(SessionStateListener sessionStateListener) {
        sessionStateListeners.add(sessionStateListener);
    }

    protected boolean lock(long timeout, TimeUnit timeUnit) throws InterruptedException {
        return lock.tryLock(timeout, timeUnit);
    }

    protected final void lock () {
        lock.lock();
    }

    protected final void unlock() {
        lock.unlock();
    }

    @Override
    public void open() {
        lock();
        try {
            changeState(SessionState.OPEN);
        } finally {
            unlock();
        }
    }

    @Override
    public boolean open(long timeout, TimeUnit timeUnit) throws InterruptedException {
        if (!lock(timeout, timeUnit)) {
            return false;
        }
        try {
            changeState(SessionState.OPEN);
        } finally {
            unlock();
        }
        return true;
    }

    @Override
    public void bound(BindType bindType) {
        lock();
        try {
            doBound(bindType);
        } finally {
            unlock();
        }
    }

    @Override
    public boolean bound(long timeout, TimeUnit timeUnit, BindType bindType) throws InterruptedException {
        if (!lock(timeout, timeUnit)) {
            return false;
        }
        try {
            doBound(bindType);
        } finally {
            unlock();
        }
        return true;
    }

    private void doBound(BindType bindType) {
        if (bindType.equals(BindType.BIND_TX)) {
            changeState(SessionState.BOUND_TX);
        } else if (bindType.equals(BindType.BIND_RX)) {
            changeState(SessionState.BOUND_RX);
        } else if (bindType.equals(BindType.BIND_TRX)) {
            changeState(SessionState.BOUND_TRX);
        } else {
            throw new IllegalArgumentException("Bind type " + bindType + " not supported");
        }
    }

    @Override
    public void unbound() {
        lock();  // block until condition holds
        try {
            doUnbound();
        } finally {
            unlock();
        }
    }

    @Override
    public boolean unbound(long timeout, TimeUnit timeUnit) throws InterruptedException {
        if (!lock(timeout, timeUnit)) {
            return false;
        }
        try {
            doUnbound();
        } finally {
            unlock();
        }
        return true;
    }

    private void doUnbound() {
        changeState(SessionState.UNBOUND);
    }

    @Override
    public void close() {
        lock();  // block until condition holds
        try {
            doClose();
        } finally {
            unlock();
        }
    }

    @Override
    public boolean close(long timeout, TimeUnit timeUnit) throws InterruptedException {
        if (!lock(timeout, timeUnit)) {
            return false;
        }
        try {
            doClose();
        } finally {
            unlock();
        }
        return true;
    }

    private void doClose() {
        changeState(SessionState.CLOSED);
    }

    public void addSessionStateListener(SessionStateListener listener) {
        sessionStateListeners.add(listener);
    }
    
    public void removeSessionStateListener(SessionStateListener l) {
        sessionStateListeners.remove(l);
    }

    protected void fireStateChanged(SessionState newState,
                                    SessionState oldState, Session source) {

        for (SessionStateListener l : sessionStateListeners) {
            if (newState.equals(oldState)){
                throw new IllegalStateException("State is already " + newState);
            }
            try {
                l.onStateChange(newState, oldState, source);
            } catch (Exception e) {
                logger.error("Invalid runtime exception thrown when calling onStateChange for {}", source, e);
            }
        }

    }
    
    public void notifyActivity() {
        lastActivityTimestamp = System.currentTimeMillis();
    }
    
    public long getLastActivityTimestamp() {
        return lastActivityTimestamp;
    }

    protected abstract void changeState(SessionState newState);
}
