package cn.garymb.ygomobile.ui.file;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import java.io.File;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.activities.BaseActivity;


public class FileActivity extends BaseActivity implements AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener
        , FileAdapter.OnPathChangedListener {
    private ListView mListView;
    private Intent mIntent;
    private TextView headText;
    private FileOpenInfo mFileOpenInfo;
    private FileAdapter mFileAdapter;
    private View saveFileButton;
    private View newFolderButton;
    private boolean selectFile;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        setResult(Activity.RESULT_CANCELED);
        if (doIntent(getIntent())) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_filebrowser);
            Toolbar toolbar = $(R.id.toolbar);
            setSupportActionBar(toolbar);
            enableBackHome();
            initViews();
            mFileAdapter.setOnPathChangedListener(this);
            mListView.setOnItemClickListener(this);
            mListView.setOnItemLongClickListener(this);
            updateUI();
        } else {
            finish();
        }
    }

    private void initViews() {
        mFileAdapter = new FileAdapter(this);
        mListView = $(R.id.list_files);
        mListView.setAdapter(mFileAdapter);
//        footView = $(R.id.head_view);
        $(R.id.file_back).setOnClickListener((v) -> {
            File path = mFileAdapter.getCurPath();
            File dir = path == null ? null : path.getParentFile();
            if (dir != null) {
                if (mFileAdapter.setPath(dir.getAbsolutePath())) {
                    mFileAdapter.loadFiles();
                }
            }
        });
        headText = $(R.id.path);
        newFolderButton = $(R.id.new_folder);
        newFolderButton.setOnClickListener((v) -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            final EditText editText = new EditText(this);
            editText.setSingleLine();
            builder.setTitle(R.string.create_folder);
            builder.setView(editText);
            builder.setNegativeButton(android.R.string.ok, (d, s) -> {
                if (editText.getText() != null) {
                    String name = String.valueOf(editText.getText());
                    if (TextUtils.isEmpty(name)) {
                        return;
                    }
                    File dir = new File(mFileAdapter.getCurPath(), name);
                    dir.mkdirs();
                    if (dir.isDirectory()) {
                        mFileAdapter.setPath(dir.getAbsolutePath());
                    }
                    mFileAdapter.loadFiles();
                }
                d.dismiss();
            });
            builder.show();
        });
        saveFileButton = $(R.id.file_save);
        saveFileButton.setOnClickListener((v) -> {
            if (mFileOpenInfo.getType() == FileOpenType.SelectFolder) {
                File file = mFileAdapter.getCurPath();
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.question);
                builder.setMessage(getString(R.string.check_choose_file, file.getName()));
                builder.setNegativeButton(android.R.string.ok, (d, s) -> {
                    selectFile(file);
                    d.dismiss();
                });
                builder.setNeutralButton(android.R.string.cancel, (d, s) -> {
                    d.dismiss();
                });
                builder.show();
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                final EditText editText = new EditText(this);
                editText.setSingleLine();
                builder.setTitle(R.string.intpu_name);
                builder.setView(editText);
                builder.setNegativeButton(android.R.string.ok, (d, s) -> {
                    if (editText.getText() != null) {
                        String name = String.valueOf(editText.getText());
                        if (TextUtils.isEmpty(name)) {
                            return;
                        }
                        String ex = mFileOpenInfo.getFileExtention();
                        if (ex != null && !name.endsWith(ex)) {
                            name += ex;
                        }
                        File file = new File(mFileAdapter.getCurPath(), name);
                        if (!file.isDirectory()) {
                            selectFile(file);
                        } else {
                            Toast.makeText(this, R.string.the_name_is_folder, Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }
                    d.dismiss();
                });
                builder.setNeutralButton(android.R.string.cancel, (d, s) -> {
                    d.dismiss();
                });
                builder.show();
            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (doIntent(intent)) {
            updateUI();
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        File file = mFileAdapter.getItemById(id);
        if (file != null && file.isDirectory()) {
            if (mFileAdapter.setPath(file.getAbsolutePath())) {
                mFileAdapter.loadFiles();
            }
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.question);
        builder.setMessage(getString(R.string.check_choose_file, file.getName()));
        builder.setNegativeButton(android.R.string.ok, (d, s) -> {
            selectFile(file);
            d.dismiss();
        });
        builder.setNeutralButton(android.R.string.cancel, (d, s) -> {
            d.dismiss();
        });
        builder.show();
    }

    @Override
    public void onBackPressed() {
        File curPath = mFileAdapter.getCurPath();
        File dir = curPath.getParentFile();
        if (dir != null && mFileAdapter.setPath(dir.getAbsolutePath())) {
            mFileAdapter.loadFiles();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onChanged(File path) {
        if (path != null) {
            headText.setText(path.getPath());
        }
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
//        mFileAdapter.setMultiSelect(!mFileAdapter.isMultiSelect());
//        mFileAdapter.loadFiles();
        return true;
    }

    @Override
    public void finish() {
        if (!selectFile) {

        }
        super.finish();
    }

    private void selectFile(File file) {
//        Log.i("kk", "select " + file);
        if (file != null) {
            Intent intent = new Intent().setData(Uri.fromFile(file));
            if (mIntent != null) {
                intent.putExtras(mIntent);
            }
            selectFile = true;
            setResult(Activity.RESULT_OK, intent);
        }
        finish();
    }

    private boolean doIntent(Intent intent) {
        mIntent = intent;
        if (intent != null && intent.hasExtra(Intent.EXTRA_STREAM)) {
            mFileOpenInfo = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            return true;
        }
        return false;
    }

    private void updateUI() {
        setTitle(mFileOpenInfo.getTitle());
        if (mFileOpenInfo.getType() == FileOpenType.SaveFile
                || mFileOpenInfo.getType() == FileOpenType.SelectFolder) {
            saveFileButton.setVisibility(View.VISIBLE);
        } else {
            saveFileButton.setVisibility(View.GONE);
        }
        if (mFileOpenInfo.getType() == FileOpenType.SelectFile) {
            newFolderButton.setVisibility(View.GONE);
        } else {
            newFolderButton.setVisibility(View.VISIBLE);
        }
        if (!TextUtils.isEmpty(mFileOpenInfo.getDefPath())) {
            mFileAdapter.setPath(mFileOpenInfo.getDefPath());
        } else {
            mFileAdapter.setPath(mFileOpenInfo.getDefPath());
        }
        mFileAdapter.setFilefilter(mFileOpenInfo.getFileFilter());
        mFileAdapter.setOnlyFolder(mFileOpenInfo.getType() == FileOpenType.SelectFolder);
        mFileAdapter.loadFiles();
    }

    /***
     * @param title    标题
     * @param filetype 格式过滤
     * @param defPath  默认路径
     * @param showHide 显示隐藏文件
     * @param type     类型
     */
    public static Intent getIntent(Context context, String title, String filetype, String defPath, boolean showHide, FileOpenType type) {
        Intent intent = new Intent(context, FileActivity.class);
        intent.putExtra(Intent.EXTRA_STREAM, new FileOpenInfo(title, filetype, showHide, defPath, type));
        return intent;
    }
}
