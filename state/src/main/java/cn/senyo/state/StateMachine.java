package cn.senyo.state;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;

/**
 * @author: Rony
 * @email: luojun@skyruler.cn
 * @date: Created 2021/1/5 15:45
 */
public class StateMachine {
    public static final String TAG = "StateMachine";
    /**
     * Message.what value when quitting
     */
    private static final int SM_QUIT_CMD = -1;

    /**
     * Message.what value when initializing
     */
    private static final int SM_INIT_CMD = -2;
    private HandlerThread mSmThread;
    private String mName;
    private SmHandler mSmHandler;
    private OnStateListener mStateListener;
    private boolean mIsDbg;

    private void initStateMachine(String name, Looper looper) {
        mName = name;
        mSmHandler = new SmHandler(looper, this);
    }

    protected StateMachine(String name) {
        mSmThread = new HandlerThread(name);
        mSmThread.start();
        Looper looper = mSmThread.getLooper();

        initStateMachine(name, looper);
    }

    protected StateMachine(String name, Looper looper) {
        initStateMachine(name, looper);
    }

    protected StateMachine(String name, @NonNull Handler handler) {
        initStateMachine(name, handler.getLooper());
    }

    public void setOnStateListener(OnStateListener stateListener) {
        mStateListener = stateListener;
    }

    private static class SmHandler extends Handler {
        /**
         * true if StateMachine has quit
         */
        private boolean mHasQuit = false;


        private StateMachine mStateMachine;
        private Message mMsg;
        private boolean mIsConstructionCompleted = false;
        private Object mSmHandlerObj = new Object();
        private int mStateStackTopIndex = -1;
        private StateInfo[] mStateStack;
        private StateInfo[] mTempStateStack;
        private boolean mTransitionInProgress = false;
        /**
         * State used when state machine is quitting
         */
        private QuittingState mQuittingState = new QuittingState(SM_QUIT_CMD);
        private int mTempStateStackCount;
        private boolean mIsDbg;


        private static class StateInfo {
            /**
             * The state
             */
            State state;

            /**
             * The parent of this state, null if there is no parent
             */
            StateInfo parentStateInfo;

            /**
             * True when the state has been entered and on the stack
             */
            boolean active;

            /**
             * Convert StateInfo to string
             */
            @NonNull
            @Override
            public String toString() {
                return "state=" + state.getName() + ",active=" + active + ",parent="
                        + ((parentStateInfo == null) ? "null" : parentStateInfo.state.getName());
            }
        }

        /**
         * State entered when a valid quit message is handled.
         */
        private static class QuittingState extends State {
            protected QuittingState(int code) {
                super(code);
            }

            @Override
            public boolean processMessage(@NonNull Message msg) {
                return NOT_HANDLED;
            }
        }


        /**
         * The map of all of the states in the state machine
         */
        private HashMap<State, StateInfo> mStateInfo = new HashMap<>();

        /**
         * The initial state that will process the first message
         */
        private State mInitialState;

        /**
         * The destination state when transitionTo has been invoked
         */
        private State mDestState;

        private SmHandler(Looper looper, StateMachine stateMachine) {
            super(looper);
            mStateMachine = stateMachine;
            addState(mQuittingState, null);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            if (!mHasQuit) {
                if (mStateMachine != null && msg.what != SM_INIT_CMD && msg.what != SM_QUIT_CMD) {
                    mStateMachine.onPreHandleMessage(msg);
                }
                if (mIsDbg) {
                    Log.d(TAG, "handleMessage: E msg.what=" + msg.what);
                }
                mMsg = msg;

                /* State that processed the message */
                State msgProcessedState = null;
                if (mIsConstructionCompleted || (mMsg.what == SM_QUIT_CMD)) {
                    /* Normal path */
                    msgProcessedState = processMsg(msg);
                } else if (!mIsConstructionCompleted && (mMsg.what == SM_INIT_CMD)
                        && (mMsg.obj == mSmHandlerObj)) {
                    /* Initial one time path. */
                    mIsConstructionCompleted = true;
                    invokeEnterMethods(0);
                } else {
                    throw new RuntimeException("StateMachine.handleMessage: "
                            + "The start method not called, received msg: " + msg);
                }
                performTransitions(msgProcessedState, msg);

                if (mStateMachine != null && msg.what != SM_INIT_CMD && msg.what != SM_QUIT_CMD) {
                    mStateMachine.onPostHandleMessage(msg);
                }
            }
        }

        public void setInitialState(@NonNull State initialState) {
            if (mIsDbg) {
                Log.d(TAG, "setInitialState: initialState=" + initialState.getName());
            }
            mInitialState = initialState;
        }

        /**
         * Cleanup all the static variables and the looper after the SM has been quit.
         */
        private final void cleanupAfterQuitting() {
            if (mStateMachine.mSmThread != null) {
                // If we made the thread then quit looper which stops the thread.
                getLooper().quit();
                mStateMachine.mSmThread = null;
            }

            mStateMachine.mSmHandler = null;
            mStateMachine = null;
            mMsg = null;
            mStateStack = null;
            mTempStateStack = null;
            mStateInfo.clear();
            mInitialState = null;
            mDestState = null;
            mHasQuit = true;
        }

        /**
         * Complete the construction of the state machine.
         */
        private final void completeConstruction() {
            if (mIsDbg) {
                Log.d(TAG, "completeConstruction: E");
            }

            /**
             * Determine the maximum depth of the state hierarchy
             * so we can allocate the state stacks.
             */
            int maxDepth = 0;
            for (StateInfo si : mStateInfo.values()) {
                int depth = 0;
                for (StateInfo i = si; i != null; depth++) {
                    i = i.parentStateInfo;
                }
                if (maxDepth < depth) {
                    maxDepth = depth;
                }
            }
            if (mIsDbg) {
                Log.d(TAG, "completeConstruction: maxDepth=" + maxDepth);
            }

            mStateStack = new StateInfo[maxDepth];
            mTempStateStack = new StateInfo[maxDepth];
            setupInitialStateStack();

            /** Sending SM_INIT_CMD message to invoke enter methods asynchronously */
            sendMessageAtFrontOfQueue(obtainMessage(SM_INIT_CMD, mSmHandlerObj));

            if (mIsDbg) {
                Log.d(TAG, "completeConstruction: X");
            }
        }

        /**
         * Initialize StateStack to mInitialState.
         */
        private final void setupInitialStateStack() {
            if (mIsDbg) {
                Log.d(TAG, "setupInitialStateStack: E mInitialState=" + mInitialState.getName());
            }

            StateInfo curStateInfo = mStateInfo.get(mInitialState);
            for (mTempStateStackCount = 0; curStateInfo != null; mTempStateStackCount++) {
                mTempStateStack[mTempStateStackCount] = curStateInfo;
                curStateInfo = curStateInfo.parentStateInfo;
            }

            // Empty the StateStack
            mStateStackTopIndex = -1;

            moveTempStateStackToStateStack();
        }

        /**
         * Move the contents of the temporary stack to the state stack
         * reversing the order of the items on the temporary stack as
         * they are moved.
         *
         * @return index into mStateStack where entering needs to start
         */
        private final int moveTempStateStackToStateStack() {
            int startingIndex = mStateStackTopIndex + 1;
            int i = mTempStateStackCount - 1;
            int j = startingIndex;
            while (i >= 0) {
                if (mIsDbg) {
                    Log.d(TAG, "moveTempStackToStateStack: i=" + i + ",j=" + j);
                }
                mStateStack[j] = mTempStateStack[i];
                j += 1;
                i -= 1;
            }

            mStateStackTopIndex = j - 1;
            if (mIsDbg) {
                Log.d(TAG, "moveTempStackToStateStack: X mStateStackTop=" + mStateStackTopIndex
                        + ",startingIndex=" + startingIndex + ",Top="
                        + mStateStack[mStateStackTopIndex].state.getName());
            }
            return startingIndex;
        }

        /**
         * Setup the mTempStateStack with the states we are going to enter.
         * <p>
         * This is found by searching up the destState's ancestors for a
         * state that is already active i.e. StateInfo.active == true.
         * The destStae and all of its inactive parents will be on the
         * TempStateStack as the list of states to enter.
         *
         * @return StateInfo of the common ancestor for the destState and
         * current state or null if there is no common parent.
         */
        private final StateInfo setupTempStateStackWithStatesToEnter(State destState) {
            /**
             * Search up the parent list of the destination state for an active
             * state. Use a do while() loop as the destState must always be entered
             * even if it is active. This can happen if we are exiting/entering
             * the current state.
             */
            mTempStateStackCount = 0;
            StateInfo curStateInfo = mStateInfo.get(destState);
            do {
                mTempStateStack[mTempStateStackCount++] = curStateInfo;
                if (curStateInfo == null) {
                    break;
                }
                curStateInfo = curStateInfo.parentStateInfo;
            } while ((curStateInfo != null) && !curStateInfo.active);

            if (mIsDbg) {
                Log.d(TAG, "setupTempStateStackWithStatesToEnter: X mTempStateStackCount="
                        + mTempStateStackCount + ",curStateInfo: " + curStateInfo);
            }
            return curStateInfo;
        }


        private void performTransitions(State msgProcessedState, Message msg) {
            State destState = mDestState;
            if (destState != null) {
                /**
                 * Process the transitions including transitions in the enter/exit methods
                 */
                while (true) {
                    if (mIsDbg) {
                        Log.d(TAG, "handleMessage: new destination call exit/enter");
                    }

                    /**
                     * Determine the states to exit and enter and return the
                     * common ancestor state of the enter/exit states. Then
                     * invoke the exit methods then the enter methods.
                     */
                    StateInfo commonStateInfo = setupTempStateStackWithStatesToEnter(destState);
                    // flag is cleared in invokeEnterMethods before entering the target state
                    mTransitionInProgress = true;
                    invokeExitMethods(commonStateInfo);
                    int stateStackEnteringIndex = moveTempStateStackToStateStack();
                    invokeEnterMethods(stateStackEnteringIndex);

//                    /**
//                     * Since we have transitioned to a new state we need to have
//                     * any deferred messages moved to the front of the message queue
//                     * so they will be processed before any other messages in the
//                     * message queue.
//                     */
//                    moveDeferredMessageAtFrontOfQueue();

                    if (destState != mDestState) {
                        // A new mDestState so continue looping
                        destState = mDestState;
                    } else {
                        // No change in mDestState so we're done
                        break;
                    }
                }
                mDestState = null;
            }

            /**
             * After processing all transitions check and
             * see if the last transition was to quit or halt.
             */
            if (destState != null) {
                if (destState == mQuittingState) {
                    /**
                     * Call onQuitting to let subclasses cleanup.
                     */
                    mStateMachine.onQuitting();
                    cleanupAfterQuitting();
                }
            }
        }

        private void invokeExitMethods(StateInfo commonStateInfo) {
            while ((mStateStackTopIndex >= 0)
                    && (mStateStack[mStateStackTopIndex] != commonStateInfo)) {
                State curState = mStateStack[mStateStackTopIndex].state;
                if (mIsDbg) {
                    Log.d(TAG, "invokeExitMethods: " + curState.getName());
                }
                curState.exit();
                mStateStack[mStateStackTopIndex].active = false;
                mStateStackTopIndex -= 1;
            }
        }

        private void invokeEnterMethods(int stateStackEnteringIndex) {
            for (int i = stateStackEnteringIndex; i <= mStateStackTopIndex; i++) {
                if (stateStackEnteringIndex == mStateStackTopIndex) {
                    // Last enter state for transition
                    mTransitionInProgress = false;
                }
                if (mIsDbg) {
                    Log.d(TAG, "invokeEnterMethods: " + mStateStack[i].state.getName());
                }
                mStateStack[i].state.enter();
                mStateStack[i].active = true;
            }
            // ensure flag set to false if no methods called
            mTransitionInProgress = false;
        }

        @Nullable
        private State processMsg(@NonNull Message msg) {
            StateInfo curStateInfo = mStateStack[mStateStackTopIndex];
            if (mIsDbg) {
                Log.d(TAG, "processMsg: " + curStateInfo.state.getName());
            }

            if (isQuit(msg)) {
                transitionTo(mQuittingState);
            } else {
                while (!curStateInfo.state.processMessage(msg)) {
                    /* Not processed */
                    curStateInfo = curStateInfo.parentStateInfo;
                    if (curStateInfo == null) {
                        /* No parents left so it's not handled  */
                        mStateMachine.unhandledMessage(msg);
                        break;
                    }
                    if (mIsDbg) {
                        Log.d(TAG, "processMsg: " + curStateInfo.state.getName());
                    }
                }
            }
            return (curStateInfo != null) ? curStateInfo.state : null;
        }

        /**
         * Add a new state to the state machine. Bottom up addition
         * of states is allowed but the same state may only exist
         * in one hierarchy.
         *
         * @param state  the state to add
         * @param parent the parent of state
         * @return stateInfo for this state
         */
        @NonNull
        private final StateInfo addState(@NonNull State state, @Nullable State parent) {
            if (mIsDbg) {
                Log.d(TAG, "addStateInternal: E state=" + state.getName() + ",parent="
                        + ((parent == null) ? "" : parent.getName()));
            }
            StateInfo parentStateInfo = null;
            if (parent != null) {
                parentStateInfo = mStateInfo.get(parent);
                if (parentStateInfo == null) {
                    // Recursively add our parent as it's not been added yet.
                    parentStateInfo = addState(parent, null);
                }
            }
            StateInfo stateInfo = mStateInfo.get(state);
            if (stateInfo == null) {
                stateInfo = new StateInfo();
                mStateInfo.put(state, stateInfo);
            }

            // Validate that we aren't adding the same state in two different hierarchies.
            if ((stateInfo.parentStateInfo != null)
                    && (stateInfo.parentStateInfo != parentStateInfo)) {
                throw new RuntimeException("state already added");
            }
            stateInfo.state = state;
            stateInfo.parentStateInfo = parentStateInfo;
            stateInfo.active = false;
            if (mIsDbg) {
                Log.d(TAG, "addStateInternal: X stateInfo: " + stateInfo);
            }
            return stateInfo;
        }

        /**
         * Validate that the message was sent by quit or quitNow.
         */
        private final boolean isQuit(@NonNull Message msg) {
            return (msg.what == SM_QUIT_CMD) && (msg.obj == mSmHandlerObj);
        }

        public void quit() {
            if (mIsDbg) {
                Log.d(TAG, "quit:");
            }
            sendMessage(obtainMessage(SM_QUIT_CMD, mSmHandlerObj));
        }

        public void quitNow() {
            if (mIsDbg) {
                Log.d(TAG, "quitNow:");
            }
            sendMessageAtFrontOfQueue(obtainMessage(SM_QUIT_CMD, mSmHandlerObj));
        }

        /**
         * @see StateMachine#transitionTo(IState)
         */
        private final void transitionTo(IState destState) {
            if (mTransitionInProgress) {
                Log.wtf(TAG, "transitionTo called while transition already in progress to " +
                        mDestState + ", new target state=" + destState);
            }
            mDestState = (State) destState;
            if (mIsDbg) {
                Log.d(TAG, "transitionTo: destState=" + mDestState.getName());
            }
        }

        /**
         * @return current state
         */
        private final IState getCurrentState() {
            return mStateStack[mStateStackTopIndex].state;
        }

        public void setDbg(boolean dbg) {
            mIsDbg = dbg;
        }
    }

    /**
     * @return the name
     */
    public final String getName() {
        return mName;
    }

    private void onQuitting() {

    }

    private void unhandledMessage(@NonNull Message msg) {
        Log.e(TAG, " - unhandledMessage: msg.what=" + msg.what);
    }

    protected void onPreHandleMessage(Message msg) {
    }

    protected void onPostHandleMessage(Message msg) {
    }

    protected void transitionTo(@NonNull IState destState) {
        transitionTo(destState, destState.getCode());
    }

    protected void transitionTo(@NonNull IState destState, int stateEvent) {
        transitionTo(destState, obtainMessage(stateEvent));
    }

    protected void transitionTo(@NonNull IState destState, @NonNull Message msg) {
        mSmHandler.transitionTo(destState);
        if (destState.getCode() == SM_QUIT_CMD || destState.getCode() == SM_INIT_CMD) {
            return;
        }
        if (mStateListener != null) {
            mStateListener.onStateChanged(destState);
        }
        sendMessage(msg);
    }

    /**
     * Set the initial state. This must be invoked before
     * and messages are sent to the state machine.
     *
     * @param initialState is the state which will receive the first message.
     */
    public final void setInitialState(State initialState) {
        mSmHandler.setInitialState(initialState);
    }

    /**
     * Add a new state to the state machine
     *
     * @param state  the state to add
     * @param parent the parent of state
     */
    public final void addState(State state, State parent) {
        mSmHandler.addState(state, parent);
    }

    /**
     * Add a new state to the state machine, parent will be null
     *
     * @param state to add
     */
    public final void addState(State state) {
        mSmHandler.addState(state, null);
    }

    /**
     * Quit the state machine after all currently queued up messages are processed.
     */
    public final void quit() {
        // mSmHandler can be null if the state machine is already stopped.
        SmHandler smh = mSmHandler;
        if (smh == null) {
            return;
        }

        smh.quit();
    }

    /**
     * Quit the state machine immediately all currently queued messages will be discarded.
     */
    public final void quitNow() {
        // mSmHandler can be null if the state machine is already stopped.
        SmHandler smh = mSmHandler;
        if (smh == null) {
            return;
        }

        smh.quitNow();
    }

    /**
     * Start the state machine.
     */
    public void start() {
        // mSmHandler can be null if the state machine has quit.
        SmHandler smh = mSmHandler;
        if (smh == null) {
            return;
        }

        /** Send the complete construction message */
        smh.completeConstruction();
    }


    /**
     * Get a message and set Message.target state machine handler.
     * <p>
     * Note: The handler can be null if the state machine has quit,
     * which means target will be null and may cause a AndroidRuntimeException
     * in MessageQueue#enqueMessage if sent directly or if sent using
     * StateMachine#sendMessage the message will just be ignored.
     *
     * @return A Message object from the global pool
     */
    public final Message obtainMessage() {
        return Message.obtain(mSmHandler);
    }

    /**
     * Get a message and set Message.target state machine handler, what.
     * <p>
     * Note: The handler can be null if the state machine has quit,
     * which means target will be null and may cause a AndroidRuntimeException
     * in MessageQueue#enqueMessage if sent directly or if sent using
     * StateMachine#sendMessage the message will just be ignored.
     *
     * @param what is the assigned to Message.what.
     * @return A Message object from the global pool
     */
    public final Message obtainMessage(int what) {
        return Message.obtain(mSmHandler, what);
    }

    /**
     * Get a message and set Message.target state machine handler,
     * what and obj.
     * <p>
     * Note: The handler can be null if the state machine has quit,
     * which means target will be null and may cause a AndroidRuntimeException
     * in MessageQueue#enqueMessage if sent directly or if sent using
     * StateMachine#sendMessage the message will just be ignored.
     *
     * @param what is the assigned to Message.what.
     * @param obj  is assigned to Message.obj.
     * @return A Message object from the global pool
     */
    public final Message obtainMessage(int what, Object obj) {
        return Message.obtain(mSmHandler, what, obj);
    }

    /**
     * Get a message and set Message.target state machine handler,
     * what, arg1 and arg2
     * <p>
     * Note: The handler can be null if the state machine has quit,
     * which means target will be null and may cause a AndroidRuntimeException
     * in MessageQueue#enqueMessage if sent directly or if sent using
     * StateMachine#sendMessage the message will just be ignored.
     *
     * @param what is assigned to Message.what
     * @param arg1 is assigned to Message.arg1
     * @return A Message object from the global pool
     */
    public final Message obtainMessage(int what, int arg1) {
        // use this obtain so we don't match the obtain(h, what, Object) method
        return Message.obtain(mSmHandler, what, arg1, 0);
    }

    /**
     * Get a message and set Message.target state machine handler,
     * what, arg1 and arg2
     * <p>
     * Note: The handler can be null if the state machine has quit,
     * which means target will be null and may cause a AndroidRuntimeException
     * in MessageQueue#enqueMessage if sent directly or if sent using
     * StateMachine#sendMessage the message will just be ignored.
     *
     * @param what is assigned to Message.what
     * @param arg1 is assigned to Message.arg1
     * @param arg2 is assigned to Message.arg2
     * @return A Message object from the global pool
     */
    public final Message obtainMessage(int what, int arg1, int arg2) {
        return Message.obtain(mSmHandler, what, arg1, arg2);
    }

    /**
     * Get a message and set Message.target state machine handler,
     * what, arg1, arg2 and obj
     * <p>
     * Note: The handler can be null if the state machine has quit,
     * which means target will be null and may cause a AndroidRuntimeException
     * in MessageQueue#enqueMessage if sent directly or if sent using
     * StateMachine#sendMessage the message will just be ignored.
     *
     * @param what is assigned to Message.what
     * @param arg1 is assigned to Message.arg1
     * @param arg2 is assigned to Message.arg2
     * @param obj  is assigned to Message.obj
     * @return A Message object from the global pool
     */
    public final Message obtainMessage(int what, int arg1, int arg2, Object obj) {
        return Message.obtain(mSmHandler, what, arg1, arg2, obj);
    }

    /**
     * Enqueue a message to this state machine.
     * <p>
     * Message is ignored if state machine has quit.
     */
    public void sendMessage(int what) {
        // mSmHandler can be null if the state machine has quit.
        SmHandler smh = mSmHandler;
        if (smh == null) {
            return;
        }

        smh.sendMessage(obtainMessage(what));
    }

    /**
     * Enqueue a message to this state machine.
     * <p>
     * Message is ignored if state machine has quit.
     */
    public void sendMessage(int what, Object obj) {
        // mSmHandler can be null if the state machine has quit.
        SmHandler smh = mSmHandler;
        if (smh == null) {
            return;
        }

        smh.sendMessage(obtainMessage(what, obj));
    }

    /**
     * Enqueue a message to this state machine.
     * <p>
     * Message is ignored if state machine has quit.
     */
    public void sendMessage(int what, int arg1) {
        // mSmHandler can be null if the state machine has quit.
        SmHandler smh = mSmHandler;
        if (smh == null) {
            return;
        }

        smh.sendMessage(obtainMessage(what, arg1));
    }

    /**
     * Enqueue a message to this state machine.
     * <p>
     * Message is ignored if state machine has quit.
     */
    public void sendMessage(int what, int arg1, int arg2) {
        // mSmHandler can be null if the state machine has quit.
        SmHandler smh = mSmHandler;
        if (smh == null) {
            return;
        }

        smh.sendMessage(obtainMessage(what, arg1, arg2));
    }

    /**
     * Enqueue a message to this state machine.
     * <p>
     * Message is ignored if state machine has quit.
     */
    public void sendMessage(int what, int arg1, int arg2, Object obj) {
        // mSmHandler can be null if the state machine has quit.
        SmHandler smh = mSmHandler;
        if (smh == null) {
            return;
        }

        smh.sendMessage(obtainMessage(what, arg1, arg2, obj));
    }

    /**
     * Enqueue a message to this state machine.
     * <p>
     * Message is ignored if state machine has quit.
     */
    public void sendMessage(Message msg) {
        // mSmHandler can be null if the state machine has quit.
        SmHandler smh = mSmHandler;
        if (smh == null) {
            return;
        }

        smh.sendMessage(msg);
    }


    /**
     * Enqueue a message to this state machine after a delay.
     * <p>
     * Message is ignored if state machine has quit.
     */
    public void sendMessageDelayed(int what, long delayMillis) {
        // mSmHandler can be null if the state machine has quit.
        SmHandler smh = mSmHandler;
        if (smh == null) {
            return;
        }

        smh.sendMessageDelayed(obtainMessage(what), delayMillis);
    }

    /**
     * Enqueue a message to this state machine after a delay.
     * <p>
     * Message is ignored if state machine has quit.
     */
    public void sendMessageDelayed(int what, Object obj, long delayMillis) {
        // mSmHandler can be null if the state machine has quit.
        SmHandler smh = mSmHandler;
        if (smh == null) {
            return;
        }

        smh.sendMessageDelayed(obtainMessage(what, obj), delayMillis);
    }

    /**
     * Enqueue a message to this state machine after a delay.
     * <p>
     * Message is ignored if state machine has quit.
     */
    public void sendMessageDelayed(int what, int arg1, long delayMillis) {
        // mSmHandler can be null if the state machine has quit.
        SmHandler smh = mSmHandler;
        if (smh == null) {
            return;
        }

        smh.sendMessageDelayed(obtainMessage(what, arg1), delayMillis);
    }

    /**
     * Enqueue a message to this state machine after a delay.
     * <p>
     * Message is ignored if state machine has quit.
     */
    public void sendMessageDelayed(int what, int arg1, int arg2, long delayMillis) {
        // mSmHandler can be null if the state machine has quit.
        SmHandler smh = mSmHandler;
        if (smh == null) {
            return;
        }

        smh.sendMessageDelayed(obtainMessage(what, arg1, arg2), delayMillis);
    }

    /**
     * Enqueue a message to this state machine after a delay.
     * <p>
     * Message is ignored if state machine has quit.
     */
    public void sendMessageDelayed(int what, int arg1, int arg2, Object obj,
                                   long delayMillis) {
        // mSmHandler can be null if the state machine has quit.
        SmHandler smh = mSmHandler;
        if (smh == null) {
            return;
        }

        smh.sendMessageDelayed(obtainMessage(what, arg1, arg2, obj), delayMillis);
    }

    /**
     * Enqueue a message to this state machine after a delay.
     * <p>
     * Message is ignored if state machine has quit.
     */
    public void sendMessageDelayed(Message msg, long delayMillis) {
        // mSmHandler can be null if the state machine has quit.
        SmHandler smh = mSmHandler;
        if (smh == null) {
            return;
        }

        smh.sendMessageDelayed(msg, delayMillis);
    }

    /**
     * Enqueue a message to the front of the queue for this state machine.
     * Protected, may only be called by instances of StateMachine.
     * <p>
     * Message is ignored if state machine has quit.
     */
    protected final void sendMessageAtFrontOfQueue(int what) {
        // mSmHandler can be null if the state machine has quit.
        SmHandler smh = mSmHandler;
        if (smh == null) {
            return;
        }

        smh.sendMessageAtFrontOfQueue(obtainMessage(what));
    }

    /**
     * Enqueue a message to the front of the queue for this state machine.
     * Protected, may only be called by instances of StateMachine.
     * <p>
     * Message is ignored if state machine has quit.
     */
    protected final void sendMessageAtFrontOfQueue(int what, Object obj) {
        // mSmHandler can be null if the state machine has quit.
        SmHandler smh = mSmHandler;
        if (smh == null) {
            return;
        }

        smh.sendMessageAtFrontOfQueue(obtainMessage(what, obj));
    }

    /**
     * Enqueue a message to the front of the queue for this state machine.
     * Protected, may only be called by instances of StateMachine.
     * <p>
     * Message is ignored if state machine has quit.
     */
    protected final void sendMessageAtFrontOfQueue(int what, int arg1) {
        // mSmHandler can be null if the state machine has quit.
        SmHandler smh = mSmHandler;
        if (smh == null) {
            return;
        }

        smh.sendMessageAtFrontOfQueue(obtainMessage(what, arg1));
    }


    /**
     * Enqueue a message to the front of the queue for this state machine.
     * Protected, may only be called by instances of StateMachine.
     * <p>
     * Message is ignored if state machine has quit.
     */
    protected final void sendMessageAtFrontOfQueue(int what, int arg1, int arg2) {
        // mSmHandler can be null if the state machine has quit.
        SmHandler smh = mSmHandler;
        if (smh == null) {
            return;
        }

        smh.sendMessageAtFrontOfQueue(obtainMessage(what, arg1, arg2));
    }

    /**
     * Enqueue a message to the front of the queue for this state machine.
     * Protected, may only be called by instances of StateMachine.
     * <p>
     * Message is ignored if state machine has quit.
     */
    protected final void sendMessageAtFrontOfQueue(int what, int arg1, int arg2, Object obj) {
        // mSmHandler can be null if the state machine has quit.
        SmHandler smh = mSmHandler;
        if (smh == null) {
            return;
        }

        smh.sendMessageAtFrontOfQueue(obtainMessage(what, arg1, arg2, obj));
    }

    /**
     * Enqueue a message to the front of the queue for this state machine.
     * Protected, may only be called by instances of StateMachine.
     * <p>
     * Message is ignored if state machine has quit.
     */
    protected final void sendMessageAtFrontOfQueue(Message msg) {
        // mSmHandler can be null if the state machine has quit.
        SmHandler smh = mSmHandler;
        if (smh == null) {
            return;
        }

        smh.sendMessageAtFrontOfQueue(msg);
    }

    /**
     * Removes a message from the message queue.
     * Protected, may only be called by instances of StateMachine.
     */
    protected final void removeMessages(int what) {
        // mSmHandler can be null if the state machine has quit.
        SmHandler smh = mSmHandler;
        if (smh == null) {
            return;
        }

        smh.removeMessages(what);
    }

    /**
     * @return current state
     */
    @Nullable
    public final IState getCurrentState() {
        // mSmHandler can be null if the state machine has quit.
        SmHandler smh = mSmHandler;
        if (smh == null) {
            return null;
        }
        return smh.getCurrentState();
    }

    public boolean isDbg() {
        return mIsDbg;
    }

    public void setDbg(boolean dbg) {
        mIsDbg = dbg;
        SmHandler smh = mSmHandler;
        if (smh != null) {
            smh.setDbg(dbg);
        }
    }

    /**
     * 状态监听
     */
    public interface OnStateListener {
        /**
         * 状态切换回调
         *
         * @param state 状态
         */
        void onStateChanged(IState state);
    }
}
