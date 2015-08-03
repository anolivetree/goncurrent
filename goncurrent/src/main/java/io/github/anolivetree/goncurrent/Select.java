package io.github.anolivetree.goncurrent;

import java.util.ArrayList;
import java.util.Random;

public class Select {

    private ArrayList<Chan> mChan = new ArrayList<Chan>();
    private ArrayList<Object> mSendData = new ArrayList<Object>();

    private Object mData;
    private final Random rand = new Random();

    /**
     * Add a channel to receive from.
     * @param chan
     * @return
     */
    public Select receive(Chan chan) {
        mChan.add(chan);
        mSendData.add(ThreadContext.sReceiveFlag);
        return this;
    }

    /**
     * Add a channel to send to and a data. Passing closed channel will cause an Exception on select().
     * @param chan
     * @param data
     * @return
     */
    public Select send(Chan chan, Object data) {
        mChan.add(chan);
        mSendData.add(data);
        return this;
    }

    /**
     *
     * @return index of the channel read or written. -1 when interrupted.
     */
    public int select() {
        return selectInternal(false);
    }

    /**
     *
     * @return index of the channel read or written. -1 when no channel is ready.
     */
    public int selectNonblock() {
        return selectInternal(true);
    }

    private int selectInternal(boolean nonblock) {
        mData = null;
        Chan.sLock.lock();

        ThreadContext context = null;
        try {
            context = ThreadContext.get();
            if (Config.DEBUG_PRINT) {
                System.out.println("call select context=" + context);
            }
            if (Config.DEBUG_CHECK_STATE) {
                context.ensureHasNoChan();
            }
            context.setChanAndData(mChan, mSendData);
            if (Config.DEBUG_PRINT) {
                System.out.println(" nchan = " + mChan.size());
            }
            mChan = new ArrayList<Chan>();
            mSendData = new ArrayList<Object>();

            while (true) {

                // find a channel which is ready to send or receive
                int index = findAvailableChanRandomAndProcess(context);
                if (index >= 0) {
                    if (Config.DEBUG_PRINT) {
                        System.out.printf("select: found available chan. i=%d\n", index);
                    }
                    return index;
                }

                if (nonblock) {
                    return -1;
                }

                // no channel is available. Add self to waiting list of all channels.
                addToAllChan(context);

                // wait
                try {
                    if (Config.DEBUG_PRINT) {
                        System.out.printf("select: waiting\n");
                    }
                    context.mCond.await();
                    if (Config.DEBUG_PRINT) {
                        System.out.printf("select: woken up\n");
                    }
                } catch (InterruptedException e) {
                    if (Config.DEBUG_PRINT) {
                        System.out.printf("select: interrupted\n");
                    }
                    context.removeFromAllChannel();
                    return -1;
                }

                // woken up by someone. might be close()

                if (context.mUnblockedChanIndex == -1) {
                    // wokenup by close()
                    if (Config.DEBUG_PRINT) {
                        System.out.printf("select: woken up by close(). remove context from all channel\n");
                    }
                    context.removeFromAllChannel();
                    continue;
                }

                // no need to call context.removeFromAllChannle() because it is called by a peer
                if (context.mChan.size() > 0) {
                    throw new RuntimeException("chan exist.");
                }

                mData = context.mReceivedData;
                int mTargetIndex = context.mUnblockedChanIndex;
                if (mTargetIndex == -1) {
                    throw new RuntimeException("illegal state");
                }
                return mTargetIndex;
            }

        } finally {
            context.clearChan();
            Chan.sLock.unlock();
        }
    }


    private int findAvailableChanRandomAndProcess(ThreadContext context) {
        int numChan = context.mChan.size();
        for (int n = 0, i = rand.nextInt(numChan); n < numChan; n++) {
            Chan ch = context.mChan.get(i);
            if (ch != null) {
                Object data = context.mSendData.get(i);
                if (data == ThreadContext.sReceiveFlag) {
                    Object peek = ch.receive(true);
                    if (peek != null) {
                        mData = ((Chan.Result)peek).data;
                        return i;
                    }
                } else {
                    boolean dontBlock = ch.send(data, true);
                    if (dontBlock) {
                        mData = null;
                        return i;
                    }
                }
            }

            i++;
            if (i >= numChan) {
                i = 0;
            }
        }
        return -1;
    }

    private void addToAllChan(ThreadContext context) {
        if (Config.DEBUG_PRINT) {
            System.out.println("select: adding context to all channels");
        }
        int numChan = context.mChan.size();
        for (int i = 0; i < numChan; i++) {
            Chan ch = context.mChan.get(i);
            if (Config.DEBUG_PRINT) {
                System.out.println(" ch=" + ch);
            }
            if (ch == null) {
                continue;
            }
            Object data = context.mSendData.get(i);
            if (data == ThreadContext.sReceiveFlag) {
                if (Config.DEBUG_PRINT) {
                    System.out.printf("  added to receiverList\n");
                }
                ch.addToReceiverList(context);
            } else {
                if (Config.DEBUG_PRINT) {
                    System.out.printf("  added to senderList\n");
                }
                ch.addToSenderList(context);
            }
        }
    }

    /**
     * Get received data. Call this after select() returns.
     * @return
     */
    public Object getData() {
        return mData;
    }

}
