import math

MFIX = 9.4
MAX = 40.0

def cost(dmg, gb, pfix):
    return (dmg * MFIX) ** pfix * (1.0 - gb)

def blocks(c):
    return math.floor(MAX / c) if c > 0 else 999

print("=== A. 校验设计文档 6.3 数值示例表 ===")
rows = [
    ("PvE 僵尸(3) 铁制小圆盾", 3, 0.35, 0.7, 6.1),
    ("PvE 铁傀儡(15) 铁固盾牌", 15, 0.55, 0.7, 15.2),
    ("PvP 钻石剑(7) 钻石小圆盾", 7, 0.45, 0.9, 19.7),
    ("PvP 下界合金斧(10) 钻石大盾", 10, 0.70, 0.9, 25.9),
    ("PvE 5伤害 下界合金大盾+强力防御", 5, 0.85, 0.7, 3.7),
]
print(f"{'场景':<38}{'dmg':>5}{'gb':>7}{'PFIX':>6}{'文档值':>9}{'公式值':>9}{'偏差':>9}{'可挡次数':>9}")
for name, d, gb, p, docv in rows:
    c = cost(d, gb, p)
    print(f"{name:<38}{d:>5}{gb:>7.2f}{p:>6.2f}{docv:>9.1f}{c:>9.2f}{c-docv:>+9.2f}{blocks(c):>9}")

print()
print("=== B. 修正后的数值速查表（PvE, PFIX=0.7）===")
gbs = [
    ("剑(默认)", 0.20), ("木质小圆盾", 0.25), ("铁制小圆盾", 0.35), ("钻石小圆盾", 0.45),
    ("下界合金小圆盾", 0.50), ("原版盾牌", 0.40), ("铁固盾牌", 0.55), ("钻石制盾牌", 0.60),
    ("下界合金盾牌", 0.65), ("木制大盾", 0.50), ("铁制大盾", 0.65), ("钻石大盾", 0.70),
    ("下界合金大盾", 0.75), ("木大盾+PG", 0.575), ("铁大盾+PG", 0.755), ("钻大盾+PG", 0.820),
    ("合金大盾+PG", 0.875),
]
dmgs = [3, 5, 7, 10, 15, 20]
for pfix, tag in ((0.7, "PvE"), (0.9, "PvP")):
    print(f"\n--- {tag} (PFIX={pfix}) 单次格挡体力消耗 / 40体力可挡次数 ---")
    hdr = f"{'装备':<18}" + "".join(f"{'dmg'+str(d):>14}" for d in dmgs)
    print(hdr)
    for name, gb in gbs:
        line = f"{name:<18}"
        for d in dmgs:
            c = cost(d, gb, pfix)
            line += f"{c:>7.1f}/{blocks(c):<6}"
        print(line)

print()
print("=== C. 常见威胁：40 体力可持续格挡次数 ===")
threats = [("僵尸", 3, 0.7), ("蜘蛛/骷髅箭", 4, 0.7), ("僵尸猪灵", 5, 0.7),
           ("末影人", 7, 0.7), ("铁傀儡", 15, 0.7), ("苦力怕爆炸(普通,近距)", 25, 0.7),
           ("玩家 钻石剑", 7, 0.9), ("玩家 合金斧", 10, 0.9), ("玩家 力量II合金剑", 13, 0.9)]
for name, d, p in threats:
    line = f"{name:<22} dmg={d:<3}{'PvE' if p==0.7 else 'PvP'}  "
    for gname, gb in [("剑", 0.20), ("小盾", 0.45), ("中盾", 0.55), ("大盾", 0.75), ("大盾+PG", 0.875)]:
        c = cost(d, gb, p)
        line += f" | {gname}:{c:>5.1f}×{blocks(c):>2}"
    print(line)

print()
print("=== D. 强力防御净体力收支（40上限，格挡中恢复2/s，PG消耗2/s）===")
print("  净速率 = 2.0(格挡中恢复) - 2.0(PG消耗) = 0.0 点/秒  → 静止不动时体力不增不减")
print()
print("=== E. v2.0 恢复时间（两段式：<=0 走 8/s 立即恢复；>0 走 4/s 且受 2s 延迟约束）===")
TICK = 1.0 / 20.0
REGEN = 4.0            # stamina > 0
DEPLETED_REGEN = 8.0   # stamina <= 0
DELAY = 2.0            # 仅当 stamina > 0 时生效

def recovery(start):
    """返回 (枯竭段耗时, 延迟等待, 正常段耗时, 总耗时)"""
    t, st, depl = 0.0, float(start), 0.0
    # 阶段 1：stamina <= 0，8/s，无视延迟
    while st <= 0 and t < 3600:
        st += DEPLETED_REGEN * TICK
        t += TICK
        depl += TICK
    # 阶段 2：stamina > 0，需等过 2 秒延迟
    wait = max(0.0, DELAY - t)
    t += wait
    # 阶段 3：4/s 恢复到上限
    normal = max(0.0, (MAX - st) / REGEN)
    t += normal
    return depl, wait, normal, t

print(f"  {'起始':>5} | {'枯竭段(8/s)':>12} | {'延迟等待':>9} | {'正常段(4/s)':>12} | {'纯恢复耗时':>11} | {'总耗时(含延迟)':>14}")
for start in [20, 1, 0, -10, -20, -40]:
    d, w, n, total = recovery(start)
    pure = d + n
    print(f"  {start:>5} | {d:>11.2f}s | {w:>8.2f}s | {n:>11.2f}s | {pure:>10.2f}s | {total:>13.2f}s")

print()
print("  【v1.0 破防 vs v2.0 枯竭】对比（纯恢复耗时，不含延迟）")
print(f"  {'起始':>5} | {'v1.0(全程8/s至40)':>18} | {'v2.0(分段)':>12} | 差异")
for start in [0, -10, -20]:
    v1 = (MAX - start) / 8.0
    d, w, n, _ = recovery(start)
    v2 = d + n
    print(f"  {start:>5} | {v1:>17.2f}s | {v2:>11.2f}s | {v2 - v1:+.2f}s")

print()
print("=== F. 公式边界（最小值/上限）===")
for gb in [0.01, 1.00]:
    print(f"  gb={gb}: dmg=1 -> {cost(1,gb,0.7):.3f} (PvE), {cost(1,gb,0.9):.3f} (PvP)")
print(f"  最小可能消耗 (dmg=0.5, gb=0.875, PvE) = {cost(0.5,0.875,0.7):.3f}")
print(f"  最大可能消耗 (dmg=30, gb=0.01, PvP) = {cost(30,0.01,0.9):.3f}")
