package cn.senyo.statemachine;

import android.os.Message;

import androidx.annotation.NonNull;

import com.senyo.android.logger.Log;

import cn.senyo.state.IState;
import cn.senyo.state.State;
import cn.senyo.state.StateMachine;


/**
 * @author: Rony
 * @email: luojun@skyruler.cn
 * @date: Created 2021/1/7 14:08
 */
public class FtpStateMachine extends StateMachine {
    public static final String TAG = "FtpStateMachine";
    public static final int STATE_DEFAULT = 0;
    public static final int STATE_START = 1;
    public static final int STATE_LOGIN = 2;
    public static final int STATE_LOGIN_FAILED = 3;
    public static final int STATE_CONNECTED = 4;
    public static final int STATE_CONNECT_FAILED = 5;
    public static final int STATE_FIRST_DATA = 6;
    public static final int STATE_LAST_DATA = 7;
    public static final int STATE_DISCONNECTED = 8;
    public static final int STATE_DISCONNECT_FAILED = 9;
    public static final int STATE_DROP = 10;
    public static final int STATE_STOP = 11;
    public static final int STATE_STOP_FAILED = 12;
    public static final int STATE_EXIT = 13;
    private final int mSaveNum;
    private int mNum;
    private DefaultState mDefaultState = new DefaultState();
    private StartState mStartState = new StartState();
    private LoginState mLoginState = new LoginState();
    private LoginFailedState mLoginFailedState = new LoginFailedState();
    private ConnectedState mConnectedState = new ConnectedState();
    private ConnectFailedState mConnectFailedState = new ConnectFailedState();
    private DropState mDropState = new DropState();
    private DisconnectedState mDisconnectedState = new DisconnectedState();
    private FirstDataState mFirstDataState = new FirstDataState();
    private LastDataState mLastDataState = new LastDataState();
    private StopState mStopState = new StopState();
    private OnMultiStateListener mListener;

    public FtpStateMachine(String name, int num) {
        super(name);
        mSaveNum = num;
        mNum = num;
        addState(mDefaultState);
        addState(mStartState);
        addState(mLoginState);
        addState(mLoginFailedState);
        addState(mConnectedState);
        addState(mConnectFailedState);
        addState(mDropState);
        addState(mDisconnectedState);
        addState(mFirstDataState);
        addState(mLastDataState);
        addState(mStopState);
        setInitialState(mDefaultState);
        start();
    }

    public void setOnMultiStateListener(OnMultiStateListener listener) {
        mListener = listener;
    }

    private void checkAndTransitionTo(IState state) {
        checkAndTransitionTo(state, state.getCode());
    }

    private void checkAndTransitionTo(IState state, int stateEvent) {
        checkAndTransitionTo(state, obtainMessage(stateEvent));
    }

    private void checkAndTransitionTo(IState state, @NonNull Message msg) {
        if (mNum <= 0 || --mNum <= 0) {
            transitionTo(state, msg);
        }
        if (mListener != null && mNum > 0) {
            mListener.onMultiState(state, mNum);
        }
        Log.d(TAG, "Num: " + mNum + ", " + state.toString());
    }

    private class DefaultState extends State {
        public DefaultState() {
            super(STATE_DEFAULT);
        }

        @Override
        public boolean processMessage(@NonNull Message msg) {
            if (msg.what == STATE_START) {
                transitionTo(mStartState);
            }
            return HANDLED;
        }
    }

    private class StartState extends State {
        public StartState() {
            super(STATE_START);
        }

        @Override
        public boolean processMessage(@NonNull Message msg) {
            switch (msg.what) {
                case STATE_LOGIN:
                    transitionTo(mLoginState);
                    break;
                case STATE_LOGIN_FAILED:
                    checkAndTransitionTo(mLoginFailedState);
                    break;
                case STATE_STOP:
                    transitionTo(mStopState);
                    break;
                default:
                    break;
            }
            return HANDLED;
        }
    }

    private class LoginState extends State {
        public LoginState() {
            super(STATE_LOGIN);
        }

        @Override
        public boolean processMessage(@NonNull Message msg) {
            switch (msg.what) {
                case STATE_CONNECTED:
                    transitionTo(mConnectedState);
                    break;
                case STATE_LOGIN_FAILED:
                case STATE_CONNECT_FAILED:
                    checkAndTransitionTo(mConnectFailedState);
                    break;
                case STATE_STOP:
                    transitionTo(mStopState);
                    break;
                default:
                    break;
            }
            return HANDLED;
        }
    }

    private class LoginFailedState extends State {
        public LoginFailedState() {
            super(STATE_LOGIN_FAILED);
        }

        @Override
        public boolean processMessage(@NonNull Message msg) {
            switch (msg.what) {
                case STATE_LOGIN_FAILED:
                    transitionTo(mStopState);
                    break;
                case STATE_STOP:
                default:
                    break;
            }
            return HANDLED;
        }
    }

    private class ConnectedState extends State {
        public ConnectedState() {
            super(STATE_CONNECTED);
        }

        @Override
        public boolean processMessage(@NonNull Message msg) {
            switch (msg.what) {
                case STATE_CONNECT_FAILED:
                case STATE_LOGIN_FAILED:
                case STATE_DROP:
                    checkAndTransitionTo(mDropState);
                    break;
                case STATE_FIRST_DATA:
                    transitionTo(mFirstDataState);
                    break;
                case STATE_STOP:
                    transitionTo(mDisconnectedState);
                    break;
                default:
                    break;
            }
            return HANDLED;
        }
    }

    private class ConnectFailedState extends State {
        public ConnectFailedState() {
            super(STATE_CONNECT_FAILED);
        }

        @Override
        public boolean processMessage(@NonNull Message msg) {
            switch (msg.what) {
                case STATE_CONNECT_FAILED:
                    transitionTo(mStopState);
                    break;
                case STATE_STOP:
                default:
                    break;
            }
            return HANDLED;
        }
    }

    private class DropState extends State {
        public DropState() {
            super(STATE_DROP);
        }

        @Override
        public boolean processMessage(@NonNull Message msg) {
            switch (msg.what) {
                case STATE_DROP:
                    checkAndTransitionTo(this, STATE_STOP);
                    break;
                case STATE_STOP:
                default:
                    break;
            }
            return HANDLED;
        }
    }

    private class FirstDataState extends State {
        public FirstDataState() {
            super(STATE_FIRST_DATA);
        }

        @Override
        public boolean processMessage(@NonNull Message msg) {
            switch (msg.what) {
                case STATE_CONNECT_FAILED:
                case STATE_LOGIN_FAILED:
                case STATE_DROP:
                    checkAndTransitionTo(mDropState);
                    break;
                case STATE_DISCONNECTED:
                case STATE_LAST_DATA:
                    checkAndTransitionTo(mLastDataState);
                    break;
                case STATE_STOP:
                    transitionTo(mLastDataState, msg.what);
                    break;
                default:
                    break;
            }
            return HANDLED;
        }
    }

    private class LastDataState extends State {
        public LastDataState() {
            super(STATE_LAST_DATA);
        }

        @Override
        public boolean processMessage(@NonNull Message msg) {
            switch (msg.what) {
                case STATE_CONNECT_FAILED:
                case STATE_LOGIN_FAILED:
                case STATE_DROP:
                    checkAndTransitionTo(mDropState);
                    break;
                case STATE_DISCONNECTED:
                case STATE_LAST_DATA:
                    checkAndTransitionTo(mDisconnectedState);
                    break;
                case STATE_STOP:
                    transitionTo(mDisconnectedState, msg.what);
                    break;
                default:
                    break;
            }
            return HANDLED;
        }
    }

    private class DisconnectedState extends State {
        public DisconnectedState() {
            super(STATE_DISCONNECTED);
        }

        @Override
        public boolean processMessage(@NonNull Message msg) {
            switch (msg.what) {
                case STATE_DISCONNECTED:
                case STATE_STOP:
                    transitionTo(mStopState);
                    break;
                default:
                    break;
            }
            return HANDLED;
        }
    }


    private class StopState extends State {
        public StopState() {
            super(STATE_STOP);
        }

        @Override
        public boolean processMessage(@NonNull Message msg) {
            if (msg.what == STATE_START) {
                mNum = mSaveNum;
                transitionTo(mStartState);
            }
            return HANDLED;
        }
    }

    public interface OnMultiStateListener {
        void onMultiState(IState state, int num);
    }
}
