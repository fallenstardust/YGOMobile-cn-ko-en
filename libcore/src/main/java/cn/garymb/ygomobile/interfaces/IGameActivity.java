package cn.garymb.ygomobile.interfaces;


import androidx.annotation.Keep;

@Keep
public interface IGameActivity {
    GameHost getNativeGameHost();
    IGameUI getNativeGameUI();
}
