package cn.garymb.ygomobile.ui.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class BaseRecyclerAdapterPlus<T, V extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<V> {
    protected Context context;
    private LayoutInflater mLayoutInflater;
    protected final List<T> mItems = new ArrayList<T>();

    public BaseRecyclerAdapterPlus(Context context) {
        super();
        this.context = context;
        mLayoutInflater = LayoutInflater.from(context);
    }
    public Context getContext() {
        return context;
    }

    public boolean add(T item) {
        return add(-1, item, false);
    }

    public boolean add(int pos, T item, boolean onlyone) {
        if (item != null) {
            if (onlyone) {
                if (exist(item)) {
                    return false;
                }
            }
            if (pos >= 0) {
                mItems.add(pos, item);
            } else {
                mItems.add(item);
            }
            return true;
        }
        return true;
    }

    public T remove(int pos) {
        return mItems.remove(pos);
    }
    public void removeItem(T item) {
        mItems.remove(item);
    }
    public List<T> getItems() {
        return mItems;
    }

    protected <VW extends View> VW inflate(int resource, ViewGroup root) {
        return (VW) mLayoutInflater.inflate(resource, root);
    }

    protected <VW extends View> VW inflate(int resource, ViewGroup root, boolean attachToRoot) {
        return (VW) mLayoutInflater.inflate(resource, root, attachToRoot);
    }

    public void clear() {
        mItems.clear();
    }

    public void set(Collection<T> items) {
        clear();
        addAll(items);
    }

    public void addAll(Collection<T> items) {
        if (items != null) {
            mItems.addAll(items);
        }
    }

    public int findItem(T item) {
        return mItems.indexOf(item);
    }

    public boolean exist(T item) {
        if (item == null) return false;
        return mItems.contains(item);
    }

    @Override
    public abstract V onCreateViewHolder(ViewGroup parent, int viewType);

    @Override
    public abstract void onBindViewHolder(V holder, int position);

    public final T getItem(int position) {
        if (position >= 0 && position < getItemCount()) {
            return mItems.get(position);
        }
        return null;
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    public final T getItemById(long id) {
        return getItem((int) id);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    public static class BaseViewHolder extends RecyclerView.ViewHolder{
        protected Context context;

        public BaseViewHolder(View view) {
            super(view);
            this.context = view.getContext();
        }

        protected <T extends View> T $(int id) {
            return (T) itemView.findViewById(id);
        }
    }
}
