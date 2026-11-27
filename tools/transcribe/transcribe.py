"""把音訊／影片檔轉成逐字稿（繁體中文），輸出 .srt 與 .txt。

用法：python transcribe.py 檔案1.m4a [檔案2.mp3 ...] [--model small]
需求：pip install faster-whisper；系統要有 ffmpeg。
網路：第一次執行要從 Hugging Face 下載模型（huggingface.co、us.aws.cdn.hf.co、
      cas-server.xethub.hf.co 等網址要放行）。
"""
import argparse, subprocess, sys, pathlib
import numpy as np
from faster_whisper import WhisperModel


def load(path):
    # 用 ffmpeg 解碼（避開 faster-whisper 內建解碼器與 av 版本不相容的問題）
    raw = subprocess.run(['ffmpeg', '-loglevel', 'error', '-i', str(path), '-f', 'f32le',
                          '-ac', '1', '-ar', '16000', '-'], capture_output=True, check=True).stdout
    return np.frombuffer(raw, np.float32)


def ts(s):
    ms = int(round(s * 1000)); h, ms = divmod(ms, 3600000); m, ms = divmod(ms, 60000); x, ms = divmod(ms, 1000)
    return f'{h:02}:{m:02}:{x:02},{ms:03}'


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('files', nargs='+')
    ap.add_argument('--model', default='small', help='tiny/base/small/medium/large-v3')
    a = ap.parse_args()
    model = WhisperModel(a.model, device='cpu', compute_type='int8')
    for f in map(pathlib.Path, a.files):
        segs, info = model.transcribe(load(f), language='zh', vad_filter=True,
                                      initial_prompt='以下是繁體中文的課程內容。')
        srt, txt = [], []
        for i, s in enumerate(segs, 1):
            t = s.text.strip()
            srt.append(f'{i}\n{ts(s.start)} --> {ts(s.end)}\n{t}\n'); txt.append(t)
            print(f'[{ts(s.start)[:8]}] {t}', flush=True)
        f.with_suffix('.srt').write_text('\n'.join(srt), encoding='utf-8')
        f.with_suffix('.txt').write_text('\n'.join(txt), encoding='utf-8')
        print(f'完成：{f.with_suffix(".srt")}', file=sys.stderr)


if __name__ == '__main__':
    main()
