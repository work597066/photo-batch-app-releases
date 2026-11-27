# AGENTS.md｜AI 團隊啟動指示與共同交接紀錄

暗號：雙號號測試 11/24 之 A

## 1. 文件用途

本文件先作為 ChatGPT、Work、Codex 協作交接本的測試版，記錄已驗證的環境能力、工作原則、錯誤檢討及後續接續事項。

核心目的：讓 AI 從已知事實與既有成果出發，不重複詢問、不憑空推測，並優先以實際操作驗證未知問題。

## 2. 已驗證的環境與能力

### GitHub Repository
- Repository：`work597066/photo-batch-app-releases`
- 分支：`main`
- 本文件：`AGENTS.md`

### ChatGPT 與 GitHub
2026-11-24 已由 ChatGPT 實際完成：
1. 搜尋並找到上述 Repository。
2. 讀取 Repository 根目錄，辨識 APK、README.md、RELEASE_NOTES.md、update.json。
3. 讀取 README.md、RELEASE_NOTES.md、update.json 的內容。
4. 建立本 Markdown 檔案並提交至 GitHub。
5. 再次讀取本檔，確認提交後的內容存在且可讀。

首次建立本檔的 Commit：`a36df6d017d0efc08a1981079f0d0eafd7fa3d36`。

本次更新將在 GitHub 回傳新的 Commit 後，才算完成。

### Work、Codex 與 Windows
使用者已明確說明，先前已在 Windows 10 環境啟用 Work 與 Codex，交付 APP 開發任務，並成功將 APK 上傳 GitHub。這是使用者已完成的實際成果；本次對話沒有重新執行 Work/Codex 開發流程。

因此，不得再把 Windows 11 當成啟用或測試 Work/Codex 的必要前提，也不得要求使用者重新證明已完成的操作。

### APP 發布資料
2026-11-27 Claude Code 讀取 Repository 根目錄，可見：
- `PhotoBatch-0.1.2-beta3-debug.apk` 至 `PhotoBatch-0.1.10-beta11-debug.apk`（共 9 個 APK）
- `README.md`
- `RELEASE_NOTES.md`
- `update.json`

README 與 update.json 指向最新測試版 **0.1.10-beta11**，版次碼 11（APP 名稱：圖知）。這是讀取時看到的內容；未重新測試 APK 安裝或執行。

### Claude 與 Claude Code（2026-11-27 實測）
1. **對話不互通**：Claude（claude.ai 聊天）與 Claude Code 是各自獨立的對話，彼此看不到對方的問答。兩邊共用的記憶只有本 Repository；結論必須寫進本檔（或日後的 `handoff/` 目錄）並合併進 `main`，另一邊才讀得到。
2. **Claude Code 會自動讀取本檔**：Claude Code 在本 Repository 開工時，本 `AGENTS.md` 已自動載入其指示，無須使用者提醒。
3. **Claude 聊天端尚未驗證**：需接上 GitHub，並在專案指示中明確要求「開始前先讀 `AGENTS.md`」。
4. **Claude Code 的分支限制**：Claude Code 雲端工作階段只能推送到指定的 `claude/...` 分支，需經 PR 合併進 `main` 後，其他夥伴才看得到。
5. **邀請 GitHub 協作者**：Claude Code 以 `work597066` 身分可讀寫本 Repository，但查詢協作者名單被拒（403 Resource not accessible by integration），也沒有寄送邀請的工具。邀請真人協作者須由使用者在 GitHub 網頁操作：Settings → Collaborators → Add people。網址：https://github.com/work597066/photo-batch-app-releases/settings/access

## 3. 2026-11-24 參謀工作檢討

### 發生的問題
今天討論 AI 協作架構時，ChatGPT 多次先介紹一般產品功能、提出架構建議，卻沒有先盤點使用者已經擁有且實際跑通的環境。

具體錯誤包括：
- 忽略使用者已在 Windows 10 成功使用 Work、Codex 完成 APP 並上傳 GitHub 的事實。
- 不必要地詢問是否有 Windows 11，造成使用者誤以為升級作業系統才可測試。
- 已經在先前對話測試過 GitHub 讀取，仍再次把讀取能力當成未知。
- 在尚可直接測試 GitHub 寫入時，先用「需要再確認」延後驗證；直到使用者要求，才實際建立本檔。
- 把規劃與提問放在實際執行之前，導致長時間討論沒有及早產生可驗收成果。

### 根本原因
參謀工作方法偏向提出可能方案，而不是先整理事實、辨識未知、動手驗證。也沒有妥善延續已經完成的測試結果，讓使用者必須反覆提醒。

### 修正原則
1. 先讀取既有紀錄與專案資料，再回答；不得把已知事實重新當成未知問題。
2. 清楚區分「使用者已完成」、「本次已實測」、「尚未驗證」及「推測／建議」。
3. 能直接測試的項目，先在授權範圍內執行最小測試，再提出結論。
4. 不要求使用者重複提供已存在的資訊；若缺少資料，先查現有 Repository、檔案及對話紀錄。
5. 不以一般產品限制推翻使用者已經成功的實際環境；遇到矛盾時，查明具體版本、入口、權限與操作路徑。
6. 不把「提出計畫」說成「完成工作」。每個任務都要標示狀態與證據。
7. 建議必須對應尚未解決的問題；不要為了架構完整而增加不必要的模組或工作。
8. 發現錯誤時，直接更正既有紀錄及後續做法，不要只口頭道歉。

## 4. 每次開始相關工作時的啟動檢查

當 ChatGPT 在新對話中要繼續這個 AI 協作／LifeOS／APP 專案，而且能存取此 GitHub Repository 時，應先：

1. 讀取本 `AGENTS.md` 最新版本。
2. 讀取相關專案的 README、任務紀錄、狀態表及最近更新。
3. 摘出已完成、進行中、待處理、阻塞及尚未驗證事項。
4. 對照使用者這次的新指示，只更新有變動的部分。
5. 先處理可以直接執行的工作；只有遇到真正缺少且無法查得的必要資訊，才向使用者詢問。
6. 完成操作後，更新紀錄並回報可驗證的結果、連結或 Commit。

**重要限制：**本 `AGENTS.md` 存在 GitHub，不代表每一段新對話會自動讀取它。只有在該對話具備 GitHub 連線／工具權限，且啟動流程或專案指示明確要求先讀取時，才能可靠執行。若當下無法存取，應坦白說明，不能假稱已讀取。

## 5. 建議的任務紀錄格式

每項任務至少記錄：
- 任務 ID 與標題
- 提出日期與需求原文／整理後目標
- 負責角色（使用者、ChatGPT、Work、Codex 或其他夥伴）
- 前置資料與相關檔案
- 執行步驟
- 狀態：待處理／進行中／待驗收／已完成／阻塞
- 驗收標準
- 實際結果與證據（檔案路徑、GitHub 連結、Commit、測試結果）
- 未解決問題與下一步
- 最後更新日期

## 6. 目前結論與下一步

### 已完成
- ChatGPT 讀取 GitHub Repository 目錄及文字檔。
- ChatGPT 建立 Markdown 檔並成功提交。
- ChatGPT 重新讀取建立後的檔案，確認內容存在。
- 使用者先前已完成 Work、Codex 在 Windows 10 環境協作開發 APP 並將 APK 上傳 GitHub。

### 尚未完成
- 尚未建立正式、可長期維護的任務狀態表與專案總紀錄。
- 尚未驗證 ChatGPT 在每一個新對話都能自動啟動讀取本文件；這需要可用連線及明確的啟動指示。
- 尚未把 Work、Codex 的啟動前讀取、執行後回寫規則正式加入其工作指示。
- 尚未測試多個 AI 同時更新同一份紀錄時的衝突處理。

### 下一步建議
先確認現有 Repository 是否適合兼作交接資料庫；若它目前只負責 APK 發布，應評估在同一 Repository 增設獨立的 `handoff/` 或 `project-log/` 目錄，避免把工作管理文件與 APK 發布資訊混在根目錄。正式改動前先盤點 Work、Codex 現有讀寫方式及使用者希望的資料邊界。

---
最後更新：2026-11-27
