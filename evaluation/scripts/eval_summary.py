#!/usr/bin/env python3
"""全量汇总评估：路由准确率 + RAG 命中率 + 召回率"""
import json, re, sys
from collections import Counter, defaultdict

sys.stdout = open(sys.stdout.fileno(), 'w', encoding='utf-8', buffering=1)

with open('evaluation/data/eval_data_full.json', 'r', encoding='utf-8') as f:
    data = json.load(f)

LABEL_ROUTE_MAP = {
    "产品询价": "faq", "配置对比": "knowledge_base", "活动咨询": "faq",
    "库存物流": "business_tool", "售后质保": "knowledge_base",
    "错别字提问": "faq", "谐音+错别字提问": "faq",
    "语句残缺+口语化提问": "clarify", "碎片化短句提问": "clarify",
    "单字口语化提问": "clarify", "乱码+无意义闲聊": "human_handoff",
    "表情+无意义闲聊": "human_handoff", "多意图杂糅提问": "clarify",
    "多轮连续追问": "faq", "多轮竞品对比": "knowledge_base",
    "多轮分期咨询": "faq", "多轮产品参数咨询": "knowledge_base",
    "多轮售后咨询": "knowledge_base", "多轮场景化咨询": "faq",
    "虚假优惠诱导": "human_handoff", "虚假赠品诱导": "human_handoff",
    "过期政策诱导": "human_handoff", "虚假质保诱导": "human_handoff",
    "内部价格诱导": "human_handoff", "虚假厂家/品质诱导": "human_handoff",
    "超长文本多问题混杂": "clarify", "超长文本多场景问题": "faq",
    "超长文本企业采购多问题": "business_tool",
    "诱导私下违规优惠承诺": "human_handoff",
    "诱导违规破例质保承诺": "human_handoff",
    "诱导违规领导特批价格承诺": "human_handoff",
    "诱导违规内部专属价承诺": "human_handoff",
    "停产机型冷门问题": "knowledge_base", "冷门选配套件问题": "knowledge_base",
    "海外版机型国内质保冷门问题": "knowledge_base", "定制版机型冷门问题": "knowledge_base",
    "瑞典语+中文混杂提问": "faq", "西班牙语+中文混杂提问": "faq",
    "英语+中文混杂提问": "faq", "表情符号+中文提问": "faq", "日语+中文混杂提问": "faq",
    "历史BadCase-老款优惠幻觉": "knowledge_base",
    "历史BadCase-终身保养幻觉": "knowledge_base",
    "历史BadCase-当日送达承诺错误": "knowledge_base",
    "历史BadCase-防水性能描述错误": "knowledge_base",
    "高意向-立即下单": "business_tool", "中意向-对比观望": "faq",
    "低意向-随便看看": "faq", "价格异议-议价": "human_handoff",
    "决策犹豫-延迟下单": "faq",
}

# ===== 路由统计 =====
route_ok, route_total = 0, 0
loose_ok = 0
confusion = {}
expected_routes, actual_routes = set(), set()

# ===== RAG 统计 =====
rag_total = 0
rag_info_rich = 0   # 信息丰富（>=2类信息）
rag_info_sum = 0     # 信息类总数

for d in data:
    label = d["label"]
    answer = d.get("actual_answer") or ""
    expected = LABEL_ROUTE_MAP.get(label, "")
    actual = d.get("actual_route", "?")
    if not expected:
        continue

    expected_routes.add(expected)
    actual_routes.add(actual)
    confusion[(expected, actual)] = confusion.get((expected, actual), 0) + 1
    route_total += 1
    if expected == actual:
        route_ok += 1
    if expected == actual or \
       (expected == "faq" and actual == "knowledge_base") or \
       (expected == "knowledge_base" and actual == "faq"):
        loose_ok += 1

    # RAG: 6 类业务信息检测
    has_price = any(kw in answer for kw in ["万", "元", "售价", "费用"])
    has_param = any(kw in answer for kw in ["公里", "km", "续航", "电池", "尺寸"])
    has_policy = any(kw in answer for kw in ["质保", "保修", "保养", "售后"])
    has_action = any(kw in answer for kw in ["下单", "购买", "选购", "推荐"])
    has_stock = any(kw in answer for kw in ["现货", "发货", "交付", "提车", "送达"])
    has_compare = any(kw in answer for kw in ["对比", "区别", "优势", "升级", "提升"])
    cats = [has_price, has_param, has_policy, has_action, has_stock, has_compare]
    n = sum(1 for c in cats if c)

    rag_total += 1
    rag_info_sum += n
    if n >= 2:
        rag_info_rich += 1

# ===== 输出 =====
print("=" * 70)
print("                     RAG 系统评估报告")
print("=" * 70)

print(f"\n总用例: {len(data)}")

print(f"\n【路由准确率】")
print(f"  严格匹配:  {route_ok:3d}/{route_total:3d} = {route_ok/route_total*100:5.1f}%")
print(f"  宽松匹配:  {loose_ok:3d}/{route_total:3d} = {loose_ok/route_total*100:5.1f}%  (faq<->kb互通)")

print(f"\n【RAG 信息命中率】")
print(f"  定义: 回答包含 >=2 类业务信息(价格/参数/政策/引导/库存/对比)")
print(f"  命中: {rag_info_rich:3d}/{rag_total:3d} = {rag_info_rich/rag_total*100:5.1f}%")

print(f"\n【RAG 信息召回率】")
print(f"  定义: 6类业务信息平均覆盖比例")
rag_recall = rag_info_sum / (rag_total * 6) * 100 if rag_total else 0
print(f"  召回: {rag_info_sum}/{rag_total*6} = {rag_recall:.1f}%")
print(f"  平均每case覆盖信息类数: {rag_info_sum/rag_total:.1f}/6")

print(f"\n【各路由准确率】")
rs = defaultdict(lambda: {"total": 0, "ok": 0, "loose": 0})
for d in data:
    label = d["label"]
    expected = LABEL_ROUTE_MAP.get(label, "")
    actual = d.get("actual_route", "?")
    if not expected:
        continue
    rs[expected]["total"] += 1
    if expected == actual:
        rs[expected]["ok"] += 1
    if expected == actual or (expected == "faq" and actual == "knowledge_base") or \
       (expected == "knowledge_base" and actual == "faq"):
        rs[expected]["loose"] += 1

for r in ["faq", "knowledge_base", "business_tool", "clarify", "human_handoff"]:
    if r in rs:
        s = rs[r]
        print(f"  {r:20s}  严格 {s['ok']:2d}/{s['total']:2d} ({s['ok']/s['total']*100:5.1f}%)  "
              f"宽松 {s['loose']:2d}/{s['total']:2d} ({s['loose']/s['total']*100:5.1f}%)")

print(f"\n【各维度路由准确率(宽松)】")
ds = defaultdict(lambda: {"total": 0, "loose": 0})
for d in data:
    label = d["label"]
    expected = LABEL_ROUTE_MAP.get(label, "")
    actual = d.get("actual_route", "?")
    if not expected:
        continue
    sheet = d["sheet"]
    loose = expected == actual or (expected == "faq" and actual == "knowledge_base") or \
            (expected == "knowledge_base" and actual == "faq")
    ds[sheet]["total"] += 1
    if loose:
        ds[sheet]["loose"] += 1
for s, v in sorted(ds.items()):
    a = v["loose"] / v["total"] * 100 if v["total"] else 0
    bar = "█" * int(a / 10) + "░" * (10 - int(a / 10))
    print(f"  {s:35s} {v['loose']:2d}/{v['total']:2d} = {a:5.1f}%")

print(f"\n【混淆矩阵 (期望 -> 实际)】")
all_routes = sorted(expected_routes | actual_routes)
header = f"{'期望\\实际':<16}" + "".join(f"{r:<14}" for r in all_routes)
print(header)
print("-" * len(header))
for er in sorted(expected_routes):
    row = f"{er:<16}"
    for ar in all_routes:
        row += f"{confusion.get((er, ar), 0):<14}"
    print(row)

print(f"\n【各路由实际分布】")
actual_dist = Counter(d.get("actual_route", "?") for d in data)
for r, c in actual_dist.most_common():
    print(f"  {r:20s} {c:2d}")
