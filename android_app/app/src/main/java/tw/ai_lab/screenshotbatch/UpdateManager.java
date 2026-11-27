package tw.ai_lab.screenshotbatch;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;

/** Reads a public GitHub-hosted update manifest and verifies downloaded APKs before install. */
final class UpdateManager {
    static final String MANIFEST_URL = "https://raw.githubusercontent.com/work597066/photo-batch-app-releases/main/update.json";
    private static final int MAX_MANIFEST_BYTES = 1024 * 1024;
    private static final long MAX_APK_BYTES = 200L * 1024L * 1024L;
    private static final String APK_FILENAME = "PhotoBatch-update.apk";

    static final class UpdateInfo {
        final int versionCode;
        final String versionName;
        final String applicationId;
        final String apkUrl;
        final String sha256;
        final String releaseNotes;

        UpdateInfo(JSONObject json) throws Exception {
            versionCode = json.getInt("versionCode");
            versionName = json.getString("versionName");
            applicationId = json.getString("applicationId");
            apkUrl = json.getString("apkUrl");
            sha256 = json.getString("sha256").toLowerCase(Locale.ROOT);
            releaseNotes = json.optString("releaseNotes", "");
            if (versionCode <= 0 || versionName.trim().isEmpty() || applicationId.trim().isEmpty()) {
                throw new IllegalArgumentException("更新資訊缺少有效版次或套件名稱。");
            }
            if (!apkUrl.startsWith("https://") || !sha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("APK 下載網址或 SHA-256 格式不正確。");
            }
        }
    }

    private UpdateManager() { }

    static UpdateInfo fetchLatest() throws Exception {
        HttpURLConnection connection = openConnection(MANIFEST_URL);
        try {
            connection.setRequestProperty("Accept", "application/json");
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) throw new IllegalStateException("更新伺服器回應 " + status + "。");
            byte[] bytes = readLimited(connection.getInputStream(), MAX_MANIFEST_BYTES);
            return new UpdateInfo(new JSONObject(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)));
        } finally {
            connection.disconnect();
        }
    }

    static File downloadAndVerify(Context context, UpdateInfo info) throws Exception {
        if (!context.getPackageName().equals(info.applicationId)) {
            throw new IllegalArgumentException("新版 App 套件名稱不符，已停止安裝。");
        }
        File dir = new File(context.getCacheDir(), "updates");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IllegalStateException("無法建立更新暫存資料夾。");
        File apk = new File(dir, APK_FILENAME);
        if (apk.exists() && !apk.delete()) throw new IllegalStateException("無法清除舊的更新暫存檔。");

        HttpURLConnection connection = openConnection(info.apkUrl);
        try {
            connection.setRequestProperty("Accept", "application/vnd.android.package-archive, application/octet-stream");
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) throw new IllegalStateException("APK 下載失敗，伺服器回應 " + status + "。");
            long contentLength = connection.getContentLengthLong();
            if (contentLength > MAX_APK_BYTES) throw new IllegalArgumentException("APK 超過允許的大小。");
            try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(apk)) {
                byte[] buffer = new byte[64 * 1024];
                long total = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_APK_BYTES) throw new IllegalArgumentException("APK 超過允許的大小。");
                    output.write(buffer, 0, count);
                }
                if (total == 0) throw new IllegalStateException("下載的 APK 是空檔案。");
            }
        } catch (Exception e) {
            apk.delete();
            throw e;
        } finally {
            connection.disconnect();
        }

        if (!info.sha256.equals(sha256(apk))) {
            apk.delete();
            throw new SecurityException("APK SHA-256 驗證失敗，已刪除下載檔。");
        }
        PackageInfo archive = context.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), 0);
        if (archive == null || !info.applicationId.equals(archive.packageName) || archive.versionCode != info.versionCode) {
            apk.delete();
            throw new SecurityException("APK 套件或版次與更新資訊不符，已停止安裝。");
        }
        return apk;
    }

    static void requestInstall(Context context, File apk) {
        Uri uri = UpdateApkProvider.uriFor(context, apk.getName());
        Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
        intent.setData(uri);
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(intent);
    }

    private static HttpURLConnection openConnection(String address) throws Exception {
        URL url = new URL(address);
        if (!"https".equalsIgnoreCase(url.getProtocol())) throw new SecurityException("更新只允許使用 HTTPS。");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(20000);
        connection.setInstanceFollowRedirects(true);
        connection.setUseCaches(false);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "PhotoBatch-Android-Updater");
        return connection;
    }

    private static byte[] readLimited(InputStream input, int maximum) throws Exception {
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            int total = 0;
            while ((count = in.read(buffer)) != -1) {
                total += count;
                if (total > maximum) throw new IllegalArgumentException("更新資訊檔過大。");
                out.write(buffer, 0, count);
            }
            return out.toByteArray();
        }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder result = new StringBuilder(64);
        for (byte value : digest.digest()) result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return result.toString();
    }

    static int installedVersionCode(Context context) throws PackageManager.NameNotFoundException {
        PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        if (Build.VERSION.SDK_INT >= 28) return (int) info.getLongVersionCode();
        return info.versionCode;
    }
}
