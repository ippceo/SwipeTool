package com.example.walkandlook;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 录制序列的本地存储：所有序列以 JSON 数组形式保存在 App 私有目录的
 * {@code filesDir/recordings.json} 中。读写都在同进程内，直接读文件即可。
 */
public final class RecordedSwipeStore {

    private static final String FILE_NAME = "recordings.json";

    private RecordedSwipeStore() {
    }

    /** 读取全部录制序列（按创建时间倒序，最新的在前）。 */
    public static synchronized List<RecordedSequence> list(Context ctx) {
        List<RecordedSequence> result = new ArrayList<>();
        JSONArray arr = readArray(ctx);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            RecordedSequence seq = RecordedSequence.fromJson(o);
            if (!seq.id.isEmpty() && !seq.strokes.isEmpty()) {
                result.add(seq);
            }
        }
        result.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));
        return result;
    }

    /** 按 id 查找，找不到返回 null。 */
    public static synchronized RecordedSequence find(Context ctx, String id) {
        if (id == null || id.isEmpty()) return null;
        for (RecordedSequence seq : list(ctx)) {
            if (id.equals(seq.id)) return seq;
        }
        return null;
    }

    /** 新增或覆盖保存。 */
    public static synchronized void upsert(Context ctx, RecordedSequence seq) {
        if (seq == null || seq.id.isEmpty()) return;
        JSONArray old = readArray(ctx);
        JSONArray out = new JSONArray();
        boolean replaced = false;
        for (int i = 0; i < old.length(); i++) {
            JSONObject o = old.optJSONObject(i);
            if (o == null) continue;
            if (seq.id.equals(o.optString("id", ""))) {
                try {
                    out.put(seq.toJson());
                } catch (Exception ignored) {
                }
                replaced = true;
            } else {
                out.put(o);
            }
        }
        if (!replaced) {
            try {
                out.put(seq.toJson());
            } catch (Exception ignored) {
            }
        }
        writeArray(ctx, out);
    }

    /** 删除指定序列，返回是否真的删除了。 */
    public static synchronized boolean delete(Context ctx, String id) {
        if (id == null || id.isEmpty()) return false;
        JSONArray old = readArray(ctx);
        JSONArray out = new JSONArray();
        boolean removed = false;
        for (int i = 0; i < old.length(); i++) {
            JSONObject o = old.optJSONObject(i);
            if (o == null) continue;
            if (id.equals(o.optString("id", ""))) {
                removed = true;
            } else {
                out.put(o);
            }
        }
        if (removed) {
            writeArray(ctx, out);
        }
        return removed;
    }

    /** 生成一个新的序列 id。 */
    public static String newId() {
        return "rec_" + System.currentTimeMillis() + "_"
                + Integer.toHexString((int) (Math.random() * 0xFFFF));
    }

    private static File file(Context ctx) {
        return new File(ctx.getFilesDir(), FILE_NAME);
    }

    private static JSONArray readArray(Context ctx) {
        File f = file(ctx);
        if (!f.exists()) return new JSONArray();
        try (FileInputStream in = new FileInputStream(f)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            String text = out.toString("UTF-8");
            if (text.trim().isEmpty()) return new JSONArray();
            return new JSONArray(text);
        } catch (Throwable t) {
            return new JSONArray();
        }
    }

    private static void writeArray(Context ctx, JSONArray arr) {
        try (FileOutputStream out = new FileOutputStream(file(ctx));
             OutputStreamWriter w = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            w.write(arr.toString());
            w.flush();
        } catch (Throwable ignored) {
        }
    }
}
