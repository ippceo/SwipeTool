package com.example.swipetool;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 录制界面：在画布上用手指画出多笔滑动轨迹，保存为一段「录制序列」。
 * 序列保存后可按 0~1 归一化坐标 + 时间戳精确回放，保留人手的抖动与弯曲。
 */
public class RecordActivity extends Activity {

    public static final String EXTRA_ID = "recorded_id";
    public static final String EXTRA_NAME = "recorded_name";

    private RecordCanvasView mCanvas;
    private TextView tvInfo;
    private Button btnUndo, btnClear, btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        setContentView(buildUi());
        updateInfo();
    }

    private View buildUi() {
        int pad = dp(16);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("录制滑动序列");
        title.setTextSize(22f);
        title.setTextColor(0xFF222222);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("在下方区域用手指画出滑动轨迹，可以连续画多笔。\n"
                + "录制会保留真实采样点与时间，回放时能重现人手的抖动与弯曲。");
        hint.setTextSize(13f);
        hint.setTextColor(0xFF555555);
        hint.setPadding(0, dp(6), 0, dp(10));
        root.addView(hint);

        mCanvas = new RecordCanvasView(this);
        mCanvas.setOnChangeListener(this::updateInfo);
        LinearLayout.LayoutParams canvasLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(mCanvas, canvasLp);

        tvInfo = new TextView(this);
        tvInfo.setTextSize(14f);
        tvInfo.setTextColor(0xFF1976D2);
        tvInfo.setGravity(Gravity.CENTER);
        tvInfo.setPadding(0, dp(8), 0, dp(8));
        root.addView(tvInfo);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        btnUndo = new Button(this);
        btnUndo.setText("撤销一笔");
        btnUndo.setOnClickListener(v -> mCanvas.undoLast());
        btnClear = new Button(this);
        btnClear.setText("清空");
        btnClear.setOnClickListener(v -> confirmClear());
        row1.addView(btnUndo, weight());
        row1.addView(btnClear, weight());
        root.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        Button btnCancel = new Button(this);
        btnCancel.setText("取消");
        btnCancel.setOnClickListener(v -> confirmExit());
        btnSave = new Button(this);
        btnSave.setText("保存序列");
        btnSave.setOnClickListener(v -> saveSequence());
        row2.addView(btnCancel, weight());
        row2.addView(btnSave, weight());
        root.addView(row2);

        return root;
    }

    private void updateInfo() {
        if (mCanvas == null || tvInfo == null) return;
        int count = mCanvas.getStrokes().size();
        long duration = mCanvas.getTotalDurationMs();
        if (count == 0 && !mCanvas.hasContent()) {
            tvInfo.setText("尚未录制：请按住并滑动手指");
        } else {
            tvInfo.setText("已录制 " + count + " 笔 · 时长 "
                    + String.format(java.util.Locale.US, "%.1f", duration / 1000f) + " 秒");
        }
        btnUndo.setEnabled(mCanvas.hasContent());
        btnClear.setEnabled(mCanvas.hasContent());
        btnSave.setEnabled(count > 0);
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle("清空录制")
                .setMessage("确定清空当前所有笔画？")
                .setPositiveButton("清空", (d, w) -> mCanvas.clearAll())
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmExit() {
        if (!mCanvas.hasContent()) {
            finish();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("放弃录制")
                .setMessage("当前录制尚未保存，确定退出？")
                .setPositiveButton("退出", (d, w) -> finish())
                .setNegativeButton("继续录制", null)
                .show();
    }

    @Override
    public void onBackPressed() {
        confirmExit();
    }

    private void saveSequence() {
        if (mCanvas.getStrokes().isEmpty()) {
            Toast.makeText(this, "请先录制至少一笔滑动", Toast.LENGTH_SHORT).show();
            return;
        }
        int next = RecordedSwipeStore.list(this).size() + 1;
        EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setFilters(new InputFilter[]{new InputFilter.LengthFilter(30)});
        et.setText("序列 " + next);
        et.setSelection(et.getText().length());

        new AlertDialog.Builder(this)
                .setTitle("给这段序列起个名字")
                .setView(et)
                .setPositiveButton("保存", (d, w) -> {
                    String name = et.getText().toString().trim();
                    if (name.isEmpty()) {
                        name = "序列 " + next;
                    }
                    saveWithName(name);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void saveWithName(String name) {
        RecordedSequence seq = new RecordedSequence();
        seq.id = RecordedSwipeStore.newId();
        seq.name = name;
        seq.createdAt = System.currentTimeMillis();
        seq.strokes.addAll(mCanvas.getStrokes());
        RecordedSwipeStore.upsert(this, seq);

        Intent data = new Intent();
        data.putExtra(EXTRA_ID, seq.id);
        data.putExtra(EXTRA_NAME, seq.name);
        setResult(RESULT_OK, data);
        Toast.makeText(this, "已保存：" + seq.name, Toast.LENGTH_SHORT).show();
        finish();
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }
}
