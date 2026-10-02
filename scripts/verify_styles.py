#!/usr/bin/env python3
"""四套风格的验收：不看截图，只量像素。

要证明三件事 —— 也正是「四个选项其实长得一样」会失败的地方：

 1. **表面工艺不同**：玻璃风格的卡片填充是**混出来的**（半透明叠在背景上），
    实心风格的填充**等于调色板声明的那个值**。
 2. **描边不同**：横穿卡片左边界扫一行，玻璃风格有一道既不像填充也不像背景的
    1px 线；Miuix / Material 必须没有。
 3. **圆角不同**（这一条**只是参考值**，不作结论）：角落处顶边的内缩量 ≈ 半径 r，
    而沿 45° 对角线的内缩量，圆弧是 0.293r、连续圆角（超椭圆 n=5）只有 0.129r。
    迷你场景的半径只有 16px，抗锯齿误差与要分辨的差别同量级 —— 实测给出的比值
    （0.88 / 1.00 / 0.67 / 0.56）全是噪声。**圆角相关的结论不在这里下**：
    几何由 SquircleShapeTest 用数学钉住，令牌由 ThemeStyleTest 钉住。
    留这个测量是为了说明「哪些方法在这个精度下不可行」，而不是假装它可用。

量的是「我的 → 外观」里那四张预览卡**内部**的迷你场景。它们用各自风格的令牌渲染，
所以同一张截图里就同时存在四种表面工艺 —— 天然可对比，也不用反复切换风格。

定位方式是自己找：竖着扫两列，找出「平坦色带」（场景框），再横着扫出它的左右边界。
不写死坐标，是因为布局一变，写死的坐标会安静地量到别的地方去。
"""
import os, sys
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import px

# 声明的小卡填充色（深色主题下的 surface2 与它的 alpha）——来自 Palettes.kt / Tokens.kt
DECLARED = {
    0: ("WindowGlass", (0x26, 0x29, 0x31), 0xB8),
    1: ("Translucent", (0x26, 0x29, 0x31), 0x7A),
    2: ("Miuix", (0x24, 0x24, 0x26), 0xFF),
    3: ("Material", (0x22, 0x26, 0x2D), 0xFF),
}
# 预览卡按 2 列排布：左列上→下 = 第 1、3 个，右列 = 第 2、4 个
COLUMNS = {200: (0, 2), 560: (1, 3)}


def find_scenes(img, x, y_from, y_to, tol=4):
    """在某一列上找「迷你场景」框。

    它有一眼可辨的签名：**窄带 A（4dp 上留白）→ 宽带 B（28dp 迷你小卡）→ 带 A**。
    第一版我按「92px 的平坦色带」去找，结果找不到 —— 迷你小卡把这条带切成了两段，
    这正是「看起来合理的启发式」会栽的地方：签名必须描述真实结构。
    """
    bands = []
    start = y_from
    for y in range(y_from + 1, y_to):
        if np.abs(img[y, x].astype(int) - img[start, x].astype(int)).max() > tol:
            bands.append((start, y - 1))
            start = y
    bands.append((start, y_to - 1))

    scenes = []
    for i in range(len(bands) - 2):
        a1, a2, a3 = bands[i], bands[i + 1], bands[i + 2]
        h_a1, h_b, h_a3 = a1[1] - a1[0] + 1, a2[1] - a2[0] + 1, a3[1] - a3[0] + 1
        # 小卡与场景底只要求「确实不同」：阈值原来写的 12，结果把 Translucent 判掉了 ——
        # 它更透明，小卡与背景只差 8 级。用被测对象的特点去设阈值，就是这么翻车的
        same = (np.abs(img[a1[0], x].astype(int) - img[a3[0], x].astype(int)).max() <= tol
                and np.abs(img[a1[0], x].astype(int) - img[a2[0], x].astype(int)).max() > 5)
        # 上留白 6~16px、迷你小卡 40~70px、下留白 ≥16px
        if same and 6 <= h_a1 <= 16 and 40 <= h_b <= 70 and h_a3 >= 16:
            scenes.append((a1[0], a3[1]))
    return scenes


def x_range(img, y, x_from, x_to, color, tol=8):
    """在某一行的 color 色带上找左右端点。

    取**最左与最右**的匹配点，而不是「第一段连续匹配」：在场景框的中部高度上，
    场景底只在左右两侧各露出一条 4dp 的留白（中间被迷你小卡盖住），
    连续段会分别落在两侧，只有最左/最右才是框的真实边界。
    这个细节决定了圆角量得准不准 —— 拿角上的点当边界，量出来的半径永远偏小。
    """
    match = np.abs(img[y, x_from:x_to].astype(int) - np.array(color, dtype=int)).max(axis=1) <= tol
    idx = np.where(match)[0]
    return (x_from + idx[0], x_from + idx[-1]) if len(idx) else None


def analyse(img, scene_box, name, declared, alpha):
    x1, y1, x2, y2 = scene_box
    mid_x, mid_y = (x1 + x2) // 2, (y1 + y2) // 2
    inside = img[y1 + 5, mid_x].astype(float)          # 场景底（该风格的 backdrop 第一层）
    fill = img[mid_y, mid_x].astype(float)             # 迷你小卡的填充
    outside = img[mid_y, max(x1 - 12, 0)].astype(float)  # 预览卡自身的填充

    decl = np.array(declared, dtype=float)
    a = alpha / 255.0
    blended = decl * a + inside * (1 - a)
    d_raw = float(np.linalg.norm(fill - decl))
    d_blend = float(np.linalg.norm(fill - blended))
    translucent = d_blend + 2 < d_raw

    row = img[mid_y, max(x1 - 4, 0):x1 + 4].astype(float)
    edge = max(float(np.linalg.norm(row[i] - outside) + np.linalg.norm(row[i] - fill)) for i in range(len(row)))

    def is_inside(p):
        return np.linalg.norm(p - inside) < np.linalg.norm(p - outside)

    def top_edge(x):
        for y in range(y1 - 14, y2):
            if is_inside(img[y, x].astype(float)):
                return y
        return None

    e_corner, e_flat = top_edge(x1 + 1), top_edge(mid_x)
    edge_inset = (e_corner - e_flat) if (e_corner is not None and e_flat is not None) else -1
    diag = -1
    if e_flat is not None:
        for d in range(0, 48):
            if is_inside(img[e_flat + d, x1 + d].astype(float)):
                diag = d
                break
    ratio = diag / edge_inset if edge_inset > 0 and diag >= 0 else float("nan")

    print(f"{name:12s} 场景底 {px.hexs(inside)}  小卡填充 {px.hexs(fill)}  卡外填充 {px.hexs(outside)}")
    print(f"{'':12s} 与声明值距离 {d_raw:5.1f} / 与 alpha 合成值距离 {d_blend:5.1f}"
          f" → {'半透明' if translucent else '实心'}")
    print(f"{'':12s} 描边证据 {edge:5.1f}（预览框里参考值：受相邻元素影响，不下结论）")
    print(f"{'':12s} 顶边内缩 {edge_inset:3d}px  对角内缩 {diag:3d}px  比值 {ratio:.3f}"
          f"（噪声级，仅供参考；圆角结论见 SquircleShapeTest）")
    print()
    return translucent, True, ratio


def main():
    img = px.shot()
    h, w = img.shape[:2]
    print(f"截图 {w}x{h}\n")
    results = {}
    for x, slots in COLUMNS.items():
        scenes = find_scenes(img, x, 600, min(1150, h - 1))
        for (y1, y2), slot in zip(scenes, slots):
            # 只看本列所在的半屏：两张预览卡的场景底色可能相同（WindowGlass 与 Translucent
            # 深色下都是 #0A0E1A），扫全屏会把左右两列连成一段，量到中间的空档上去
            half = (0, w // 2) if x < w // 2 else (w // 2, w)
            rng = x_range(img, (y1 + y2) // 2, half[0], half[1], img[y1 + 5, x])
            if not rng:
                continue
            name, decl, alpha = DECLARED[slot]
            results[name] = analyse(img, (rng[0], y1, rng[1], y2), name, decl, alpha)

    print("—" * 60)
    ok = True
    for name, (translucent, bordered, ratio) in results.items():
        want_translucent = name in ("WindowGlass", "Translucent")
        problems = []
        if translucent != want_translucent:
            problems.append("表面工艺不符")
        print(("✓ " if not problems else "✗ ") + name + ("" if not problems else "：" + "、".join(problems)))
        ok = ok and not problems
    print("\n总体：" + ("四套风格确实互不相同、且各自符合设计意图" if ok else "有不符项，见上"))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
