package cn.garymb.ygomobile.ui.file;

import android.content.Context;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.adapters.BaseAdapterPlus;
import cn.garymb.ygomobile.ui.plus.VUiKit;

class FileAdapter extends BaseAdapterPlus<File> {
    public interface OnPathChangedListener {
        void onChanged(File path);
    }

    private boolean mFolder = false;
    private boolean mShowHide = false;
    private boolean isMultiSelect = false;
    private File mCurPath;
    private volatile File mParent;
    private String mFilefilter;
    private String rootPath;
    private OnPathChangedListener mOnPathChangedListener;

    public FileAdapter(Context context) {
        super(context);
        rootPath = getRootPath();
    }

    public void setOnPathChangedListener(OnPathChangedListener onPathChangedListener) {
        mOnPathChangedListener = onPathChangedListener;
    }

    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
        setPath(rootPath);
    }

    private String getRootPath() {
        return "/";
    }

    public File getCurPath() {
        return mCurPath;
    }

    public boolean setPath(String path) {
        if (TextUtils.isEmpty(path)) {
            try {
                path = Environment.getExternalStorageDirectory().getAbsolutePath();
            } catch (Exception e) {
                path = "/";
            }
        }
        path = new File(path).getAbsolutePath();
//        Log.d("kk", "set path " + rootPath + " " + path);
        if (rootPath != null) {
            if (rootPath.length() > path.length()) {
                return false;
            }
        }
        mCurPath = new File(path);
        if (mCurPath.isFile()) {
            mCurPath = mCurPath.getParentFile();
        }
        return true;
    }

    public boolean isCurPath(File file) {
        if (mCurPath == null) {
            return false;
        }
        return TextUtils.equals(file.getAbsolutePath(), mCurPath.getAbsolutePath());
    }

    public boolean isParent(File file) {
        if (mParent == null) {
            return false;
        }
        return TextUtils.equals(file.getAbsolutePath(), mParent.getAbsolutePath());
    }

    public void setOnlyFolder(boolean folder) {
        mFolder = folder;
    }

    public void setShowHide(boolean showHide) {
        mShowHide = showHide;
    }

    public void setFilefilter(String filefilter) {
        mFilefilter = filefilter;
    }

    public void setMultiSelect(boolean multiSelect) {
        isMultiSelect = multiSelect;
    }

    public boolean isMultiSelect() {
        return isMultiSelect;
    }

    public void loadFiles() {
        VUiKit.defer().when(() -> {
            if (mCurPath == null) {
                return null;
            }
            Pattern p = null;
            if (mFilefilter != null) {
                try {
                    p = Pattern.compile(mFilefilter, Pattern.CASE_INSENSITIVE);
                } catch (Exception e) {
                    try {
                        p = Pattern.compile(mFilefilter.replace("*.", "[\\S\\s]*?\\."), Pattern.CASE_INSENSITIVE);
                    } catch (Exception e2) {
                        Log.e("file", "load files", e2);
                    }

                }
            }
            final Pattern finalP = p;
            File[] files = mCurPath.listFiles((pathname) -> {
                if (mFolder) {
                    if (!pathname.isDirectory()) {
                        return false;
                    }
                }
                if (pathname.isFile() && !TextUtils.isEmpty(mFilefilter)) {
                    String ex = pathname.getName().toLowerCase(Locale.US);
                    if (finalP != null) {
                        Matcher m = finalP.matcher(ex);
                        if (!m.find()) {
                            return false;
                        }
                    }
                }
                if (!mShowHide) {
                    if (pathname.getName().startsWith(".")) {
                        return false;
                    }
                }
                return true;
            });
            List<File> filesList = new ArrayList<File>();
            List<File> pathsList = new ArrayList<File>();
//            File pfile = mCurPath.getParentFile();
//            if (pfile != null) {
//                if (rootPath != null && pfile.getAbsolutePath().length() < rootPath.length()) {
//                    mParent = mCurPath;
//                } else {
//                    mParent = pfile;
//                }
//                pathsList.add(mParent);
//            }
            if (files != null) {
                for (File f : files) {
                    if (f.isDirectory()) {
                        pathsList.add(f);
                    } else {
                        filesList.add(f);
                    }
                }
            }
            List<File> result = new ArrayList<File>();
            result.addAll(pathsList);
            result.addAll(filesList);
            return result;
        }).done((rs) -> {
            if (rs != null) {
                if (mOnPathChangedListener != null) {
                    mOnPathChangedListener.onChanged(mCurPath);
                }
                set(rs);
                notifyDataSetChanged();
            }
        });
    }

    @Override
    protected View createView(int position, ViewGroup parent) {
        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        ViewHolder viewHolder = new ViewHolder(context);
        linearLayout.addView(viewHolder.checkBox);
        linearLayout.addView(viewHolder.icon, new ViewGroup.LayoutParams((int) (getContext().getResources().getDimension(R.dimen.label_width_small)),
                (int) (getContext().getResources().getDimension(R.dimen.item_height))));
        linearLayout.addView(viewHolder.name, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                (int) (getContext().getResources().getDimension(R.dimen.item_height))));
        linearLayout.setTag(viewHolder);
        return linearLayout;
    }

    @Override
    protected void attach(View view, File item, int position) {
        ViewHolder viewHolder = (ViewHolder) view.getTag();
        if (isMultiSelect()) {
            viewHolder.checkBox.setVisibility(View.VISIBLE);
        } else {
            viewHolder.checkBox.setVisibility(View.GONE);
        }
        String name = item.getName().toLowerCase(Locale.US);
        boolean isimage = false;
        for (String ex : Constants.FILE_IMAGE_EX) {
            if (name.endsWith(ex)) {
                isimage = true;
                break;
            }
        }
        if (!item.isDirectory()) {
            if (isimage) {
                Glide.with(context).load(item).into(viewHolder.icon);
            } else {
                viewHolder.icon.setImageResource(R.drawable.ic_insert_drive_file);
            }
        } else {
            viewHolder.icon.setImageResource(R.drawable.ic_folder_open);
        }
        viewHolder.icon.setVisibility(View.VISIBLE);
        viewHolder.name.setText(item.getName());
    }

    private class ViewHolder {
        public ImageView icon;
        public TextView name;
        public CheckBox checkBox;

        public ViewHolder(Context context) {
            icon = new ImageView(context);
            icon.setPadding(VUiKit.dpToPx(4), 0, 0, 0);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            name = new TextView(context);
            name.setPadding(VUiKit.dpToPx(8), 0, 0, 0);
            name.setSingleLine();
            name.setGravity(Gravity.CENTER_VERTICAL);
            checkBox = new CheckBox(context);
            checkBox.setGravity(Gravity.CENTER_VERTICAL);
            checkBox.setPadding(VUiKit.dpToPx(4), 0, 0, 0);
        }
    }
}
