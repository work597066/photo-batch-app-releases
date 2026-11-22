# 照片批次整理 Beta 3

版本：0.1.2-beta3（版次碼 3）

## 更新內容
- App 每次開啟時自動檢查公開 GitHub 更新資訊。
- 有新版才提示使用者；由使用者選擇下載與安裝，或稍後處理。
- 下載 APK 後核對 SHA-256、App 套件名稱與版次。
- 安裝由 Android 系統確認；不會自動靜默安裝。
- 更新檢查只讀取公開版本資訊，不上傳照片或批次資料。

## GitHub 發佈方式
- Release tag：`v0.1.2-beta3`
- Release asset：`PhotoBatch-0.1.2-beta3-debug.apk`
- 將本資料夾的 `update.json` 放到儲存庫根目錄的 `main` 分支。
- 先建立 Release 並上傳 APK，再發布根目錄的 `update.json`。
- 不要上傳 BATCH_001 照片、JSON、PDF 或 ZIP。

這是 debug 測試版，套件名稱與本機 Android Debug 簽章沿用前一測試版，以便同通道升級。正式發佈前需改用固定且安全保存的正式簽章。

