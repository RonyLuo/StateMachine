package cn.senyo.statemachine;

import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.senyo.android.logger.Log;
import com.senyo.android.toasty.Toasty;

import cn.senyo.state.IState;
import cn.senyo.state.StateMachine;
import cn.senyo.statemachine.databinding.ActivityMainBinding;

/**
 * @author: Rony
 * @email: luojun@jltechwise.com
 * @date Created 2021/1/8 17:36
 */
public class MainActivity extends AppCompatActivity implements View.OnClickListener, StateMachine.OnStateListener, FtpStateMachine.OnMultiStateListener {

    private FtpStateMachine mStateMachine;
    private ActivityMainBinding mBinding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());
        Log.initialization(this, "StateMachine", android.util.Log.DEBUG, 10 * 1024 * 1024);
        mStateMachine = new FtpStateMachine("FtpStateMachine", 10);
        mStateMachine.setOnStateListener(this);
        mStateMachine.setOnMultiStateListener(this);
        mStateMachine.setDbg(true);
        mBinding.btStart.setOnClickListener(this);
        mBinding.btConnect.setOnClickListener(this);
        mBinding.btConnectFail.setOnClickListener(this);
        mBinding.btLogin.setOnClickListener(this);
        mBinding.btLoginFail.setOnClickListener(this);
        mBinding.btFirstData.setOnClickListener(this);
        mBinding.btDrop.setOnClickListener(this);
        mBinding.btLastData.setOnClickListener(this);
        mBinding.btDisconnect.setOnClickListener(this);
        mBinding.btDisconnecFail.setOnClickListener(this);
        mBinding.btStop.setOnClickListener(this);
        mBinding.btStopFailed.setOnClickListener(this);
        mBinding.tvResult.setMovementMethod(ScrollingMovementMethod.getInstance());
    }

    @Override
    public void onClick(View view) {
        if (view == null) {
            return;
        }
        switch (view.getId()) {
            case R.id.btStart:
                mStateMachine.sendMessage(FtpStateMachine.STATE_START);
                break;
            case R.id.btLogin:
                mStateMachine.sendMessage(FtpStateMachine.STATE_LOGIN);
                break;
            case R.id.btLoginFail:
                mStateMachine.sendMessage(FtpStateMachine.STATE_LOGIN_FAILED);
                break;
            case R.id.btConnect:
                mStateMachine.sendMessage(FtpStateMachine.STATE_CONNECTED);
                break;
            case R.id.btConnectFail:
                mStateMachine.sendMessage(FtpStateMachine.STATE_CONNECT_FAILED);
                break;
            case R.id.btFirstData:
                mStateMachine.sendMessage(FtpStateMachine.STATE_FIRST_DATA);
                break;
            case R.id.btDrop:
                mStateMachine.sendMessage(FtpStateMachine.STATE_DROP);
                break;
            case R.id.btLastData:
                mStateMachine.sendMessage(FtpStateMachine.STATE_LAST_DATA);
                break;
            case R.id.btDisconnect:
                mStateMachine.sendMessage(FtpStateMachine.STATE_DISCONNECTED);
                break;
            case R.id.btDisconnecFail:
                mStateMachine.sendMessage(FtpStateMachine.STATE_DISCONNECT_FAILED);
                break;
            case R.id.btStop:
                mStateMachine.sendMessage(FtpStateMachine.STATE_STOP);
                break;
            case R.id.btStopFailed:
                mStateMachine.sendMessage(FtpStateMachine.STATE_STOP_FAILED);
                break;
            default:
                break;
        }
    }

    @Override
    public void onStateChanged(final IState state) {
        if (state == null) {
            return;
        }

        String string = mBinding.tvResult.getText().toString();
        if (string.length() > 1024) {
            string = state.toString();
        } else {
            string = string + "\n" + state.toString();
        }
        mBinding.tvResult.setText(string);
        Log.d("MainActivity", state.toString());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mStateMachine.quit();
    }

    @Override
    public void onMultiState(IState state, int num) {
        if (state == null) {
            return;
        }
        String str = "num: " + num + ", " + state.toString();
        Toasty.normal(MainActivity.this, str).show();
//        runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                String str = "num: " + num + ", " + state.toString();
//                Toasty.normal(MainActivity.this, str).show();
//            }
//        });
    }
}