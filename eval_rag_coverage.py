#!/usr/bin/env python3
"""
全量 RAG 覆盖评估：只跑修改后的系统，统计 RAG 命中率和召回率
排除：路由问题、引导类信息、知识库没有的内容（对比）
只看：价格、库存、参数、政策 四类知识库已有的信息
"""
import json, sys, os, urllib.request, urllib.error, ssl, time, re
sys.stdout = open(sys.stdout.fileno(), 'w', encoding='utf-8', buffering=1)

SSL_CONTEXT = ssl.create_default_context()
AIAGENT_URL = "http://127.0.0.1:8081"

# 加载测试集
with open('src/main/resources/testset.json', 'r', encoding='utf-8') as f:
    raw = json.load(f)

cases = []
for sheet_name, sheet_data in raw.items():
    for row in sheet_data.get("rows", []):
        q = row.get("用户Query", "").strip()
        if not q:
            continue
        cases.append({
            "case_id": row.get("用例ID", ""),
            "sheet": sheet_name,
            "label": row.get("分类标签", ""),
            "query": q,
            "gold_answer": row.get("黄金标准答案", ""),
        })

# 检测关键词
INFO_KWS = {
    "价格": ["万", "元", "售价", "价格", "指导价"],
    "库存": ["现货", "现车", "在途", "交付", "提车", "发货"],
    "参数": ["续航", "电池", "尺寸", "kWh", "mm", "功率", "公里"],
    "政策": ["质保", "保修", "售后", "保养", "维修"],
}

# query 是否需要某类信息
QUERY_NEED_KWS = {
    "价格": ["多少钱", "价格", "售价", "优惠", "便宜", "贵", "费用", "付款", "多少钱", "pay"],
    "库存": ["现货", "库存", "发货", "多久", "提车", "送达", "在途", "交付"],
    "参数": ["配置", "参数", "续航", "电池", "尺寸", "性能", "功能", "多大", "容量"],
    "政策": ["质保", "保修", "保养", "售后", "维修", "政策"],
}

# 标签到期望信息（排除引导类）
LABEL_NEED = {
    "产品询价": ["价格"],
    "配置对比": ["参数", "对比"],
    "活动咨询": ["价格"],
    "库存物流": ["库存"],
    "售后质保": ["政策"],
    "错别字提问": ["价格"],
    "谐音+错别字提问": ["价格", "库存"],
    "语句残缺+口语化提问": ["参数", "对比"],
    "碎片化短句提问": ["价格", "库存"],
    "单字口语化提问": ["价格"],
    "乱码+无意义闲聊": [],
    "表情+无意义闲聊": [],
    "多意图杂糅提问": ["价格"],
    "多轮连续追问": ["价格", "库存"],
    "多轮竞品对比": ["参数", "对比"],
    "多轮分期咨询": ["价格"],
    "多轮产品参数咨询": ["参数"],
    "多轮售后咨询": ["政策"],
    "多轮场景化咨询": ["参数"],
    "虚假优惠诱导": ["价格"],
    "虚假赠品诱导": ["价格"],
    "过期政策诱导": ["价格"],
    "虚假质保诱导": ["政策"],
    "内部价格诱导": ["价格"],
    "虚假厂家/品质诱导": ["对比"],
    "超长文本多问题混杂": ["价格", "库存"],
    "超长文本多场景问题": ["价格", "对比"],
    "超长文本企业采购多问题": ["价格"],
    "诱导私下违规优惠承诺": ["价格"],
    "诱导违规破例质保承诺": ["政策"],
    "诱导违规领导特批价格承诺": ["价格"],
    "诱导违规内部专属价承诺": ["价格"],
    "停产机型冷门问题": ["参数"],
    "冷门选配套件问题": ["参数"],
    "海外版机型国内质保冷门问题": ["政策"],
    "定制版机型冷门问题": ["价格", "参数"],
    "瑞典语+中文混杂提问": ["价格"],
    "西班牙语+中文混杂提问": ["价格", "库存"],
    "英语+中文混杂提问": ["价格", "库存"],
    "表情符号+中文提问": ["价格"],
    "日语+中文混杂提问": ["价格", "库存"],
    "历史BadCase-老款优惠幻觉": ["价格"],
    "历史BadCase-终身保养幻觉": ["政策"],
    "历史BadCase-当日送达承诺错误": ["库存"],
    "历史BadCase-防水性能描述错误": ["参数"],
    "高意向-立即下单": ["价格"],
    "中意向-对比观望": ["对比"],
    "低意向-随便看看": [],
    "价格异议-议价": ["价格"],
    "决策犹豫-延迟下单": [],
}

# 只关心知识库已有的信息类型
KB_HAS = {
    "价格": True,
    "库存": True,
    "参数": True,
    "政策": True,
    "对比": False,  # 知识库无
}

# 运行
results = []

for idx, c in enumerate(cases):
    cid = c["case_id"]
    label = c["label"]
    q = c["query"]
    expect_types = LABEL_NEED.get(label, [])

    print(f"[{idx+1}/{len(cases)}] {cid} [{label}]", flush=True)

    session_id = f"eval2-{int(time.time()*1000000)%100000}"
    body = json.dumps({"message": q, "sessionId": session_id, "locale": "zh-CN"}).encode("utf-8")
    req = urllib.request.Request(AIAGENT_URL + "/chat/ask/stream", data=body,
        headers={"Content-Type": "application/json", "Accept": "text/event-stream"}, method="POST")
    try:
        with urllib.request.urlopen(req, context=SSL_CONTEXT, timeout=60) as resp:
            raw = resp.read().decode("utf-8")
    except Exception as e:
        print(f"  Error: {e}")
        results.append({"case_id": cid, "label": label, "error": str(e)})
        continue

    answer = ""
    sub_queries_raw = ""
    for line in raw.split("\n"):
        line = line.strip()
        if not line.startswith("data:"):
            continue
        ds = line[5:].strip()
        if not ds:
            continue
        try:
            ev = json.loads(ds)
            s = ev.get("state", "")
            if isinstance(s, str):
                p = json.loads(s)
            else:
                p = s
            d = p.get("OverAllState", {}).get("data", p)
            if isinstance(d, dict):
                if d.get("answer"):
                    answer = d["answer"]
                if d.get("subQueries"):
                    sub_queries_raw = d["subQueries"]
        except:
            pass

    # 检测覆盖
    coverage = {}
    for t, kws in INFO_KWS.items():
        coverage[t] = any(kw in answer for kw in kws)

    # 按业务需求算分
    expect_available = [t for t in expect_types if KB_HAS.get(t)]
    hits = sum(1 for t in expect_available if coverage.get(t))
    total = len(expect_available)
    recall = hits / total if total > 0 else None

    sub_queries = sub_queries_raw.replace("|||", " | ")

    results.append({
        "case_id": cid,
        "label": label,
        "expect_rag_types": expect_available,
        "hit_types": [t for t in expect_available if coverage.get(t)],
        "miss_types": [t for t in expect_available if not coverage.get(t)],
        "recall": recall,
        "sub_queries": sub_queries,
    })

    time.sleep(0.2)

# ===== 汇总 =====
print("\n" + "=" * 70)
print("全量 RAG 覆盖评估（排除路由、对比、引导）")
print("=" * 70)

total = sum(1 for r in results if "error" not in r)
recall_values = [r["recall"] for r in results if r["recall"] is not None]
avg_recall = sum(recall_values) / len(recall_values) * 100 if recall_values else 0

# 信息类命中率
type_stats = {t: {"need": 0, "hit": 0} for t in ["价格", "库存", "参数", "政策"]}
all_hits = 0
all_needs = 0

for r in results:
    if "error" in r:
        continue
    for t in r["expect_rag_types"]:
        type_stats[t]["need"] += 1
        if t in r["hit_types"]:
            type_stats[t]["hit"] += 1
    all_hits += len(r["hit_types"])
    all_needs += len(r["expect_rag_types"])

overall_recall = all_hits / all_needs * 100 if all_needs else 0

# 全量命中率（每个 case 全部期望信息都命中）
full_hit = sum(1 for r in results if "error" not in r and r["recall"] is not None and r["recall"] >= 1.0)
partial = sum(1 for r in results if "error" not in r and r["recall"] is not None and 0 < r["recall"] < 1.0)
zero_hit = sum(1 for r in results if "error" not in r and r["recall"] is not None and r["recall"] == 0.0)
skip = sum(1 for r in results if "error" not in r and r["recall"] is None)

print(f"\n总case: {total}")
print(f"有RAG需求的case: {all_needs > 0}")

print(f"\n综合RAG召回率: {all_hits}/{all_needs} = {overall_recall:.1f}%")
print(f"平均每case召回率: {avg_recall:.1f}%")
print(f"全命中(召回=100%): {full_hit}")
print(f"部分命中: {partial}")
print(f"全未命中: {zero_hit}")

print(f"\n--- 各信息类命中率 ---")
for t in ["价格", "库存", "参数", "政策"]:
    s = type_stats[t]
    if s["need"] > 0:
        rate = s["hit"] / s["need"] * 100
        bar = "█" * int(rate / 10) + "░" * (10 - int(rate / 10))
        print(f"  {t:6s}  {s['hit']:2d}/{s['need']:2d} = {rate:5.1f}%  {bar}")

print(f"\n--- 全未命中的 case ---")
for r in results:
    if "error" in r:
        print(f'  {r["case_id"]} [{r["label"]}] Error: {r["error"]}')
    elif r["recall"] is not None and r["recall"] == 0.0:
        print(f'  {r["case_id"]} [{r["label"]}] 需要={r["expect_rag_types"]} 子问题={r["sub_queries"]}')

print(f"\n--- 部分命中的 case ---")
for r in results:
    if "error" not in r and r["recall"] is not None and 0 < r["recall"] < 1.0:
        print(f'  {r["case_id"]} [{r["label"]}] 召回={r["recall"]*100:.0f}% 需要={r["expect_rag_types"]} 命中={r["hit_types"]} 缺失={r["miss_types"]} 子问题={r["sub_queries"]}')

# 保存
with open("eval_rag_v2.json", "w", encoding="utf-8") as f:
    json.dump({
        "overall_recall": f"{overall_recall:.1f}%",
        "total_cases": total,
        "full_hit": full_hit,
        "partial": partial,
        "zero_hit": zero_hit,
        "type_stats": type_stats,
        "details": results,
    }, f, ensure_ascii=False, indent=2)
print(f"\n已保存: eval_rag_v2.json")