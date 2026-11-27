package tw.ai_lab.screenshotbatch;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.File;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int REQUEST_PICK_PHOTOS = 101;
    private static final int REQUEST_SAVE_ZIP = 102;
    private static final int REQUEST_INSTALL_PERMISSION = 103;
    private static final int REQUEST_METADATA_SETTINGS = 104;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ArrayList<PhotoItem> selectedPhotos = new ArrayList<>();
    private LinearLayout root;
    private LinearLayout listContainer;
    private ScrollView namesScrollView;
    private ImageView previewImage;
    private TextView countView;
    private TextView statusView;
    private Button processButton;
    private Button chooseButton;
    private Button replaceButton;
    private Button clearButton;
    private Button excludeButton;
    private Button saveButton;
    private Button shareButton;
    private Button updateButton;
    private Button settingsButton;
    private Button lockOuterButton;
    private ProgressBar progressBar;
    private BatchExporter.Result latestResult;
    private boolean processing;
    private boolean checkingUpdate;
    private boolean outerScrollLocked;
    private File pendingUpdateApk;
    private String previewUri;
    private boolean replaceAfterPick;
    private final Set<String> excludedUris = new HashSet<>();
    private MetadataOptions metadataOptions = new MetadataOptions();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        if (savedInstanceState == null) checkForUpdates(true);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this) {
            @Override public boolean onInterceptTouchEvent(MotionEvent event) {
                return outerScrollLocked ? false : super.onInterceptTouchEvent(event);
            }
            @Override public boolean onTouchEvent(MotionEvent event) {
                return outerScrollLocked ? false : super.onTouchEvent(event);
            }
        };
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(28));
        root.setBackgroundColor(AppPalette.PAGE);
        getWindow().setStatusBarColor(AppPalette.BRAND_DARK);
        getWindow().setNavigationBarColor(AppPalette.BRAND_DARK);
        scroll.addView(root);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("圖知");
        title.setTextColor(AppPalette.TEXT);
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(0, 0, 0, dp(8));
        titleRow.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        settingsButton = button("⚙");
        settingsButton.setContentDescription("設定：JSON 標識與版本更新");
        settingsButton.setTextSize(21);
        settingsButton.setOnClickListener(v -> openMetadataSettings());
        titleRow.addView(settingsButton, new LinearLayout.LayoutParams(dp(52), dp(48)));
        root.addView(titleRow, matchWrap());

        TextView versionView = new TextView(this);
        versionView.setText("版本 " + getVersionName().replace("-debug", ""));
        versionView.setTextColor(AppPalette.MUTED);
        versionView.setTextSize(12);
        versionView.setPadding(0, 0, 0, dp(6));
        root.addView(versionView, matchWrap());

        LinearLayout hintRow = new LinearLayout(this);
        hintRow.setOrientation(LinearLayout.HORIZONTAL);
        hintRow.setGravity(Gravity.CENTER_VERTICAL);
        ImageView appLogo = new ImageView(this);
        appLogo.setImageResource(R.mipmap.ic_launcher);
        appLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        appLogo.setContentDescription("圖知 Logo");
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        logoParams.rightMargin = dp(8);
        hintRow.addView(appLogo, logoParams);
        TextView hint = new TextView(this);
        hint.setText("選取照片後逐張檢視；照片名稱與排除狀態\n分開管理。照片原檔不會被修改。");
        hint.setTextColor(AppPalette.MUTED);
        hint.setTextSize(12);
        hintRow.addView(hint, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(hintRow, matchWrap());

        // Three vertically ordered blocks. The preview and name list are the first two
        // visible work areas; the action block follows them in the same natural scroll.
        LinearLayout previewBlock = new LinearLayout(this);
        previewBlock.setOrientation(LinearLayout.VERTICAL);
        previewBlock.setPadding(dp(12), dp(8), dp(12), dp(12));
        previewBlock.setBackgroundColor(AppPalette.WHITE);
        LinearLayout.LayoutParams previewBlockParams = matchWrapWithTop();
        root.addView(previewBlock, previewBlockParams);

        TextView previewTitle = new TextView(this);
        previewTitle.setText("單張預覽");
        previewTitle.setTextColor(AppPalette.BRAND_DARK);
        previewTitle.setTextSize(19);
        previewTitle.setPadding(0, dp(4), 0, dp(8));
        previewBlock.addView(previewTitle, matchWrap());

        previewImage = new ImageView(this);
        previewImage.setAdjustViewBounds(false);
        previewImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        previewImage.setBackgroundColor(Color.WHITE);
        previewImage.setContentDescription("目前選取照片預覽；保持比例，不裁切");
        LinearLayout.LayoutParams imageParams = matchWrap();
        int screenHeightDp = Math.round(getResources().getDisplayMetrics().heightPixels /
                getResources().getDisplayMetrics().density);
        imageParams.height = dp(Math.max(360, Math.min(680, Math.round(screenHeightDp * 0.60f))));
        previewBlock.addView(previewImage, imageParams);

        LinearLayout namesBlock = new LinearLayout(this);
        namesBlock.setOrientation(LinearLayout.VERTICAL);
        namesBlock.setPadding(dp(12), dp(8), dp(12), dp(12));
        namesBlock.setBackgroundColor(AppPalette.PANEL);
        root.addView(namesBlock, matchWrapWithTop());

        LinearLayout countRow = new LinearLayout(this);
        countRow.setOrientation(LinearLayout.HORIZONTAL);
        countRow.setGravity(Gravity.CENTER_VERTICAL);
        countView = new TextView(this);
        countView.setText("已選取 0 張");
        countView.setTextColor(AppPalette.TEXT);
        countView.setTextSize(17);
        countRow.addView(countView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        lockOuterButton = button("🔒鎖外層");
        lockOuterButton.setTextSize(13);
        lockOuterButton.setContentDescription("鎖定或解鎖外層頁面捲動；照片清單仍可捲動");
        lockOuterButton.setOnClickListener(v -> toggleOuterScrollLock());
        countRow.addView(lockOuterButton, new LinearLayout.LayoutParams(dp(104), dp(44)));
        namesBlock.addView(countRow, matchWrap());

        namesScrollView = new ScrollView(this);
        namesScrollView.setFillViewport(false);
        namesScrollView.setNestedScrollingEnabled(false);
        namesScrollView.setBackgroundColor(AppPalette.PANEL_ALT);
        LinearLayout.LayoutParams namesParams = matchWrapWithTop();
        // Five rows fit while keeping the names list independently scrollable.
        namesParams.height = dp(280);
        namesBlock.addView(namesScrollView, namesParams);
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(dp(6), dp(6), dp(6), dp(6));
        namesScrollView.addView(listContainer);

        excludeButton = button("確認排除勾選照片");
        excludeButton.setEnabled(false);
        excludeButton.setOnClickListener(v -> confirmExcludeChecked());
        namesBlock.addView(excludeButton, matchWrapWithTop());

        LinearLayout actionsBlock = new LinearLayout(this);
        actionsBlock.setOrientation(LinearLayout.VERTICAL);
        actionsBlock.setPadding(dp(12), dp(10), dp(12), dp(12));
        actionsBlock.setBackgroundColor(AppPalette.WHITE);
        root.addView(actionsBlock, matchWrapWithTop());

        TextView actionsTitle = new TextView(this);
        actionsTitle.setText("照片整理與輸出");
        actionsTitle.setTextColor(AppPalette.BRAND_DARK);
        actionsTitle.setTextSize(18);
        actionsBlock.addView(actionsTitle, matchWrap());

        chooseButton = button("添加照片");
        stylePrimaryButton(chooseButton);
        chooseButton.setOnClickListener(v -> choosePhotos());
        actionsBlock.addView(chooseButton, matchWrapWithTop());

        LinearLayout listActions = new LinearLayout(this);
        listActions.setOrientation(LinearLayout.HORIZONTAL);
        replaceButton = button("重新選取");
        replaceButton.setOnClickListener(v -> confirmReplaceSelection());
        clearButton = button("清空全部");
        clearButton.setOnClickListener(v -> confirmClearSelection());
        listActions.addView(replaceButton, new LinearLayout.LayoutParams(0, dp(48), 1f));
        listActions.addView(clearButton, new LinearLayout.LayoutParams(0, dp(48), 1f));
        actionsBlock.addView(listActions, matchWrapWithTop());

        processButton = button("整理並建立 ZIP");
        stylePrimaryButton(processButton);
        processButton.setEnabled(false);
        processButton.setOnClickListener(v -> confirmBeforePackage());
        actionsBlock.addView(processButton, matchWrapWithTop());

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = matchWrapWithTop();
        progressParams.height = dp(10);
        actionsBlock.addView(progressBar, progressParams);

        statusView = new TextView(this);
        statusView.setText("尚未建立批次");
        statusView.setTextColor(AppPalette.MUTED);
        statusView.setTextSize(14);
        statusView.setPadding(0, dp(12), 0, 0);
        actionsBlock.addView(statusView, matchWrap());

        saveButton = button("儲存 ZIP 到手機");
        stylePrimaryButton(saveButton);
        saveButton.setEnabled(false);
        saveButton.setOnClickListener(v -> saveZip());
        actionsBlock.addView(saveButton, matchWrapWithTop());

        shareButton = button("分享 ZIP");
        stylePrimaryButton(shareButton);
        shareButton.setEnabled(false);
        shareButton.setOnClickListener(v -> shareZip());
        actionsBlock.addView(shareButton, matchWrapWithTop());

        setContentView(scroll);
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                root.setPadding(dp(20) + insets.getSystemWindowInsetLeft(),
                        dp(24) + insets.getSystemWindowInsetTop(),
                        dp(20) + insets.getSystemWindowInsetRight(),
                        dp(28) + insets.getSystemWindowInsetBottom());
                return insets;
            });
            root.requestApplyInsets();
        }
        refreshList();
    }

    private void choosePhotos() {
        openPhotoPicker(false);
    }

    private void openPhotoPicker(boolean replace) {
        replaceAfterPick = replace;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_PICK_PHOTOS);
        } catch (Exception e) {
            Toast.makeText(this, "無法開啟照片選取器：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void confirmReplaceSelection() {
        if (selectedPhotos.isEmpty()) {
            openPhotoPicker(true);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("重新選取照片")
                .setMessage("新選取的照片會取代目前清單。手機內的原始照片不會刪除。")
                .setNegativeButton("取消", null)
                .setPositiveButton("繼續選取", (dialog, which) -> openPhotoPicker(true))
                .show();
    }

    private void confirmClearSelection() {
        if (selectedPhotos.isEmpty()) return;
        new AlertDialog.Builder(this)
                .setTitle("清空照片清單？")
                .setMessage("只會清空本次整理清單，不會刪除手機內的原始照片。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空清單", (dialog, which) -> {
                    selectedPhotos.clear();
                    excludedUris.clear();
                    previewUri = null;
                    latestResult = null;
                    saveButton.setEnabled(false);
                    shareButton.setEnabled(false);
                    refreshList();
                    statusView.setText("照片清單已清空；手機原始照片未變更。");
                })
                .show();
    }

    private void confirmExcludeChecked() {
        int checked = excludedUris.size();
        if (checked == 0) return;
        new AlertDialog.Builder(this)
                .setTitle("從本次清單排除照片？")
                .setMessage("將排除 " + checked + " 張照片，並重新排列本批次流水號。只會變更 App 內的清單，不會刪除或修改手機原始照片。")
                .setNegativeButton("返回檢查", null)
                .setPositiveButton("確認排除", (dialog, which) -> {
                    selectedPhotos.removeIf(item -> excludedUris.contains(item.uri.toString()));
                    excludedUris.clear();
                    if (selectedPhotos.stream().noneMatch(item -> item.uri.toString().equals(previewUri))) {
                        previewUri = selectedPhotos.isEmpty() ? null : selectedPhotos.get(0).uri.toString();
                    }
                    latestResult = null;
                    saveButton.setEnabled(false);
                    shareButton.setEnabled(false);
                    refreshList();
                    statusView.setText("已從本次清單排除照片；流水號已重新排列，原始照片未變更。");
                })
                .show();
    }

    private void confirmRemoveOne(PhotoItem item) {
        new AlertDialog.Builder(this)
                .setTitle("從清單移除此照片？")
                .setMessage("只會從本次整理清單移除，不會刪除手機原始照片。")
                .setNegativeButton("取消", null)
                .setPositiveButton("移除", (dialog, which) -> {
                    selectedPhotos.remove(item);
                    excludedUris.remove(item.uri.toString());
                    if (item.uri.toString().equals(previewUri)) previewUri = selectedPhotos.isEmpty() ? null : selectedPhotos.get(0).uri.toString();
                    latestResult = null;
                    saveButton.setEnabled(false);
                    shareButton.setEnabled(false);
                    refreshList();
                })
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_PHOTOS && resultCode == RESULT_OK && data != null) {
            ArrayList<Uri> uris = new ArrayList<>();
            if (data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++) uris.add(data.getClipData().getItemAt(i).getUri());
            } else if (data.getData() != null) uris.add(data.getData());
            importUris(uris, data.getFlags(), replaceAfterPick);
        } else if (requestCode == REQUEST_SAVE_ZIP && resultCode == RESULT_OK && data != null && data.getData() != null) {
            saveZipTo(data.getData());
        } else if (requestCode == REQUEST_INSTALL_PERMISSION) {
            if (Build.VERSION.SDK_INT < 26 || getPackageManager().canRequestPackageInstalls()) {
                installPendingUpdate();
            } else {
                statusView.setText("尚未允許此 App 安裝更新。你仍可手動下載 APK 安裝。");
            }
        } else if (requestCode == REQUEST_METADATA_SETTINGS && resultCode == RESULT_OK && data != null) {
            if (data.getBooleanExtra("check_updates", false)) {
                checkForUpdates(false);
            } else {
                metadataOptions = MetadataOptions.fromJson(data.getStringExtra("result_options"));
                statusView.setText("本批次資料設定已套用；重新開啟圖知後會回到系統預設項目。");
            }
        }
    }

    private void openMetadataSettings() {
        try {
            Intent intent = new Intent(this, MetadataSettingsActivity.class);
            intent.putExtra("options", metadataOptions.toJson().toString());
            startActivityForResult(intent, REQUEST_METADATA_SETTINGS);
        } catch (Exception e) {
            Toast.makeText(this, "無法開啟資料設定：" + errorText(e), Toast.LENGTH_LONG).show();
        }
    }

    private void toggleOuterScrollLock() {
        outerScrollLocked = !outerScrollLocked;
        if (lockOuterButton != null) {
            lockOuterButton.setText(outerScrollLocked ? "🔓解鎖外層" : "🔒鎖外層");
            lockOuterButton.setContentDescription(outerScrollLocked
                    ? "已鎖定外層頁面捲動；點擊解鎖後可前往整理與輸出區；照片清單仍可捲動"
                    : "鎖定外層頁面捲動；鎖定後照片清單仍可捲動");
        }
    }

    private void checkForUpdates(boolean automatic) {
        if (processing || checkingUpdate) return;
        checkingUpdate = true;
        setUpdateButtonEnabled(false);
        statusView.setText("正在檢查網路更新…");
        executor.execute(() -> {
            try {
                UpdateManager.UpdateInfo info = UpdateManager.fetchLatest();
                int currentVersion = UpdateManager.installedVersionCode(this);
                runOnUiThread(() -> {
                    checkingUpdate = false;
                    setUpdateButtonEnabled(!processing);
                    if (info.versionCode <= currentVersion) {
                        statusView.setText(automatic ? "App 已開啟；目前已是最新版本。"
                                : "目前已是最新版本（" + info.versionName + "）。");
                        return;
                    }
                    String message = "目前版本：" + getVersionName() + "\n最新版本：" + info.versionName
                            + (info.releaseNotes.isEmpty() ? "" : "\n\n更新內容：\n" + info.releaseNotes);
                    new AlertDialog.Builder(this)
                            .setTitle("發現新版本")
                            .setMessage(message)
                            .setNegativeButton("稍後", (dialog, which) -> statusView.setText("已找到新版，可隨時再次檢查。"))
                            .setPositiveButton("下載並安裝", (dialog, which) -> downloadUpdate(info))
                            .show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    checkingUpdate = false;
                    setUpdateButtonEnabled(!processing);
                    statusView.setText(automatic
                            ? "目前無法連線檢查更新，App 可繼續使用。"
                            : "更新檢查失敗：" + errorText(e));
                });
            }
        });
    }

    private void downloadUpdate(UpdateManager.UpdateInfo info) {
        if (checkingUpdate || processing) return;
        checkingUpdate = true;
        setUpdateButtonEnabled(false);
        statusView.setText("正在安全下載並驗證新版 APK…");
        executor.execute(() -> {
            try {
                File apk = UpdateManager.downloadAndVerify(this, info);
                runOnUiThread(() -> {
                    checkingUpdate = false;
                    setUpdateButtonEnabled(!processing);
                    pendingUpdateApk = apk;
                    statusView.setText("新版 APK 驗證完成，交由 Android 安裝器處理。");
                    openInstallPermissionOrInstall();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    checkingUpdate = false;
                    setUpdateButtonEnabled(!processing);
                    statusView.setText("新版下載或驗證失敗：" + errorText(e));
                });
            }
        });
    }

    private void openInstallPermissionOrInstall() {
        if (pendingUpdateApk == null) return;
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(this)
                    .setTitle("需要允許安裝更新")
                    .setMessage("請在 Android 設定中允許「圖知」安裝自己下載的更新，之後仍會由 Android 顯示確認畫面。")
                    .setNegativeButton("稍後", (dialog, which) -> statusView.setText("新版已下載；允許安裝後可再次檢查更新。"))
                    .setPositiveButton("前往設定", (dialog, which) -> {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + getPackageName()));
                        startActivityForResult(intent, REQUEST_INSTALL_PERMISSION);
                    })
                    .show();
        } else {
            installPendingUpdate();
        }
    }

    private void installPendingUpdate() {
        if (pendingUpdateApk == null || !pendingUpdateApk.isFile()) {
            statusView.setText("找不到已驗證的更新檔，請重新檢查更新。");
            return;
        }
        try {
            UpdateManager.requestInstall(this, pendingUpdateApk);
        } catch (Exception e) {
            statusView.setText("無法開啟 Android 安裝器：" + errorText(e));
        }
    }

    private String getVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
            return "目前版本";
        }
    }

    private String errorText(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private void importUris(List<Uri> uris, int resultFlags, boolean replace) {
        if (uris.isEmpty()) return;
        processing = true;
        setBusyUi(true, "正在讀取照片資訊…");
        executor.execute(() -> {
            ArrayList<PhotoItem> loaded = new ArrayList<>();
            ArrayList<String> failures = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            if (!replace) for (PhotoItem item : selectedPhotos) seen.add(item.uri.toString());
            int order = replace ? 0 : selectedPhotos.size();
            for (Uri uri : uris) {
                if (uri == null || !seen.add(uri.toString())) continue;
                try {
                    int persistFlags = resultFlags & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    if ((resultFlags & Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0) {
                        try { getContentResolver().takePersistableUriPermission(uri, persistFlags); } catch (Exception ignored) { }
                    }
                    PhotoTimeResolver.Metadata metadata = PhotoTimeResolver.read(this, uri);
                    if (metadata.widthPx <= 0 || metadata.heightPx <= 0) {
                        failures.add(metadata.displayName + "（不是可解碼圖片）");
                        continue;
                    }
                    loaded.add(new PhotoItem(uri, metadata.displayName, metadata.mimeType,
                            metadata.captureTime.millis, metadata.captureTime.source, metadata.sizeBytes,
                            metadata.widthPx, metadata.heightPx, order++));
                } catch (Exception e) {
                    failures.add(uri.getLastPathSegment() + "（無法讀取）");
                }
            }
            loaded.sort(photoComparator());
            runOnUiThread(() -> {
                if (!loaded.isEmpty()) {
                    if (replace) {
                        selectedPhotos.clear();
                        excludedUris.clear();
                    }
                    selectedPhotos.addAll(loaded);
                    selectedPhotos.sort(photoComparator());
                    latestResult = null;
                    saveButton.setEnabled(false);
                    shareButton.setEnabled(false);
                }
                if (previewUri == null || selectedPhotos.stream().noneMatch(item -> item.uri.toString().equals(previewUri))) {
                    previewUri = selectedPhotos.isEmpty() ? null : selectedPhotos.get(0).uri.toString();
                }
                processing = false;
                refreshList();
                String message = failures.isEmpty() ? "" : "有 " + failures.size() + " 個檔案無法讀取，已略過。\n" + String.join("、", failures);
                if (message.isEmpty()) {
                    if (loaded.isEmpty()) message = replace ? "沒有匯入有效照片；原清單保留。" : "沒有新增照片；原清單保留。";
                    else message = replace ? "已重新選取；依拍攝時間由早到晚排序。" : "已追加照片；重複項目已略過並依拍攝時間排序。";
                }
                setBusyUi(false, message);
            });
        });
    }

    private Comparator<PhotoItem> photoComparator() {
        return (a, b) -> {
            if (a.captureTimeMillis == null && b.captureTimeMillis != null) return 1;
            if (a.captureTimeMillis != null && b.captureTimeMillis == null) return -1;
            if (a.captureTimeMillis != null) {
                int byTime = Long.compare(a.captureTimeMillis, b.captureTimeMillis);
                if (byTime != 0) return byTime;
            }
            return Integer.compare(a.selectionOrder, b.selectionOrder);
        };
    }

    private void refreshList() {
        if (listContainer == null) return;
        listContainer.removeAllViews();
        countView.setText("已選取 " + selectedPhotos.size() + " 張");
        processButton.setEnabled(!selectedPhotos.isEmpty() && !processing);
        clearButton.setEnabled(!selectedPhotos.isEmpty() && !processing);
        excludeButton.setEnabled(!excludedUris.isEmpty() && !processing);
        excludeButton.setText(excludedUris.isEmpty() ? "確認排除勾選照片" : "確認排除勾選照片（" + excludedUris.size() + "）");
        View currentRow = null;
        for (int i = 0; i < selectedPhotos.size(); i++) {
            PhotoItem item = selectedPhotos.get(i);
            String key = item.uri.toString();
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(8), dp(4), dp(6), dp(4));
            boolean isCurrent = key.equals(previewUri);
            row.setBackgroundColor(isCurrent ? AppPalette.PANEL_ALT : (i % 2 == 0 ? AppPalette.WHITE : AppPalette.PAGE));

            TextView rowText = new TextView(this);
            rowText.setText(String.format(Locale.getDefault(), "%03d  %s", i + 1, item.originalFilename));
            rowText.setTextColor(AppPalette.TEXT);
            rowText.setTextSize(15);
            rowText.setSingleLine(true);
            rowText.setEllipsize(android.text.TextUtils.TruncateAt.END);
            rowText.setPadding(dp(4), dp(12), dp(4), dp(12));
            rowText.setOnClickListener(v -> {
                previewUri = key;
                refreshList();
            });
            rowText.setEnabled(!processing);
            row.addView(rowText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView currentTag = new TextView(this);
            currentTag.setText(isCurrent ? "目前" : "");
            currentTag.setTextColor(AppPalette.BRAND_DARK);
            currentTag.setTextSize(12);
            currentTag.setGravity(Gravity.CENTER);
            currentTag.setMinWidth(dp(38));
            row.addView(currentTag, new LinearLayout.LayoutParams(dp(42), dp(44)));

            CheckBox excludeCheck = new CheckBox(this);
            excludeCheck.setButtonTintList(ColorStateList.valueOf(AppPalette.BRAND));
            excludeCheck.setText("排除");
            excludeCheck.setTextSize(12);
            excludeCheck.setChecked(excludedUris.contains(key));
            excludeCheck.setEnabled(!processing);
            excludeCheck.setContentDescription("勾選排除第 " + (i + 1) + " 張照片");
            excludeCheck.setOnCheckedChangeListener((buttonView, checked) -> {
                if (checked) excludedUris.add(key); else excludedUris.remove(key);
                excludeButton.setEnabled(!excludedUris.isEmpty() && !processing);
                excludeButton.setText(excludedUris.isEmpty() ? "確認排除勾選照片" : "確認排除勾選照片（" + excludedUris.size() + "）");
            });
            row.addView(excludeCheck, new LinearLayout.LayoutParams(dp(76), dp(48)));

            Button remove = button("移除");
            remove.setTextSize(12);
            remove.setEnabled(!processing);
            remove.setOnClickListener(v -> confirmRemoveOne(item));
            row.addView(remove, new LinearLayout.LayoutParams(dp(54), dp(48)));

            LinearLayout.LayoutParams params = matchWrap();
            params.bottomMargin = dp(4);
            listContainer.addView(row, params);
            if (isCurrent) currentRow = row;
        }
        if (currentRow != null) {
            View rowToShow = currentRow;
            namesScrollView.post(() -> {
                int top = rowToShow.getTop();
                int bottom = rowToShow.getBottom();
                int viewTop = namesScrollView.getScrollY();
                int viewBottom = viewTop + namesScrollView.getHeight();
                if (top < viewTop) namesScrollView.smoothScrollTo(0, top);
                else if (bottom > viewBottom) namesScrollView.smoothScrollTo(0, bottom - namesScrollView.getHeight());
            });
        }
        refreshPreview();
    }

    private void refreshPreview() {
        if (previewImage == null) return;
        PhotoItem selected = null;
        for (PhotoItem item : selectedPhotos) {
            if (item.uri.toString().equals(previewUri)) { selected = item; break; }
        }
        if (selected == null) {
            previewImage.setImageDrawable(null);
            previewImage.setContentDescription("尚未選取照片");
            return;
        }
        PhotoItem item = selected;
        String key = item.uri.toString();
        previewImage.setContentDescription("目前照片預覽；保持原始比例顯示");
        previewImage.setImageDrawable(null);
        executor.execute(() -> {
            Bitmap decoded = null;
            try {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                try (InputStream input = getContentResolver().openInputStream(item.uri)) {
                    if (input != null) BitmapFactory.decodeStream(input, null, bounds);
                }
                int sample = 1;
                while (bounds.outWidth / sample > 1800 || bounds.outHeight / sample > 1800) sample *= 2;
                try (InputStream input = getContentResolver().openInputStream(item.uri)) {
                    if (input != null) {
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inSampleSize = sample;
                        decoded = BitmapFactory.decodeStream(input, null, options);
                    }
                }
                if (decoded != null) decoded = orientPreview(item.uri, decoded);
            } catch (Exception ignored) { }
            final Bitmap bitmap = decoded;
            runOnUiThread(() -> {
                if (key.equals(previewUri)) {
                    if (bitmap == null) Toast.makeText(this, "無法載入這張照片預覽；原照片仍保留。", Toast.LENGTH_SHORT).show();
                    else previewImage.setImageBitmap(bitmap);
                } else if (bitmap != null) bitmap.recycle();
            });
        });
    }

    private Bitmap orientPreview(Uri uri, Bitmap source) {
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) return source;
            ExifInterface exif = new ExifInterface(input);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            Matrix matrix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f);
                case ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f);
                case ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f);
                case ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setScale(-1f, 1f); matrix.postRotate(270f); }
                case ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f);
                case ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setScale(-1f, 1f); matrix.postRotate(90f); }
                case ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f);
                default -> { return source; }
            }
            Bitmap oriented = Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
            if (oriented != source) source.recycle();
            return oriented;
        } catch (Exception ignored) {
            return source;
        }
    }

    private void confirmBeforePackage() {
        if (!excludedUris.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("還有待排除照片")
                    .setMessage("右側已勾選 " + excludedUris.size() + " 張照片。請先按「確認排除勾選照片」完成排除，或取消勾選，再確認最終清單。")
                    .setPositiveButton("返回清單", null)
                    .show();
            return;
        }
        StringBuilder summary = new StringBuilder("即將依拍攝時間整理 " + selectedPhotos.size() + " 張照片。\n\n");
        for (int i = 0; i < selectedPhotos.size(); i++) {
            summary.append(String.format(Locale.getDefault(), "%03d  %s\n", i + 1, selectedPhotos.get(i).originalFilename));
        }
        summary.append("\n原始照片不會被刪除或修改。請確認清單後再繼續。");
        new AlertDialog.Builder(this)
                .setTitle("確認最終照片清單")
                .setMessage(summary.toString())
                .setNegativeButton("返回清單", null)
                .setPositiveButton("確認並整理", (dialog, which) -> createPackage())
                .show();
    }

    private void createPackage() {
        if (selectedPhotos.isEmpty() || processing) return;
        processing = true;
        latestResult = null;
        saveButton.setEnabled(false);
        shareButton.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setProgress(0);
        setBusyUi(true, "正在保留原始照片並建立交付檔案…");
        int batchNumber = getPreferences(MODE_PRIVATE).getInt("next_batch_number", 1);
        executor.execute(() -> {
            try {
                BatchExporter.Result result = BatchExporter.create(this, selectedPhotos, batchNumber, metadataOptions,
                        (current, total, message) -> runOnUiThread(() -> {
                            progressBar.setProgress(Math.min(100, current * 100 / Math.max(1, total)));
                            statusView.setText(message);
                        }));
                getPreferences(MODE_PRIVATE).edit().putInt("next_batch_number", Integer.parseInt(result.batchId.substring(6)) + 1).apply();
                runOnUiThread(() -> {
                    latestResult = result;
                    processing = false;
                    progressBar.setVisibility(View.GONE);
                    setBusyUi(false, result.batchId + " 已建立：" + result.imageCount + " 張照片、PDF、JSON、README、ZIP（" + formatSize(result.zipFile.length()) + "）");
                    saveButton.setEnabled(true);
                    shareButton.setEnabled(true);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    processing = false;
                    progressBar.setVisibility(View.GONE);
                    setBusyUi(false, "建立失敗：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
                });
            }
        });
    }

    private void saveZip() {
        if (latestResult == null) return;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_TITLE, latestResult.zipFile.getName());
        startActivityForResult(intent, REQUEST_SAVE_ZIP);
    }

    private void saveZipTo(Uri destination) {
        if (latestResult == null) return;
        executor.execute(() -> {
            try (InputStream input = new java.io.FileInputStream(latestResult.zipFile);
                 OutputStream output = getContentResolver().openOutputStream(destination, "w")) {
                if (output == null) throw new IllegalStateException("無法開啟儲存位置。");
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
                runOnUiThread(() -> Toast.makeText(this, "ZIP 已儲存", Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "儲存失敗：" + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void shareZip() {
        if (latestResult == null) return;
        try {
            Uri uri = ExportFileProvider.uriFor(this, latestResult.zipFile.getName());
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("application/zip");
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, "分享照片批次 ZIP"));
        } catch (Exception e) {
            Toast.makeText(this, "無法分享 ZIP：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void setBusyUi(boolean busy, String message) {
        statusView.setText(message);
        setUpdateButtonEnabled(!busy && !checkingUpdate);
        settingsButton.setEnabled(!busy);
        if (lockOuterButton != null) lockOuterButton.setEnabled(!busy);
        chooseButton.setEnabled(!busy);
        replaceButton.setEnabled(!busy);
        clearButton.setEnabled(!busy && !selectedPhotos.isEmpty());
        excludeButton.setEnabled(!busy && !excludedUris.isEmpty());
        processButton.setEnabled(!busy && !selectedPhotos.isEmpty());
        for (int i = 0; i < listContainer.getChildCount(); i++) {
            View row = listContainer.getChildAt(i);
            if (row instanceof LinearLayout) {
                LinearLayout layout = (LinearLayout) row;
                for (int j = 0; j < layout.getChildCount(); j++) layout.getChildAt(j).setEnabled(!busy);
            }
        }
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(AppPalette.BRAND_DARK);
        button.setBackgroundTintList(ColorStateList.valueOf(AppPalette.PANEL_ALT));
        return button;
    }

    private void stylePrimaryButton(Button button) {
        button.setTextColor(Color.WHITE);
        button.setBackgroundTintList(ColorStateList.valueOf(AppPalette.BRAND));
    }

    private void setUpdateButtonEnabled(boolean enabled) {
        if (updateButton != null) updateButton.setEnabled(enabled);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams matchWrapWithTop() {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(10);
        return params;
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private String formatSize(long bytes) {
        return String.format(Locale.getDefault(), "%.1f MB", bytes / 1024.0 / 1024.0);
    }

    @Override protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
