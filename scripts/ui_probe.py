#!/usr/bin/env python3
"""真机 UI 探针：按控件树里的**文本/描述**定位并点击，不依赖屏幕方向。

动机：这台测试机的自动旋转是开着的，同一段交互里两次 uiautomator dump 可能一次横屏一次竖屏，
写死坐标的脚本会点空（我因此浪费过好几轮）。所有操作都从「刚 dump 出来的树」里取 bounds，
所以横竖屏都对。屏幕被软键盘盖住底部导航栏也是同一类坑：找不到目标就报错而不是乱点。

用法：
  python3 tools/ui.py dump [正则]          # 列出节点（可选过滤）
  python3 tools/ui.py tap <正则>           # 点第一个匹配的节点中心
  python3 tools/ui.py wait <正则> [秒]     # 轮询直到出现
  python3 tools/ui.py text                 # 只列文本
  python3 tools/ui.py tabs                 # 列出底部导航的可点区域（按 x 排序）
  python3 tools/ui.py tab <序号>           # 点第 n 个底部导航项（1 起，从左到右）

底部导航的**文字节点 bounds 是 [0,0][0,0]**（Compose 的 NavigationBar 就是这样被导出的），
所以「按文本点搜索 Tab」永远点不到。这里改为：取屏幕下缘那条带状区域里的可点节点，按 x 排序取第 n 个。
设备用 -s/--serial 指定，默认 $ANDROID_SERIAL 或 192.168.5.11:5555。
"""
import os, re, subprocess, sys, time

SERIAL = os.environ.get("ANDROID_SERIAL", "192.168.5.11:5555")
REMOTE = "/sdcard/ui_probe.xml"


def _adb(*args, binary=False, stdin=None):
    cmd = ["adb", "-s", SERIAL] + list(args)
    r = subprocess.run(cmd, capture_output=True, input=stdin)
    if r.returncode != 0 and not binary:
        sys.stderr.write(r.stderr.decode("utf-8", "replace"))
    return r.stdout if binary else r.stdout.decode("utf-8", "replace")


def dump():
    _adb("shell", "rm", "-f", REMOTE)
    out = _adb("shell", "uiautomator", "dump", REMOTE)
    if "dumped" not in out:
        raise SystemExit("dump 失败：" + out.strip())
    return _adb("shell", "cat", REMOTE)


def nodes(xml):
    for m in re.finditer(r"<node[^>]*>", xml):
        n = m.group(0)
        def attr(k):
            a = re.search(k + r'="([^"]*)"', n)
            return a.group(1) if a else ""
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        if not b:
            continue
        x1, y1, x2, y2 = map(int, b.groups())
        yield {
            "text": attr("text"), "desc": attr("content-desc"), "cls": attr("class"),
            "clickable": 'clickable="true"' in n,
            "center": ((x1 + x2) // 2, (y1 + y2) // 2),
            "bounds": (x1, y1, x2, y2),
        }


def find(xml, pattern):
    """匹配文本或内容描述。

    前缀 `t:` 只匹配文本、`d:` 只匹配内容描述 —— 因为页面上常常**同名**：
    搜索页的标题是「搜索」，右上角的搜索图标 content-desc 也是「搜索」，
    不加区分会点到标题上（点了没反应，白等一轮）。
    """
    mode = "both"
    if pattern.startswith("t:"):
        mode, pattern = "text", pattern[2:]
    elif pattern.startswith("d:"):
        mode, pattern = "desc", pattern[2:]
    rx = re.compile(pattern)
    def hit(n):
        if mode in ("both", "text") and rx.search(n["text"]):
            return True
        if mode in ("both", "desc") and rx.search(n["desc"]):
            return True
        return False
    hits = [n for n in nodes(xml) if hit(n)]
    # 文本节点本身常常不是可点区域（点击落在祖先上），但坐标一样，取中心即可
    return hits


def main():
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    cmd = sys.argv[1]
    if cmd == "dump":
        pat = sys.argv[2] if len(sys.argv) > 2 else None
        for n in nodes(dump()):
            label = n["text"] or n["desc"]
            if not label.strip():
                continue
            if pat and not re.search(pat, label):
                continue
            print(f'{n["bounds"]} {"C" if n["clickable"] else " "} {label[:70]!r}')
    elif cmd == "text":
        for n in nodes(dump()):
            if n["text"].strip():
                print(n["text"])
    elif cmd == "tap":
        pat = sys.argv[2]
        hits = find(dump(), pat)
        if not hits:
            raise SystemExit(f"没找到匹配 {pat!r} 的节点（键盘是否盖住了目标？）")
        for n in hits[:6]:
            print("命中:", n["bounds"], repr(n["text"] or n["desc"])[:60])
        x, y = hits[0]["center"]
        print("点击:", x, y)
        _adb("shell", "input", "tap", str(x), str(y))
    elif cmd in ("tab", "tabs"):
        xml = dump()
        all_nodes = list(nodes(xml))
        if not all_nodes:
            raise SystemExit("控件树是空的")
        w = max(n["bounds"][2] for n in all_nodes)
        h = max(n["bounds"][3] for n in all_nodes)
        band = [n for n in all_nodes if n["clickable"] and n["bounds"][1] > h * 0.85
                and (n["bounds"][2] - n["bounds"][0]) > 0]
        band.sort(key=lambda n: n["bounds"][0])
        if cmd == "tabs":
            print(f"屏幕 {w}x{h}；底部可点区域 {len(band)} 个（当前选中项不可点，所以常常只有 3 个）")
            for i, n in enumerate(band, 1):
                print(" ", i, n["bounds"], repr(n["text"] or n["desc"])[:40])
            for slot in range(1, 5):
                print(f"  第 {slot} 格中心 x={w * (2 * slot - 1) // 8}")
        else:
            # 参数是「第几格」（1..4，从左到右），不是「第几个可点区域」——
            # 当前选中的 Tab 不可点，用后者会整体错位
            slot = int(sys.argv[2])
            if not 1 <= slot <= 4:
                raise SystemExit("格子序号只能是 1..4")
            y = (min(n["bounds"][1] for n in band) + max(n["bounds"][3] for n in band)) // 2 if band else h - 60
            x = w * (2 * slot - 1) // 8
            print(f"点击底部第 {slot} 格: {x},{y}")
            _adb("shell", "input", "tap", str(x), str(y))
    elif cmd == "wait":
        pat = sys.argv[2]
        limit = float(sys.argv[3]) if len(sys.argv) > 3 else 30
        t0 = time.time()
        while time.time() - t0 < limit:
            if find(dump(), pat):
                print("出现:", pat)
                return
            time.sleep(1.5)
        raise SystemExit(f"超时未见 {pat!r}")
    else:
        raise SystemExit(__doc__)


if __name__ == "__main__":
    main()
