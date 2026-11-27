package tw.ai_lab.screenshotbatch;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Locale;

public final class MetadataSettingsActivity extends Activity {
    private static final String STATE_OPTIONS = "metadata_options";
    private final LinkedHashMap<String, CheckBox> fieldChecks = new LinkedHashMap<>();
    private final HashMap<String, CheckBox> parentForField = new HashMap<>();
    private final ArrayList<CheckBox> parentChecks = new ArrayList<>();
    private EditText batchNameInput;
    private EditText customLabelInput;
    private MetadataOptions options;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        options = MetadataOptions.fromJson(state == null
                ? getIntent().getStringExtra("options") : state.getString(STATE_OPTIONS, "{}"));
        buildPage();
    }

    private void buildPage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(AppPalette.PAGE);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(22), dp(20), dp(48));
        page.setBackgroundColor(AppPalette.PAGE);
        TextView title = text("本批次資料設定", 24, true);
        page.addView(title, matchWrap());
        TextView intro = text("先勾選大項，展開後再選本批要寫入 JSON 的資料。未勾選資料不會輸出。", 14, false);
        page.addView(intro, top());

        TextView version = text("目前版本：" + currentVersionName(), 14, false);
        page.addView(version, top());
        Button update = new Button(this);
        update.setText("手動檢查版本更新");
        styleButton(update, false);
        update.setOnClickListener(v -> {
            Intent result = new Intent();
            result.putExtra("check_updates", true);
            setResult(RESULT_OK, result);
            finish();
        });
        page.addView(update, matchWrap());

        batchNameInput = new EditText(this);
        batchNameInput.setSingleLine(true);
        batchNameInput.setHint("照片統一名稱（例如：日本之旅）");
        batchNameInput.setText(options.batchName);
        styleInput(batchNameInput);
        page.addView(batchNameInput, top());

        page.addView(category("自訂標識", new String[]{
                "label_japan|日本之旅", "label_hawaii|夏威夷之旅", "label_korea|韓國之旅",
                "label_work|工作紀錄", "label_custom|使用者自訂分類名稱"
        }), top());
        page.addView(category("照片基本資料", new String[]{
                "original_filename|原始檔名", "file_format|檔案格式",
                "fixed_file_size|檔案容量|fixed", "image_dimensions|影像尺寸",
                "fixed_capture_date|拍攝日期|fixed", "fixed_capture_time|拍攝時間|fixed"
        }), top());
        page.addView(category("位置資料", new String[]{
                "gps_latitude|GPS 緯度", "gps_longitude|GPS 經度", "place_name|拍攝地點", "geography|其他地理位置資訊"
        }), top());
        page.addView(category("設備資料", new String[]{
                "device_brand|手機品牌", "device_model|手機型號", "camera_info|相機資訊", "other_exif|其他 EXIF 資料"
        }), top());

        Button apply = new Button(this);
        apply.setText("確認本批設定");
        styleButton(apply, true);
        apply.setOnClickListener(v -> applyAndFinish());
        page.addView(apply, top());
        Button cancel = new Button(this);
        cancel.setText("返回");
        styleButton(cancel, false);
        cancel.setOnClickListener(v -> {
            setResult(RESULT_CANCELED, getIntent());
            finish();
        });
        page.addView(cancel, top());
        scroll.addView(page);
        setContentView(scroll);
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            scroll.setClipToPadding(false);
            scroll.setOnApplyWindowInsetsListener((view, insets) -> {
                page.setPadding(dp(20) + insets.getSystemWindowInsetLeft(),
                        dp(22) + insets.getSystemWindowInsetTop(),
                        dp(20) + insets.getSystemWindowInsetRight(),
                        dp(48) + insets.getSystemWindowInsetBottom());
                return insets;
            });
            scroll.requestApplyInsets();
        }
        refreshEnabledState();
    }

    private View category(String title, String[] fields) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        CheckBox parent = new CheckBox(this);
        parent.setText(title);
        parent.setTextSize(18);
        styleCheck(parent);
        parentChecks.add(parent);
        block.addView(parent, matchWrap());
        LinearLayout children = new LinearLayout(this);
        children.setOrientation(LinearLayout.VERTICAL);
        children.setPadding(dp(22), 0, 0, dp(8));
        for (String entry : fields) {
            String[] pair = entry.split("\\|", 3);
            boolean fixed = pair.length > 2 && "fixed".equals(pair[2]);
            CheckBox child = new CheckBox(this);
            child.setText(fixed ? pair[1] + "（圖知固定記錄）" : pair[1]);
            child.setTextSize(15);
            styleCheck(child);
            if (fixed) {
                child.setChecked(true);
                child.setEnabled(false);
                child.setTag("fixed");
            } else {
                child.setChecked(options.selectedFields.contains(pair[0]));
                fieldChecks.put(pair[0], child);
                parentForField.put(pair[0], parent);
            }
            children.addView(child, matchWrap());
        }
        if ("自訂標識".equals(title)) {
            customLabelInput = new EditText(this);
            customLabelInput.setSingleLine(true);
            customLabelInput.setHint("自訂分類名稱");
            customLabelInput.setText(options.labels.isEmpty() ? "" : options.labels.get(0));
            styleInput(customLabelInput);
            CheckBox customCheck = fieldChecks.get("label_custom");
            customLabelInput.setEnabled(parent.isChecked() && customCheck != null && customCheck.isChecked());
            if (customCheck != null) customCheck.setOnCheckedChangeListener((button, checked) ->
                    customLabelInput.setEnabled(parent.isChecked() && checked));
            children.addView(customLabelInput, matchWrap());
        }
        block.addView(children, matchWrap());
        parent.setOnCheckedChangeListener((button, checked) -> {
            children.setVisibility(checked ? View.VISIBLE : View.GONE);
            for (int i = 0; i < children.getChildCount(); i++) {
                View child = children.getChildAt(i);
                if (child == customLabelInput || "fixed".equals(child.getTag())) {
                    if ("fixed".equals(child.getTag())) child.setEnabled(false);
                    else {
                    CheckBox customCheck = fieldChecks.get("label_custom");
                    child.setEnabled(checked && customCheck != null && customCheck.isChecked());
                    }
                } else child.setEnabled(checked);
            }
        });
        parent.setChecked(fieldsChecked(fields));
        children.setVisibility(parent.isChecked() ? View.VISIBLE : View.GONE);
        return block;
    }

    private boolean fieldsChecked(String[] fields) {
        for (String entry : fields) {
            String[] pair = entry.split("\\|", 3);
            if (pair.length > 2 && "fixed".equals(pair[2])) continue;
            String key = pair[0];
            if (options.selectedFields.contains(key)) return true;
        }
        return false;
    }

    private void refreshEnabledState() {
        if (customLabelInput != null) {
            CheckBox parent = parentForField.get("label_custom");
            CheckBox child = fieldChecks.get("label_custom");
            customLabelInput.setEnabled(parent != null && parent.isChecked() && child != null && child.isChecked());
        }
    }

    private void applyAndFinish() {
        options.selectedFields.clear();
        for (String key : fieldChecks.keySet()) {
            CheckBox parent = parentForField.get(key);
            if (parent != null && parent.isChecked() && fieldChecks.get(key).isChecked()) options.selectedFields.add(key);
        }
        options.batchName = batchNameInput.getText().toString().trim();
        options.labels.clear();
        String custom = customLabelInput.getText().toString().trim();
        if (options.selectedFields.contains("label_custom") && !custom.isEmpty()) options.labels.add(custom);
        getIntent().putExtra("options", "");
        try {
            getIntent().putExtra("result_options", options.toJson().toString());
            setResult(RESULT_OK, getIntent());
        } catch (Exception e) {
            setResult(RESULT_CANCELED);
        }
        finish();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        try { state.putString(STATE_OPTIONS, options.toJson().toString()); }
        catch (Exception ignored) { state.putString(STATE_OPTIONS, "{}"); }
        super.onSaveInstanceState(state);
    }

    private TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(bold ? AppPalette.BRAND_DARK : AppPalette.MUTED);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }
    private void styleCheck(CheckBox check) {
        check.setTextColor(AppPalette.TEXT);
        check.setButtonTintList(ColorStateList.valueOf(AppPalette.BRAND));
    }
    private void styleButton(Button button, boolean primary) {
        button.setTextColor(primary ? android.graphics.Color.WHITE : AppPalette.BRAND_DARK);
        button.setBackgroundTintList(ColorStateList.valueOf(primary ? AppPalette.BRAND : AppPalette.PANEL_ALT));
    }
    private void styleInput(EditText input) {
        input.setTextColor(AppPalette.TEXT);
        input.setHintTextColor(AppPalette.MUTED);
        input.setBackgroundTintList(ColorStateList.valueOf(AppPalette.BRAND_LIGHT));
    }
    private String currentVersionName() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Exception ignored) { return "未知"; }
    }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(-1, -2); }
    private LinearLayout.LayoutParams top() { LinearLayout.LayoutParams p = matchWrap(); p.topMargin = dp(10); return p; }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
