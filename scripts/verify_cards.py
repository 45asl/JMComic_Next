#!/usr/bin/env python3
"""按风格逐个量**页面级大卡片**：圆角内缩与描边。

预览卡里的迷你场景只有 16px 半径，抗锯齿误差与要分辨的差别同量级（量出来全是噪声），
所以圆角与描边改在大卡片上量：内容屏蔽卡片宽 624px，半径在 16~32px 之间，
误差占比小一个数量级。

判据：
 - **圆角内缩**：在左边缘内侧 2px 处，顶边的 y 相对卡片中部顶边的偏移。
   圆弧几何上这个偏移是 `r - sqrt(4r-4)`：r=16px(8dp)≈8px、r=24px(12dp)≈14px、
   r=32px(16dp)≈21px。四套风格的圆角尺度不同，这个数就该不同。

   **这一段在真机上没量成功，留着当记录。** 两个原因：
   1. 玻璃风格的填充本来就贴着背景色（半透明），边界根本找不到 —— 这恰恰又是它透明的证据；
   2. 照片壁纸下背景横向不均匀，跨列比较会在远离采样列处误判。
   所以圆角相关的结论改用单元测试钉（SquircleShapeTest 量几何、ThemeStyleTest 量令牌），
   描边与半透明这两条才是靠这里量出来的。
 - **描边**：横穿卡片左边缘扫一行，玻璃风格应出现一条既不像卡片填充也不像页面底色的
   1px 线；Miuix / Material 应该是「页面底色→卡片填充」的直接跳变。
"""
import re, subprocess, sys, os
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import px
import ui

STYLES = ["WindowGlass", "Translucent", "Miuix", "Material"]


def title_y():
    """「外观」卡片的标题位置。

    量的是**风格选择器所在的那张卡**：它是唯一切换风格时一定在视野里的大卡片
    （内容屏蔽在下面，滚走了就量不到 —— 第一版就是这么失败的）。
    """
    out = subprocess.run([sys.executable, os.path.join(os.path.dirname(os.path.abspath(__file__)), "ui.py"),
                          "dump", "^外观$"], capture_output=True, text=True).stdout
    m = re.search(r"\((\d+), (\d+),", out)
    return int(m.group(2)) if m else None


def main():
    for style in STYLES:
        subprocess.run([sys.executable, os.path.join(os.path.dirname(os.path.abspath(__file__)), "ui.py"),
                        "tap", f"t:^{style}$"], capture_output=True, text=True)
        import time
        time.sleep(3)
        y = title_y()
        if y is None:
            print(f"{style}: 找不到内容屏蔽卡片（预览卡不在视野内？）")
            continue
        img = px.shot()
        fill_guess = img[y, 600].astype(float)
        # 卡片顶边：从标题那一行往上走，颜色离开卡片填充的那一步就是顶边。
        # 不能用「标题 y 减去内边距」估：文字控件的 bounds 是**字形**的，不含行高留白，
        # 估出来的顶边会落在卡片内部，于是「圆角内缩」恒为 0（第一版就是这样量出 0 的）
        top = None
        for yy in range(y, y - 140, -1):
            if np.linalg.norm(img[yy, 360].astype(float) - fill_guess) > 18:
                top = yy + 1
                break
        if top is None:
            print(f"{style}: 找不到卡片顶边")
            continue
        bg = img[top - 6, 360].astype(float)       # 卡片之间的页面底色

        def first_card_pixel(x):
            """该列上卡片的上边界。

            参考色取**同一列**上方的背景像素，而不是 x=360 处采样到的那个背景色：
            背景是照片壁纸 + 渐变，横向本来就不均匀，跨列比较会在远离采样列的地方
            立刻「发现」差别（上一版因此量出 -18px 这种几何上不可能的负数）。
            """
            ref = img[top - 6, x].astype(float)
            for yy in range(top - 6, top + 60):
                if np.linalg.norm(img[yy, x].astype(float) - ref) > 20:
                    return yy
            return None

        corner, flat = first_card_pixel(50), first_card_pixel(360)
        fill = img[y, 600].astype(float)           # 卡片填充（标题同一行、最右侧空白处）
        row = img[y, 40:64].astype(float)
        seg = [px.hexs(p) for p in row]
        # 描边证据：存在既不像 bg 也不像 fill 的像素
        distinct = [i for i, p in enumerate(row)
                    if np.linalg.norm(p - bg) > 12 and np.linalg.norm(p - fill) > 12]
        print(f"{style:12s} 页面底 {px.hexs(bg)}  卡片填充 {px.hexs(fill)}")
        inset = (corner - flat) if (corner is not None and flat is not None) else None
        print(f"{'':12s} 圆角内缩 {'  n/a' if inset is None else f'{inset:>4}'} px"
              f"   （8dp≈8 / 12dp≈14 / 16dp≈21）")
        print(f"{'':12s} 左边缘像素 {seg[0]} {seg[1]} … {seg[-2]} {seg[-1]}"
              f"   异常像素 {len(distinct)} → {'有描边' if distinct else '无描边（直接跳变）'}")
        print()


if __name__ == "__main__":
    main()
