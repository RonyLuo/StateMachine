package cn.senyo.state;

import android.os.Message;

import androidx.annotation.NonNull;

/**
 * @author: Rony
 * @email: luojun@skyruler.cn
 * @date: Created 2021/1/5 15:40
 */
public interface IState {

    /**
     * Returned by processMessage to indicate the the message was processed.
     */
    boolean HANDLED = true;

    /**
     * Returned by processMessage to indicate the the message was NOT processed.
     */
    boolean NOT_HANDLED = false;

    int getCode();

    void enter();

    void exit();

    boolean processMessage(@NonNull Message msg);

    String getName();
}
