#!/usr/bin/env python3
"""截图取色探针：没长眼睛的 agent 怎么「看」界面。

动机：验证四套风格不能靠「我觉得像」。这里把真机截图转成像素，
量出**背景色、卡片填充色、描边是否存在、圆角半径**这几件事 ——
它们正好就是四套风格彼此不同的地方，也正好是最容易被做糊的地方
（比如四个选项其实都画成了同一套玻璃）。

用法：
  python3 tools/px.py shot                    # 拉一张截图到 shot.png
  python3 tools/px.py color X Y               # 某一点的颜色
  python3 tools/px.py region X1 Y1 X2 Y2      # 一块区域的平均色
  python3 tools/px.py probe X1 Y1 X2 Y2       # 卡片体检：背景/填充/描边/圆角
  python3 tools/px.py scan X Y1 Y2            # 竖着扫一列，打印颜色变化的位置（找元素边界）
  python3 tools/px.py hscan Y X1 X2           # 横着扫一行
坐标是设备像素（与 uiautomator dump 的 bounds 同一套）。
"""
import os, subprocess, sys
import numpy as np

SERIAL = os.environ.get("ANDROID_SERIAL", "192.168.5.11:5555")
PNG = os.environ.get("PX_PNG", "/data/data/com.termux/files/home/jmc/shot.png")
RAW = "/data/data/com.termux/files/home/jmc/shot.raw"


def adb(*args):
    return subprocess.run(["adb", "-s", SERIAL] + list(args), capture_output=True)


def shot():
    adb("shell", "screencap", "-p", "/sdcard/px.png")
    adb("pull", "/sdcard/px.png", PNG)
    subprocess.run(["convert", PNG, "-depth", "8", "rgb:" + RAW], check=True)
    return load()


def load():
    with open(RAW, "rb") as f:
        data = np.frombuffer(f.read(), dtype=np.uint8)
    # 尺寸从 PNG 头读（IHDR 里宽高各 4 字节，大端）
    with open(PNG, "rb") as f:
        head = f.read(33)
    w = int.from_bytes(head[16:20], "big")
    h = int.from_bytes(head[20:24], "big")
    return data.reshape(h, w, 3)


def hexs(px):
    return "#%02X%02X%02X" % tuple(int(v) for v in px)


def main():
    img = shot()
    print(f"截图 {img.shape[1]}x{img.shape[0]}")
    if len(sys.argv) < 2:
        return
    cmd = sys.argv[1]
    a = [int(v) for v in sys.argv[2:]]
    if cmd == "color":
        x, y = a
        print(hexs(img[y, x]))
    elif cmd == "region":
        x1, y1, x2, y2 = a
        patch = img[y1:y2, x1:x2].reshape(-1, 3).mean(axis=0)
        print(hexs(patch))
    elif cmd == "probe":
        x1, y1, x2, y2 = a
        bg = img[y1 - 8:y1 - 3, (x1 + x2) // 2 - 3:(x1 + x2) // 2 + 3].reshape(-1, 3).mean(axis=0)
        mid_y = (y1 + y2) // 2
        # 从左边界内侧取填充色（避开描边与圆角）
        fill = img[mid_y - 4:mid_y + 4, x1 + 20:x1 + 40].reshape(-1, 3).mean(axis=0)
        print("背景 ", hexs(bg))
        print("填充 ", hexs(fill))
        print("填充-背景 距离 %.1f" % np.linalg.norm(fill - bg))
        # 描边：横向扫过左边界，看有没有既不像背景也不像填充的一列
        row = img[mid_y, x1 - 3:x1 + 6].astype(float)
        line = " ".join(hexs(p) for p in row)
        dev = [np.linalg.norm(row[i] - bg) + np.linalg.norm(row[i] - fill) for i in range(len(row))]
        print("边界扫描", line)
        print("描边证据 %.0f （越大越像有一条独立的描边线）" % max(dev))
        # 圆角：在 x1+2 与卡片中部两个位置各找一次顶边的 y，差值≈半径
        def top_edge(x):
            col = img[y1 - 30:y2, x].astype(float)
            for i, p in enumerate(col):
                if np.linalg.norm(p - bg) > 24:
                    return y1 - 30 + i
            return None
        e_corner, e_flat = top_edge(x1 + 2), top_edge((x1 + x2) // 2)
        if e_corner is not None and e_flat is not None:
            print("圆角半径 ≈ %d px" % (e_corner - e_flat))
    elif cmd in ("scan", "hscan"):
        # 沿着一条线打印「颜色在哪一段发生了变化」——不用猜坐标就能找到元素边界。
        # 容差 6：小于它的变化是渐变/抗锯齿，不是边界
        tol = 6
        if cmd == "scan":
            x, y1, y2 = a
            line = img[y1:y2, x].astype(int)
            coords = list(range(y1, y2))
        else:
            y, x1, x2 = a
            line = img[y, x1:x2].astype(int)
            coords = list(range(x1, x2))
        start = 0
        for i in range(1, len(line)):
            if np.abs(line[i] - line[start]).max() > tol:
                if i - start >= 3:
                    mid = (start + i) // 2
                    print(f"{coords[start]}..{coords[i-1]}  {hexs(line[mid])}")
                start = i
        if len(line) - start >= 3:
            mid = (start + len(line)) // 2
            print(f"{coords[start]}..{coords[-1]}  {hexs(line[mid])}")
    else:
        print(__doc__)


if __name__ == "__main__":
    main()
