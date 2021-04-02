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

import java.util.concurrent.TimeUnit;

/**
 * Context defined session life cycle.
 *
 * Possible states:
 * OPEN -&gt; BOUND_TX | BOUND_RX | BOUND_TRX -&gt; UNBOUND -&gt; CLOSED.
 * 
 * @author uudashr
 *
 */
public interface SessionContext extends ActivityNotifier {
    /**
     * Change state to open.
     */
    void open();

    /**
     * Change state to open if the session lock is not held by another thread within the given waiting time
     * and the current thread has not been interrupted.
     */
    boolean open(long timeout, TimeUnit unit) throws InterruptedException;
    
    /**
     * Change state to bound state.
     * @param bindType the bindType enum
     */
    void bound(BindType bindType);

    /**
     * Change state to bound state if the session lock is not held by another thread within the given waiting time
     * and the current thread has not been interrupted.
     * @param bindType the bindType enum
     */
    boolean bound(long timeout, TimeUnit unit, BindType bindType) throws InterruptedException;

    /**
     * Change state to unbound.
     */
    void unbound();

    /**
     * Change state to unbound if the session lock is not held by another thread within the given waiting time
     * and the current thread has not been interrupted.
     */
    boolean unbound(long timeout, TimeUnit unit) throws InterruptedException;

    /**
     * Change state to close.
     */
    void close();

    /**
     * Change state to close if the session lock is not held by another thread within the given waiting time
     * and the current thread has not been interrupted.
     */
    boolean close(long timeout, TimeUnit unit) throws InterruptedException;


    /**
     * Get current session state.
     *
     * @return the current session state
     */
    SessionState getSessionState();

    /**
     * Get current session state if the session lock is not held by another thread within the given waiting time
     * and the current thread has not been interrupted, otherwise returns null.
     *
     *
     *
     * @return the current session state if the session lock could be acquired, null otherwise.
     */
    SessionState getSessionState(long timeout, TimeUnit unit) throws InterruptedException;

    /**
     * Get the last activity of a session.
     * 
     * @return the last activity timestamp
     */
    long getLastActivityTimestamp();
}