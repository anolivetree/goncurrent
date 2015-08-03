package io.github.anolivetree.goncurrent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.locks.ReentrantLock;

public class Chan<T> implements Iterable<T> {

    static final ReentrantLock sLock = new ReentrantLock();
    static final Object sWouldBlock = new Object();

    static public <T> Chan<T> create(int depth) {
        return new Chan<T>(depth);
    }

    static public class Result<T> {
        public final T data;
        public final boolean ok;

        public Result(T data, boolean ok) {
            this.data = data;
            this.ok = ok;
        }
    }

    private final int mDepth;
    private final ArrayList<ThreadContext> mReceivers = new ArrayList<ThreadContext>();
    private final T[] mData;
    private final ArrayList<ThreadContext> mSenders = new ArrayList<ThreadContext>();

    private boolean mIsClosed = false;
    private T mEnd = null;
    private int mDataR;
    private int mDataW;
    private int mDataNum;

    private Chan(int depth) {
        if (depth < 0) {
            throw new IllegalArgumentException("depth < 0");
        }
        mDepth = depth;
        mData = (T[])new Object[depth];
        mDataNum = 0;
        mDataR = 0;
        mDataW = 0;
    }

    public void send(T data) {
        sLock.lock();
        try {
            if (Config.DEBUG_CHECK_STATE) {
                ThreadContext.get().ensureHasNoChan();
            }
            send(data, false);
        } finally {
            sLock.unlock();
        }
    }

    /**
     *
     * @return false if it would block. Even when it's true, it might mean that the thread is interrupted.
     */
    boolean send(T data, boolean nonblock) {
        while (true) {

            // Go's implementation forbids sending even though a goroutine has been waiting to send before close() is called.
            if (mIsClosed) {
                throw new RuntimeException("send on closed channel");
            }

            // try to make space in the queue
            while (mReceivers.size() > 0 && mDataNum > 0) {
                // copy data from queue
                passDataToFirstReceiverAndWakeup(mData[mDataR]);
                mData[mDataR] = null;
                mDataNum--;
                mDataR++;
                if (mDataR >= mDepth) {
                    mDataR = 0;
                }
            }

            // no more receivers || no more data || no receiver && no data
            if (mReceivers.size() > 0) {
                // pass data directly to a receiver
                passDataToFirstReceiverAndWakeup(data);
                return true;
            }

            if (mDataNum < mDepth) {
                // copy data to the queue
                mData[mDataW] = data;
                mDataNum++;
                mDataW++;
                if (mDataW >= mDepth) {
                    mDataW = 0;
                }
                return true;
            }

            if (nonblock) {
                return false;
            }

            // wait
            ThreadContext context = null;
            try {
                context = ThreadContext.get();
                if (Config.DEBUG_CHECK_STATE) {
                    context.ensureHasNoChan();
                }
                context.addSendChan(this, data);
                mSenders.add(context);

                try {
                    context.mCond.await();
                } catch (InterruptedException e) {
                    mSenders.remove(context);
                    return true;
                }

                boolean exist = mSenders.remove(context);
                if (exist) {
                    throw new RuntimeException("not removed from mSenders list");
                }

                if (context.mUnblockedChanIndex != -1) {
                    // woken up by receiver
                    return true;
                }
                // woken up by close.
            } finally {
                context.clearChan();
            }
        }
    }

    public T receive() {
        sLock.lock();
        try {
            if (Config.DEBUG_CHECK_STATE) {
                ThreadContext.get().ensureHasNoChan();
            }
            Result<T> result = receive(false);
            return result.data;
        } finally {
            sLock.unlock();
        }
    }

    public Result<T> receiveWithResult() {
        sLock.lock();
        try {
            if (Config.DEBUG_CHECK_STATE) {
                ThreadContext.get().ensureHasNoChan();
            }
            return receive(false);
        } finally {
            sLock.unlock();
        }
    }

    /**
     *
     * @return If closed, returns 'end' of close(T end) or null. When interrupted, returns null.
     */
    Result<T> receive(boolean nonblock) {
        while (true) {

            boolean hasRet = false;
            T data = null;

            // receive from queue
            if (mDataNum > 0) {
                data = mData[mDataR];
                hasRet = true;
                mData[mDataR] = null;
                mDataNum--;
                mDataR++;
                if (mDataR >= mDepth) {
                    mDataR = 0;
                }
            }

            // receive directly from the first sender
            if (!hasRet && mSenders.size() > 0) {
                if (Config.DEBUG_PRINT) {
                    System.out.printf("receive: receive directly from sender. numSenders=%d\n", mSenders.size());
                }
                data = removeAndWakeupFirstSender();
                hasRet = true;
            }

            // copy data from senders to the queue
            while (mSenders.size() > 0 && mDataNum < mDepth) {
                if (Config.DEBUG_PRINT) {
                    System.out.printf("receive: queue is some room. copy data from sender to the queue. numSenders=%d\n", mSenders.size());
                }
                mData[mDataW] = removeAndWakeupFirstSender();
                mDataNum++;
                mDataW++;
                if (mDataW >= mDepth) {
                    mDataW = 0;
                }
            }

            if (hasRet) {
                return new Result<T>(data, true);
            }

            if (mIsClosed) {
                return new Result<T>(mEnd, false);
            }

            if (nonblock) {
                return null;
            }

            ThreadContext context = null;
            try {
                context = ThreadContext.get();
                if (Config.DEBUG_CHECK_STATE) {
                    context.ensureHasNoChan();
                }
                context.addReceiveChan(this);
                if (Config.DEBUG_PRINT) {
                    System.out.println("add receiver " + context);
                }
                mReceivers.add(context);

                // Wait until there's a space in the queue or any sender appear.
                try {
                    context.mCond.await();
                } catch (InterruptedException e) {
                    boolean exist = mReceivers.remove(context);
                    if (Config.DEBUG_PRINT) {
                        System.out.println("remove context from receiver list context=" + context + " " + exist);
                    }
                    return new Result<T>(null, false);
                }

                if (Config.DEBUG_PRINT) {
                    System.out.println("receive: woken up");
                }
                boolean exist = mReceivers.remove(context);
                if (Config.DEBUG_PRINT) {
                    System.out.println("receive: remove context from receiver list context=" + context + " " + exist);
                }

                if (context.mUnblockedChanIndex != -1) {
                    data = (T) context.mReceivedData;
                    return new Result<T>(data, true);
                }
                // woken up by close()
            } finally {
                context.clearChan();
            }
        }
    }

    private void passDataToFirstReceiverAndWakeup(T data) {
        ThreadContext context = mReceivers.remove(0);
        if (Config.DEBUG_PRINT) {
            System.out.println("remove receiver(head) remain=" + mReceivers.size() + " " + context);
        }
        context.markReceiverUnblockedAndRemoveFromOtherChans(this, data);
        context.mCond.signal();
    }


    private T removeAndWakeupFirstSender() {
        ThreadContext context = mSenders.remove(0);
        T data = (T) context.markSenderUnblockedAnsRemoveFromOtherChans(this);
        context.mCond.signal();
        return data;
    }

    void addToSenderList(ThreadContext context) {
        mSenders.add(context);
        if (Config.DEBUG_PRINT) {
            System.out.println("add context to sender list (select) context=" + context + ", numSenders=" + mSenders.size());
        }
    }

    void removeFromSenderList(ThreadContext context) {
        mSenders.remove(context);
        if (Config.DEBUG_PRINT) {
            System.out.println("remove context from sender list (select) context=" + context);
        }
    }

    void addToReceiverList(ThreadContext context) {
        mReceivers.add(context);
        if (Config.DEBUG_PRINT) {
            System.out.println("add context to receiver list (select) context=" + context + ", numReceivers=" + mReceivers.size());
        }
    }

    void removeFromReceiverList(ThreadContext context) {
        boolean exist = mReceivers.remove(context);
        if (Config.DEBUG_PRINT) {
            System.out.println("remove context from receiver list(select) context=" + context + " " + exist);
        }
    }

    public void close() {
        close(null);
    }

    public void close(T end) {
        sLock.lock();
        if (!mIsClosed) {
            mIsClosed = true;
            mEnd = end;

            // wakeup receivers
            int size = mReceivers.size();
            for (int i = 0; i < size; i++) {
                mReceivers.get(i).mCond.signal();
            }
            // wakeup senders
            size = mSenders.size();
            for (int i = 0; i < size; i++) {
                mSenders.get(i).mCond.signal();
            }
        }
        sLock.unlock();
    }

    public int length() {
        sLock.lock();
        int ret = mDataNum;
        sLock.unlock();
        return ret;
    }

    public int capacity() {
        return mDepth;
    }

    @Override
    public Iterator<T> iterator() {
        if (Config.DEBUG_PRINT) {
            System.out.printf("iterator()\n");
        }
        return new ChanIterator<>(this);
    }

    static private class ChanIterator<T> implements Iterator<T> {

        final private Chan<T> mChan;
        private boolean mHasData = false;
        private T mData = null;

        public ChanIterator(Chan<T> chan) {
            mChan = chan;
        }

        @Override
        public boolean hasNext() {
            if (Config.DEBUG_PRINT) {
                System.out.printf("hasNext() called\n");
            }
            if (mHasData) {
                if (Config.DEBUG_PRINT) {
                    System.out.printf("hasNext() return true\n");
                }
                return true;
            }
            read();
            if (Config.DEBUG_PRINT) {
                System.out.printf("hasNext() return " + mHasData + "\n");
            }
            return mHasData;
        }

        @Override
        public T next() {
            if (!mHasData) {
                read();
            }
            if (mHasData) {
                T ret = mData;
                mHasData = false;
                mData = null;
                return ret;
            } else {
                return null;
            }
        }

        @Override
        public void remove() {
            throw new RuntimeException("remove() not supported");
        }

        private void read() {
            if (Config.DEBUG_PRINT) {
                System.out.printf("call receiveWithResult\n");
            }
            Result<T> result = mChan.receiveWithResult();
            if (result.ok) {
                mData = result.data;
                mHasData = true;
                if (Config.DEBUG_PRINT) {
                    System.out.printf("read ok. mData=" + mData + "\n");
                }
            } else {
                mData = null;
                mHasData = false;
                if (Config.DEBUG_PRINT) {
                    System.out.printf("read fail\n");
                }
            }
        }
    }

}
