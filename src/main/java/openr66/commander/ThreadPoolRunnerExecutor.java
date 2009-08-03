/**
 * Copyright 2009, Frederic Bregier, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 3.0 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package openr66.commander;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import openr66.database.data.AbstractDbData;

/**
 * Thread Pool Executor for ClientRunner
 * @author Frederic Bregier
 *
 */
public class ThreadPoolRunnerExecutor extends ThreadPoolExecutor {

    /**
     * RejectedExecutionHandler for this ThreadPoolRunnerExecutor
     * @author Frederic Bregier
     *
     */
    private class RunnerRejectedExecutionHandler implements RejectedExecutionHandler {

        /* (non-Javadoc)
         * @see java.util.concurrent.RejectedExecutionHandler#rejectedExecution(java.lang.Runnable, java.util.concurrent.ThreadPoolExecutor)
         */
        @Override
        public void rejectedExecution(Runnable arg0, ThreadPoolExecutor arg1) {
            ClientRunner runner = (ClientRunner) arg0;
            runner.changeUpdatedInfo(AbstractDbData.UpdatedInfo.UPDATED);
        }

    }
    /**
     * @param corePoolSize
     * @param maximumPoolSize
     * @param keepAliveTime
     * @param unit
     * @param workQueue
     */
    public ThreadPoolRunnerExecutor(int corePoolSize, int maximumPoolSize,
            long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
        this.setRejectedExecutionHandler(new RunnerRejectedExecutionHandler());
    }

    /**
     * @param corePoolSize
     * @param maximumPoolSize
     * @param keepAliveTime
     * @param unit
     * @param workQueue
     * @param threadFactory
     */
    public ThreadPoolRunnerExecutor(int corePoolSize, int maximumPoolSize,
            long keepAliveTime, TimeUnit unit,
            BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
                threadFactory);
        this.setRejectedExecutionHandler(new RunnerRejectedExecutionHandler());
    }

    /**
     * @param corePoolSize
     * @param maximumPoolSize
     * @param keepAliveTime
     * @param unit
     * @param workQueue
     * @param handler
     */
    public ThreadPoolRunnerExecutor(int corePoolSize, int maximumPoolSize,
            long keepAliveTime, TimeUnit unit,
            BlockingQueue<Runnable> workQueue, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
                handler);
        this.setRejectedExecutionHandler(new RunnerRejectedExecutionHandler());
    }

    /**
     * @param corePoolSize
     * @param maximumPoolSize
     * @param keepAliveTime
     * @param unit
     * @param workQueue
     * @param threadFactory
     * @param handler
     */
    public ThreadPoolRunnerExecutor(int corePoolSize, int maximumPoolSize,
            long keepAliveTime, TimeUnit unit,
            BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory,
            RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
                threadFactory, handler);
        this.setRejectedExecutionHandler(new RunnerRejectedExecutionHandler());
    }

    /* (non-Javadoc)
     * @see java.util.concurrent.ThreadPoolExecutor#beforeExecute(java.lang.Thread, java.lang.Runnable)
     */
    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        ClientRunner runner = (ClientRunner) r;
        runner.changeUpdatedInfo(AbstractDbData.UpdatedInfo.NOTUPDATED);
    }

}