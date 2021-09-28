package cn.garymb.ygomobile.utils.glide;

import androidx.annotation.NonNull;

import com.bumptech.glide.load.Key;
import com.bumptech.glide.util.Preconditions;

import java.security.MessageDigest;
import java.util.Objects;

public class StringSignature implements Key {
    private final String key;

    public StringSignature(String key) {
        this.key = Preconditions.checkNotNull(key);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StringSignature that = (StringSignature) o;
        return Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key);
    }

    @Override
    public void updateDiskCacheKey(@NonNull MessageDigest messageDigest) {
        messageDigest.update(key.getBytes(CHARSET));
    }

    @NonNull
    @Override
    public String toString() {
        return "StringSignature{" +
                "key='" + key + '\'' +
                '}';
    }
}
