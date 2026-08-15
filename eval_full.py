#!/usr/bin/env python3
"""
aiagent 完整评估脚本 v2 — 路由准确率 + RAG 命中率 + 召回率
从 testset.json 加载 52 条用例，逐条调用 SSE 接口评估
"""
import json, sys, time, urllib.request, urllib.error, ssl, re, os
from collections import Counter, defaultdict

# 解决 Windows 控制台编码
sys.stdout = open(sys.stdout.fileno(), 'w', encoding='utf-8', buffering=1)
sys.stderr = open(sys.stderr.fileno(), 'w', encoding='utf-8', buffering=1)

AIAGENT_URL = "http://127.0.0.1:8081"
TIMEOUT_SEC = 60
SSL_CONTEXT = ssl.create_default_context()

# 标签→期望路由映射
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


def load_testset(path):
    """从 testset.json 加载用例"""
    with open(path, "r", encoding="utf-8") as f:
        raw = json.load(f)
    cases = []
    for sheet_name, sheet_data in raw.items():
        for row in sheet_data.get("rows", []):
            query = row.get("用户Query", "").strip()
            label = row.get("分类标签", "").strip()
            gold = row.get("黄金标准答案", "")
            if not query:
                continue
            expected = LABEL_ROUTE_MAP.get(label, "")
            cases.append({
                "sheet": sheet_name,
                "case_id": row.get("用例ID", ""),
                "query": query,
                "label": label,
                "expected_route": expected,
                "gold_answer": gold,
            })
    return cases


def call_agent(query, session_id=None):
    """调用 SSE 接口，返回最终 state 和 answer，以及 semantic_route 的 decision"""
    if session_id is None:
        session_id = "eval-v2-" + str(int(time.time() * 1000000) % 10000000)
    url = AIAGENT_URL + "/chat/ask/stream"
    body = json.dumps({"message": query, "sessionId": session_id, "locale": "zh-CN"}).encode("utf-8")
    req = urllib.request.Request(url, data=body, headers={
        "Content-Type": "application/json", "Accept": "text/event-stream"
    }, method="POST")
    try:
        with urllib.request.urlopen(req, context=SSL_CONTEXT, timeout=TIMEOUT_SEC) as resp:
            raw = resp.read().decode("utf-8")
    except Exception as e:
        return None, str(e)

    state = {}
    answer = ""
    route_decision = {}  # 从 semantic_route 节点提取

    for line in raw.split("\n"):
        line = line.strip()
        if not line.startswith("data:"):
            continue
        ds = line[5:].strip()
        if not ds:
            continue
        try:
            ev = json.loads(ds)
        except json.JSONDecodeError:
            continue

        # 从 semantic_route 节点提取路由决策
        node = ev.get("node", "")
        s_raw = ev.get("state", "")
        if node == "semantic_route" and s_raw:
            try:
                parsed = json.loads(s_raw) if isinstance(s_raw, str) else s_raw
                data = parsed.get("OverAllState", {}).get("data", parsed)
                if isinstance(data, dict):
                    route_decision = {
                        "route": data.get("route"),
                        "confidence": data.get("confidence"),
                        "decisionReason": data.get("decisionReason"),
                        "codeScores": data.get("codeScores"),
                        "llmScores": data.get("llmScores"),
                        "finalScores": data.get("finalScores"),
                    }
            except (json.JSONDecodeError, TypeError):
                pass

        # 合并所有 state
        if s_raw:
            try:
                parsed = json.loads(s_raw) if isinstance(s_raw, str) else s_raw
                data = parsed.get("OverAllState", {}).get("data", parsed)
                if isinstance(data, dict):
                    state.update(data)
            except (json.JSONDecodeError, TypeError):
                pass

        # 收集 answer（从 ai_reply 或最终 state）
        if isinstance(s_raw, str) and s_raw:
            try:
                p3 = json.loads(s_raw)
                d3 = p3.get("OverAllState", {}).get("data", p3)
                if isinstance(d3, dict) and d3.get("answer"):
                    answer = d3["answer"]
            except:
                pass
        elif isinstance(s_raw, dict):
            d3 = s_raw.get("OverAllState", {}).get("data", s_raw)
            if isinstance(d3, dict) and d3.get("answer"):
                answer = d3["answer"]

    return {"state": state, "answer": answer, "route_decision": route_decision}, None


def extract_rag_keywords(gold_answer, query=""):
    """
    从黄金标准答案提取 RAG 期望关键词。
    策略：取 query 中的有意义的词，在 gold_answer 中校验存在性。
    如果 query 词太短，则从 gold_answer 取高频有意义的词。
    """
    if not gold_answer:
        return []

    # 先从 query 中提取有意义的词
    q_words = re.findall(r'[一-鿿A-Za-z0-9一-鿿]{2,}', query) if query else []
    # 停用词
    stop_words = {"您好", "产品", "什么", "怎么", "多久", "一个", "可以", "需要", "没有",
                  "这个", "那个", "还是", "不是", "就是", "如果", "因为", "所以", "但是",
                  "请问", "你好", "感谢", "非常", "我们", "目前", "当前", "现在", "已经",
                  "进行", "以及", "之后", "同时", "还有", "所有", "都是", "就是", "是否",
                  "能够", "您的", "我们的", "给您", "为您", "帮您", "可以", "这款", "这款产品",
                  "能不能", "会不会", "要不", "还是", "或者", "比较", "一些", "很多", "有关",
                  "来说", "的话", "那个", "一下", "一样", "这么", "那么", "怎么", "哪个",
                  "吗?", "吗", "呢?", "呢", "吧?", "吧", "了?", "了", "的?", "的", "啊", "哦",
                  "你说", "我说", "看看", "这样", "那样"}

    # 方法1: 从 query 取词
    keywords = []
    for w in q_words:
        if w in stop_words or len(w) > 8:
            continue
        if w in gold_answer and w not in keywords:
            keywords.append(w)
        if len(keywords) >= 5:
            break

    # 方法2: 如果 query 词不够，从 gold_answer 取高频词
    if len(keywords) < 2:
        all_words = re.findall(r'[一-鿿]{2,}', gold_answer)
        freq = Counter(all_words)
        for w, c in freq.most_common():
            if w in stop_words or len(w) > 6:
                continue
            if c >= 2 and w not in keywords:
                keywords.append(w)
            if len(keywords) >= 5:
                break

    return keywords[:5]


def check_rag_hit(answer, keywords):
    """检查 answer 中是否包含期望关键词"""
    if not answer or not keywords:
        return [], 0, 0
    hits = [kw for kw in keywords if kw in answer]
    return hits, len(hits), len(keywords)


def check_rag_noise(answer, unwanted):
    """检查 answer 是否包含不应出现的关键词"""
    if not answer or not unwanted:
        return []
    return [kw for kw in unwanted if kw in answer]


def main():
    # 加载测试集
    testset_path = "src/main/resources/testset.json"
    cases = load_testset(testset_path)
    print(f"加载测试集: {len(cases)} 条用例\n")

    # 统计
    results = []
    route_ok, route_total = 0, 0
    rag_hit_cases, rag_total_cases = 0, 0
    rag_keyword_hit, rag_keyword_total = 0, 0
    confusion = defaultdict(lambda: defaultdict(int))
    expected_routes, actual_routes = set(), set()

    for idx, c in enumerate(cases):
        cid = c["case_id"] or f"TC-{idx+1:03d}"
        q = c["query"]
        exp_route = c["expected_route"]
        label = c["label"]
        sheet = c["sheet"]
        gold = c["gold_answer"]

        print(f"[{idx+1}/{len(cases)}] {cid} [{label}]", flush=True)

        resp, err = call_agent(q)
        if err:
            print(f"  FAIL: {err}\n", flush=True)
            continue

        state = resp["state"]
        answer = resp["answer"]
        route_decision = resp.get("route_decision", {})
        # 优先从 semantic_route 节点取路由，兜底用最终 state
        actual_route = route_decision.get("route") or state.get("route", "?")
        confidence = route_decision.get("confidence") or state.get("confidence")

        # === 路由评估 ===
        route_match = (actual_route == exp_route)
        if exp_route:
            expected_routes.add(exp_route)
            actual_routes.add(actual_route)
            confusion[exp_route][actual_route] += 1
            if route_match:
                route_ok += 1
            route_total += 1

        route_sym = "OK" if route_match else "XX"
        print(f"  路由: 期望={exp_route} 实际={actual_route} conf={confidence} {route_sym}", flush=True)

        # === RAG 评估 ===
        # 从黄金标准答案提取关键词（传入 query 辅助）
        rag_keywords = extract_rag_keywords(gold, q)
        if answer and rag_keywords:
            hits, h, t = check_rag_hit(answer, rag_keywords)
            rag_keyword_hit += h
            rag_keyword_total += t
            if h == t:
                rag_hit_cases += 1
            rag_total_cases += 1
            hit_pct = f"{h}/{t}"
            if hits:
                print(f"  RAG命中: {hits} ({hit_pct})", flush=True)
            else:
                print(f"  RAG未命中: 期望{rag_keywords}", flush=True)
        else:
            hits = []
            print(f"  RAG跳过", flush=True)

        results.append({
            "case_id": cid, "sheet": sheet, "query": q, "label": label,
            "expected_route": exp_route, "actual_route": actual_route,
            "route_match": route_match, "confidence": confidence,
            "rag_keywords": rag_keywords, "rag_hits": hits,
        })

        time.sleep(0.15)

    # ===== 汇总 =====
    print("\n" + "=" * 70)
    print("                     评估报告")
    print("=" * 70)

    route_acc = route_ok / route_total * 100 if route_total else 0
    rag_case_acc = rag_hit_cases / rag_total_cases * 100 if rag_total_cases else 0
    rag_recall = rag_keyword_hit / rag_keyword_total * 100 if rag_keyword_total else 0

    # 宽松路由
    loose_ok = sum(1 for r in results if r["route_match"] or
        (r["expected_route"] == "faq" and r["actual_route"] == "knowledge_base") or
        (r["expected_route"] == "knowledge_base" and r["actual_route"] == "faq"))
    loose_acc = loose_ok / route_total * 100 if route_total else 0

    print(f"\n  总用例:             {len(cases)}")
    print(f"  路由准确率(严格):   {route_ok:3d}/{route_total:3d} = {route_acc:5.1f}%")
    print(f"  路由准确率(宽松):   {loose_ok:3d}/{route_total:3d} = {loose_acc:5.1f}%  (faq↔knowledge_base互通)")
    print(f"  RAG命中率(全词命中):{rag_hit_cases:3d}/{rag_total_cases:3d} = {rag_case_acc:5.1f}%")
    print(f"  RAG召回率(关键词):  {rag_keyword_hit:3d}/{rag_keyword_total:3d} = {rag_recall:5.1f}%")

    # 各路由准确率
    print(f"\n--- 各路由准确率 ---")
    route_stats = defaultdict(lambda: {"total": 0, "ok": 0})
    for r in results:
        e = r["expected_route"]
        if e:
            route_stats[e]["total"] += 1
            if r["route_match"]:
                route_stats[e]["ok"] += 1
    for rt in ["faq", "knowledge_base", "business_tool", "clarify", "human_handoff"]:
        if rt in route_stats:
            s = route_stats[rt]
            a = s["ok"] / s["total"] * 100 if s["total"] else 0
            bar = "█" * int(a / 10) + "░" * (10 - int(a / 10))
            print(f"  {rt:20s}  {s['ok']:2d}/{s['total']:2d}  {bar}  {a:5.1f}%")

    # 混淆矩阵
    print(f"\n--- 混淆矩阵 (期望 -> 实际) ---")
    all_routes = sorted(expected_routes | actual_routes)
    header = f"{'期望\\实际':<16}" + "".join(f"{r:<14}" for r in all_routes)
    print(header)
    print("-" * len(header))
    for er in sorted(expected_routes):
        row = f"{er:<16}"
        for ar in all_routes:
            row += f"{confusion[er].get(ar, 0):<14}"
        print(row)

    # 各维度准确率
    print(f"\n--- 各维度路由准确率 ---")
    dims = defaultdict(lambda: {"total": 0, "ok": 0})
    for r in results:
        dims[r["sheet"]]["total"] += 1
        if r["route_match"]:
            dims[r["sheet"]]["ok"] += 1
    for dim, s in sorted(dims.items()):
        a = s["ok"] / s["total"] * 100 if s["total"] else 0
        print(f"  {dim:30s}  {s['ok']:2d}/{s['total']:2d}  {a:5.1f}%")

    # 保存
    report = {
        "测试时间": time.strftime("%Y-%m-%d %H:%M:%S"),
        "总用例数": len(cases),
        "路由准确率(严格)": f"{route_acc:.1f}%",
        "路由准确率(宽松)": f"{loose_acc:.1f}%",
        "RAG命中率(全词命中)": f"{rag_case_acc:.1f}%",
        "RAG召回率(关键词)": f"{rag_recall:.1f}%",
        "各路由准确率": {rt: {"正确": s["ok"], "总数": s["total"],
                              "准确率": f"{s['ok']/s['total']*100:.1f}%" if s['total'] else "N/A"}
                          for rt, s in sorted(route_stats.items())},
        "混淆矩阵": {er: {ar: confusion[er][ar] for ar in all_routes} for er in sorted(expected_routes)},
        "详细结果": results,
    }
    out_path = "eval_report_full.json"
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)
    print(f"\n报告已保存: {out_path}")


if __name__ == "__main__":
    main()