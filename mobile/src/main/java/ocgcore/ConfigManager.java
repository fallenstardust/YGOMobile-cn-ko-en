package ocgcore;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import cn.garymb.ygomobile.utils.IOUtils;

public class ConfigManager implements Closeable {

    private final List<String> mLines = new ArrayList<>();
    private File file;

    ConfigManager(File file) {
        this.file = file;
    }

    @Override
    public void close(){
        mLines.clear();
    }

    public void read() {
        mLines.clear();
        InputStreamReader in = null;
        FileInputStream inputStream = null;
        try {
            inputStream = new FileInputStream(file);
            in = new InputStreamReader(inputStream, "utf-8");
            BufferedReader reader = new BufferedReader(in);
            String line = null;
            while ((line = reader.readLine()) != null) {
                mLines.add(line);
            }
        } catch (Exception e) {

        } finally {
            IOUtils.close(in);
            IOUtils.close(inputStream);
        }
    }

    public boolean isLoad() {
        return mLines.size() > 0;
    }

    public void save() {
        if (!isLoad()) {
            read();
        }
        OutputStreamWriter out = null;
        FileOutputStream outputStream = null;
        File tmp = new File(file.getAbsolutePath() + ".tmp");
        boolean ok = false;
        try {
            outputStream = new FileOutputStream(tmp);
            out = new OutputStreamWriter(outputStream, "utf-8");
            BufferedWriter writer = new BufferedWriter(out);
            int count = mLines.size();
            for (int i = 0; i < count; i++) {
                writer.write(mLines.get(i));
                if (i < count - 1) {
                    writer.newLine();
                }
            }
            writer.flush();
            ok = true;
        } catch (Exception e) {

        } finally {
            IOUtils.close(out);
            IOUtils.close(outputStream);
        }
        if (ok) {
            if (file.exists()) {
                file.delete();
            }
            tmp.renameTo(file);
        }
    }

    public void setFontSize(int size) {
        if (!isLoad()) {
            read();
        }
        int count = mLines.size();
        for (int i = 0; i < count; i++) {
            String line = mLines.get(i);
            if (line == null) continue;
            line = line.toLowerCase(Locale.US);
            if (line.contains("textfont")) {
                String[] values = line.split("=");
                if (values.length > 1) {
                    String key = values[0];
                    String val = values[1];
                    String newline = key + "= ";
                    String[] vs = val.trim().split(" ");
                    newline += vs[0] + " " + size;
                    mLines.add(i, newline);
                    mLines.remove(i + 1);
                }
            }
        }
        save();
    }
}
