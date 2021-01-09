package cn.senyo.state;

import android.os.Message;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * @author: Rony
 * @email: luojun@skyruler.cn
 * @date: Created 2021/1/5 15:42
 */
public class State implements IState {

    private final int mCode;

    protected State(int code) {
        mCode = code;
    }

    @Override
    public int getCode() {
        return mCode;
    }

    @Override
    public void enter() {

    }

    @Override
    public void exit() {

    }

    @Override
    public boolean processMessage(@NonNull Message msg) {
        return false;
    }

    @Override
    public String getName() {
        String name = getClass().getName();
        int lastDollar = name.lastIndexOf('$');
        return name.substring(lastDollar + 1);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        return super.equals(obj);
    }

    @NonNull
    @Override
    public String toString() {
        return "State{" +
                "name=" + getName() +
                ", code=" + mCode +
                '}';
    }
}
