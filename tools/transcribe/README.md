# transcribe｜音訊轉逐字稿

`python transcribe.py 課程01.m4a --model small` → 產生 `課程01.srt`、`課程01.txt`。

## 驗證狀態（2026-11-27，Claude Code 雲端）
- 已驗證：Common Voice 台灣華語 12 句（12 位不同說話者，共 49 秒），small 模型、CPU：
  轉錄 9 秒，與原文字元相似度 94.2%。錯字多為同音字（例：蹣跚→蠻山、同事→紅柿）。
- 未驗證：真實課程錄音（長時間、單一講者）。準確度不夠時改用 `--model medium` 或 `large-v3`（較慢）。
- 雲端環境需放行 Hugging Face 網址才能下載模型；本工作階段已建立個人環境
  「photo-batch-app-releases」並放行。
