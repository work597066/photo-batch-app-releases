# 圖知 Android App 原始碼

版本：`0.1.10-beta11`（versionCode 11）

這是圖知 Android App 的原始碼專案。公開版本安裝檔與更新說明位於儲存庫根目錄。

## 建置需求

- Android Studio（支援 Android Gradle Plugin 8.13）
- JDK 17
- Android SDK Platform 36、Build Tools 36
- Gradle 8.13（可使用專案內的 Gradle Wrapper）

在 Android Studio 開啟此資料夾並同步 Gradle，或在 Windows 執行 `gradlew.bat assembleDebug` 建置測試版。APK 會輸出到 `app/build/outputs/apk/debug/`。

原始碼不包含簽章金鑰。從原始碼自行建置的 APK 會使用建置電腦上的 Debug 簽章，未必能直接升級已安裝的公開測試版；發布升級版必須使用相同的私人簽章金鑰。請勿將金鑰或密碼上傳至 GitHub。

## App 範圍

圖知只處理使用者自行選取的照片，產生 JSON、PDF 與 ZIP 封存；原始照片不會因整理而修改。App 不會將照片上傳到雲端，也不包含照片內容 AI 辨識或地址智慧分析。
