/**
 * Copyright (C) 2015 Hiroshi Sakurai
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.anolivetree.goncurrent;

import java.util.ArrayList;
import java.util.concurrent.locks.Condition;

class ThreadContext {

    private static final ThreadLocal<ThreadContext> context =
            new ThreadLocal<ThreadContext>() {
                @Override protected ThreadContext initialValue() {
                    ThreadContext context = new ThreadContext();
                    return context;
                }
            };

    public static ThreadContext get() {
        return context.get();
    }

    static final Object sReceiveFlag = new Object();

    final Condition mCond;

    int mUnblockedChanIndex = -1;
    Object mReceivedData;

    ArrayList<Chan> mChan = new ArrayList<Chan>();
    ArrayList<Object> mSendData = new ArrayList<Object>();

    private ThreadContext() {
        mCond = Chan.sLock.newCondition();
    }

    /**
     * register send channel one by one
     * @param chan
     * @param data
     */
    void addSendChan(Chan chan, Object data) {
        mChan.add(chan);
        mSendData.add(data);
    }

    /**
     * register receive channel one by one
     * @param chan
     */
    void addReceiveChan(Chan chan) {
        mChan.add(chan);
        mSendData.add(sReceiveFlag);
    }

    /**
     * set send and receive channels
     * @param chan
     */
    void setChanAndData(ArrayList<Chan> chan, ArrayList<Object> data) {
        if (chan.size() != data.size()) {
            throw new RuntimeException("size differ");
        }
        mChan = chan;
        mSendData = data;
    }

    void markReceiverUnblockedAndRemoveFromOtherChans(Chan chan, Object data) {
        //System.out.println("context: remove from all channels " + this + " target ch =" + chan);
        boolean found = false;
        int numChan = mChan.size();
        for (int i = 0; i < numChan; i++) {
            Chan ch = mChan.get(i);
            if (ch == null) {
                continue;
            }
            //System.out.println("  ch=" + ch);
            if (ch == chan && mSendData.get(i) == sReceiveFlag && !found) {
                //System.out.println("  found");
                mReceivedData = data;
                mUnblockedChanIndex = i;
                found = true;
            } else {
                //System.out.println("  differ. sSendData.get(i)=" + mSendData.get(i) + " sReceiveFlag=" + sReceiveFlag);
                if (mSendData.get(i) == sReceiveFlag) {
                    ch.removeFromReceiverList(this);
                } else {
                    ch.removeFromSenderList(this);
                }
            }
        }
        if (!found) {
            throw new RuntimeException("chan not found in context context=" + this + " chan=" + chan);
        }
        mChan.clear();
        mSendData.clear();
    }

    Object markSenderUnblockedAnsRemoveFromOtherChans(Chan chan) {
        boolean found = false;
        Object receivedData = null;
        int numChan = mChan.size();
        for (int i = 0; i < numChan; i++) {
            Chan ch = mChan.get(i);
            if (Config.DEBUG_PRINT) {
                System.out.printf("markSenderUnblockedAnsRemoveFromOtherChans: ch=" + ch + " looking for=" + chan + "\n");
            }
            if (ch == null) {
                continue;
            }
            if (ch == chan && mSendData.get(i) != sReceiveFlag && !found) {
                if (Config.DEBUG_PRINT) {
                    System.out.printf("  found\n");
                }
                mReceivedData = null;
                mUnblockedChanIndex = i;
                receivedData = mSendData.get(i);
                found = true;
            } else {
                if (Config.DEBUG_PRINT) {
                    System.out.printf("  ignore\n");
                }
                if (mSendData.get(i) == sReceiveFlag) {
                    ch.removeFromReceiverList(this);
                } else {
                    ch.removeFromSenderList(this);
                }
            }
        }
        if (!found) {
            if (Config.DEBUG_PRINT) {
                System.out.printf("try to find ch=" + chan + ", numChan=%d\n", numChan);
                for (int i = 0; i < numChan; i++) {
                    Chan ch = mChan.get(i);
                    if (ch != null && mSendData.get(i) == sReceiveFlag) {
                        System.out.printf("ch(R)=" + ch + "\n");
                    } else if (ch != null && mSendData.get(i) != sReceiveFlag) {
                        System.out.printf("ch(S)=" + ch + "\n");
                    } else {
                        System.out.printf("ch(?)=" + ch + "\n");
                    }
                }
                System.out.printf("=================================\n");
                System.out.flush();
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            throw new RuntimeException("chan not found in context context=" + this + " chan=" + chan);
        }
        mChan.clear();
        mSendData.clear();
        return receivedData;
    }


    /**
     * remove this context from waiting list of all channels.
     */
    void removeFromAllChannel() {
        //System.out.println("context: remove from all channels " + this);
        int numChan = mChan.size();
        for (int i = 0; i < numChan; i++) {
            Chan ch = mChan.get(i);
            if (ch == null) {
                continue;
            }
            if (mSendData.get(i) == sReceiveFlag) {
                ch.removeFromReceiverList(this);
            } else {
                ch.removeFromSenderList(this);
            }
        }
    }

    void clearChan() {
        //System.out.println("context: clear channels " + this);
        //for (Chan ch : mChan) {
            //System.out.println(" ch:" + ch);
        //}

        mUnblockedChanIndex = -1;
        mReceivedData = null;
        mChan.clear();
        mSendData.clear();
    }

    /**
     * for debug
     */
    void ensureHasNoChan() {
        if (!Config.DEBUG_CHECK_STATE) {
            throw new RuntimeException("DEBUG_CHECK_STATE is false");
        }
        if (mChan.size() > 0 || mSendData.size() > 0 || mUnblockedChanIndex != -1 || mReceivedData != null) {
            throw new RuntimeException("illegal state");
        }

    }
}
