#!/usr/bin/env python3
"""
aiagent 批量评估脚本。

功能：
1. 从测试集 JSON/Excel 读取测试用例
2. 批量调用 aiagent 的 /chat/ask 接口
3. 评估路由准确率 + RAG 命中率 + 噪音率
4. 输出聚合报告

用法：
    python test_eval.py                  # 使用默认测试集（内置示例）
    python test_eval.py --file my_testset.json  # 使用自定义测试集
    python test_eval.py --url http://localhost:8081  # 自定义服务地址
"""

import json
import sys
import time
import argparse
import urllib.request
import urllib.error
import ssl
import re
from collections import Counter, defaultdict

# ========== 默认配置 ==========
DEFAULT_URL = "http://localhost:8081"
TIMEOUT_SEC = 60
SSL_CONTEXT = ssl.create_default_context()

# ========== 默认测试集（内置示例）==========
DEFAULT_TESTSET = {
    "测试集名称": "aiagent 内置评估集",
    "用例": [
        {
            "用例ID": "TC-001",
            "用户Query": "G6多少钱？",
            "期望路由": "knowledge_base",
            "分类标签": "产品询价",
            "期望包含关键词": ["G6", "19.99"],
            "期望不包含关键词": ["G9", "X9"],
            "期望命中文档ID": ["xp_pricing.md"]
        },
        {
            "用例ID": "TC-002",
            "用户Query": "贷款怎么申请",
            "期望路由": "knowledge_base",
            "分类标签": "贷款咨询",
            "期望包含关键词": ["贷款", "申请"],
            "期望不包含关键词": [],
            "期望命中文档ID": ["xp_payment.md", "xp_ordering.md"]
        },
        {
            "用例ID": "TC-003",
            "用户Query": "提车要多久",
            "期望路由": "knowledge_base",
            "分类标签": "交付咨询",
            "期望包含关键词": ["交付", "周期", "周"],
            "期望不包含关键词": [],
            "期望命中文档ID": ["xp_delivery.md", "xp_promotions.md"]
        },
        {
            "用例ID": "TC-004",
            "用户Query": "订金能退吗",
            "期望路由": "knowledge_base",
            "分类标签": "退订咨询",
            "期望包含关键词": ["退订", "订金"],
            "期望不包含关键词": ["保险"],
            "期望命中文档ID": ["xp_cancellation.md", "xp_faq.md"]
        },
        {
            "用例ID": "TC-005",
            "用户Query": "G6的优惠活动有哪些",
            "期望路由": "knowledge_base",
            "分类标签": "活动咨询",
            "期望包含关键词": ["G6", "优惠", "限时"],
            "期望不包含关键词": ["G9", "X9"],
            "期望命中文档ID": ["xp_promotions.md", "xp_gifts.md"]
        },
        {
            "用例ID": "TC-006",
            "用户Query": "我要投诉",
            "期望路由": "human_handoff",
            "分类标签": "投诉",
            "期望包含关键词": [],
            "期望不包含关键词": [],
            "期望命中文档ID": []
        },
        {
            "用例ID": "TC-007",
            "用户Query": "你好",
            "期望路由": "clarify",
            "分类标签": "寒暄",
            "期望包含关键词": [],
            "期望不包含关键词": [],
            "期望命中文档ID": []
        },
        {
            "用例ID": "TC-008",
            "用户Query": "G6多少钱?贷款怎么申请?提车要多久?订金能退吗",
            "期望路由": "knowledge_base",
            "分类标签": "多意图长句",
            "期望包含关键词": ["G6", "贷款", "提车", "订金"],
            "期望不包含关键词": [],
            "期望命中文档ID": ["xp_pricing.md", "xp_payment.md", "xp_delivery.md", "xp_cancellation.md"]
        },
        {
            "用例ID": "TC-009",
            "用户Query": "有优慧吗?现huo多久发？",
            "期望路由": "knowledge_base",
            "分类标签": "错别字+拼音",
            "期望包含关键词": ["优惠", "现货"],
            "期望不包含关键词": [],
            "期望命中文档ID": ["xp_promotions.md", "xp_delivery.md"]
        },
        {
            "用例ID": "TC-010",
            "用户Query": "查一下我的订单ORD123456",
            "期望路由": "business_tool",
            "分类标签": "订单查询",
            "期望包含关键词": [],
            "期望不包含关键词": [],
            "期望命中文档ID": []
        },
    ]
}


def load_testset(filepath):
    """从 JSON 文件加载测试集"""
    with open(filepath, "r", encoding="utf-8") as f:
        return json.load(f)


def save_report(report, filepath):
    """保存评估报告"""
    with open(filepath, "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)
    print(f"\n评估报告已保存: {filepath}")


def call_agent(query, base_url, session_id=None):
    """调用 aiagent SSE 接口 /chat/ask/stream"""
    if session_id is None:
        session_id = "eval-" + str(int(time.time() * 1000000) % 10000000)

    url = base_url.rstrip("/") + "/chat/ask/stream"
    body = json.dumps({
        "message": query,
        "sessionId": session_id,
        "locale": "zh-CN"
    }).encode("utf-8")

    req = urllib.request.Request(
        url,
        data=body,
        headers={
            "Content-Type": "application/json",
            "Accept": "text/event-stream"
        },
        method="POST"
    )

    try:
        with urllib.request.urlopen(req, context=SSL_CONTEXT, timeout=TIMEOUT_SEC) as resp:
            raw = resp.read().decode("utf-8")
            events = parse_sse_events(raw)
            # 从所有事件中提取状态，最终状态会在最后一个事件中
            final_state = extract_final_state(events)
            return final_state, None
    except urllib.error.HTTPError as e:
        return None, f"HTTP {e.code}"
    except urllib.error.URLError as e:
        return None, f"连接失败: {e.reason}"
    except Exception as e:
        return None, f"异常: {e}"


def parse_sse_events(raw):
    """解析 SSE 数据流为事件列表"""
    events = []
    for line in raw.split("\n"):
        line = line.strip()
        if line.startswith("data:"):
            data_str = line[5:].strip()
            if data_str:
                try:
                    events.append(json.loads(data_str))
                except json.JSONDecodeError:
                    pass
    return events


def extract_final_state(events):
    """从所有 SSE 事件中提取最终状态"""
    state = {}
    for event in events:
        raw_state = event.get("state")
        if not raw_state:
            continue
        try:
            if isinstance(raw_state, str):
                parsed = json.loads(raw_state)
            else:
                parsed = raw_state
            # 处理 OverAllState 嵌套
            overall = parsed.get("OverAllState", {})
            data = overall.get("data", parsed)
            if isinstance(data, dict):
                state.update(data)
        except (json.JSONDecodeError, TypeError):
            pass
    return state


def extract_route_and_rag(state):
    """从最终状态中提取路由信息和 RAG 结果"""
    if not state:
        return None, None, None

    route = state.get("route")
    confidence = state.get("confidence")
    rag_content = state.get("answer")
    # 从 semantic_route 节点的结果中取路由（如有，用 final_decision 中的 route）
    trace = state.get("trace", {})
    if trace and isinstance(trace, dict):
        if not route or route == "ai_reply":
            route = trace.get("selectedRoute") or trace.get("modelRoute")

    return route, confidence, rag_content


def check_rag_hit(rag_content, expected_keywords):
    """检查 RAG 检索结果是否包含期望关键词"""
    if not rag_content or not expected_keywords:
        return None
    hits = []
    for kw in expected_keywords:
        if kw in rag_content:
            hits.append(kw)
    return hits


def check_rag_noise(rag_content, unexpected_keywords):
    """检查 RAG 检索结果是否包含不应出现的关键词"""
    if not rag_content or not unexpected_keywords:
        return []
    noises = []
    for kw in unexpected_keywords:
        if kw in rag_content:
            noises.append(kw)
    return noises


def extract_hit_sources(rag_content):
    """从 RAG 检索结果中提取命中的文档来源列表"""
    if not rag_content:
        return []
    sources = set()
    for match in re.finditer(r'来源:\s*(\S+)', rag_content):
        sources.add(match.group(1))
    return list(sources)


def check_recall(hit_sources, expected_sources):
    """计算召回率：期望文档中被实际命中的比例"""
    if not expected_sources:
        return None, None
    hit_set = set(hit_sources)
    expected_set = set(expected_sources)
    matched = expected_set & hit_set
    recall = len(matched) / len(expected_set) * 100
    return list(matched), recall


def evaluate(testset, base_url, session_prefix="eval"):
    """对测试集执行全量评估"""
    cases = testset.get("用例", [])
    total = len(cases)
    print(f"总用例数: {total}")
    print(f"{'='*60}")

    results = []
    route_ok = 0
    rag_hit_count = 0
    rag_noise_count = 0
    route_total = 0
    rag_total = 0

    # 混淆矩阵
    confusion = defaultdict(lambda: defaultdict(int))
    expected_routes = set()
    actual_routes = set()

    for idx, case in enumerate(cases):
        case_id = case.get("用例ID", f"TC-{idx+1:03d}")
        query = case.get("用户Query", "")
        expected_route = case.get("期望路由", "")
        expected_kws = case.get("期望包含关键词", [])
        unexpected_kws = case.get("期望不包含关键词", [])
        expected_sources = case.get("期望命中文档ID", [])
        label = case.get("分类标签", "")

        print(f"\n[{idx+1}/{total}] {case_id} [{label}]")
        print(f"  Query: {query}")
        print(f"  期望路由: {expected_route}")

        session_id = f"{session_prefix}-{case_id}-{int(time.time() * 1000000) % 100000}"
        response, err = call_agent(query, base_url, session_id)

        if err:
            print(f"  [FAIL] 请求失败: {err}")
            results.append({
                "用例ID": case_id,
                "Query": query,
                "期望路由": expected_route,
                "实际路由": None,
                "路由匹配": False,
                "错误": err,
                "RAG命中": None,
                "RAG噪音": None
            })
            continue

        route, confidence, rag_content = extract_route_and_rag(response)
        print(f"  实际路由: {route} (confidence={confidence})")

        # 路由评估
        route_match = (route == expected_route)
        expected_routes.add(expected_route)
        if route:
            actual_routes.add(route)
            confusion[expected_route][route] += 1
        if route_match:
            route_ok += 1
        route_total += 1

        # RAG 命中评估
        rag_hit = check_rag_hit(rag_content, expected_kws) if expected_kws else None
        rag_noise = check_rag_noise(rag_content, unexpected_kws) if unexpected_kws else []

        if rag_hit is not None:
            rag_total += 1
            if len(rag_hit) > 0 and len(rag_hit) == len(expected_kws):
                rag_hit_count += 1

        if rag_noise:
            rag_noise_count += 1

        # 召回率评估
        hit_sources = extract_hit_sources(rag_content)
        matched_sources, recall = check_recall(hit_sources, expected_sources)

        print(f"  {'[OK]' if route_match else '[FAIL]'} 路由{'匹配' if route_match else '不匹配'}")
        if rag_hit is not None:
            print(f"  RAG命中: {rag_hit if rag_hit else '[FAIL] 未命中期望关键词'}")
        if rag_noise:
            print(f"  RAG噪音: [FAIL] 出现不应包含的关键词: {rag_noise}")
        if expected_sources:
            print(f"  命中文档: {hit_sources}")
            print(f"  期望文档: {expected_sources}")
            print(f"  召回率: {recall:.0f}% ({len(matched_sources)}/{len(expected_sources)})")

        result = {
            "用例ID": case_id,
            "期望路由": expected_route,
            "实际路由": route,
            "路由匹配": route_match,
            "RAG命中": rag_hit,
            "RAG噪音": rag_noise,
            "期望命中文档ID": expected_sources,
            "实际命中文档": hit_sources,
            "召回率": recall,
            "错误": None
        }
        results.append(result)

    # ========== 聚合统计 ==========
    print(f"\n{'='*60}")
    route_accuracy = route_ok / route_total * 100 if route_total > 0 else 0
    rag_accuracy = rag_hit_count / rag_total * 100 if rag_total > 0 else 0
    noise_rate = rag_noise_count / total * 100

    # 平均召回率
    recall_total = 0
    recall_count = 0
    for r in results:
        if r.get("召回率") is not None:
            recall_total += r["召回率"]
            recall_count += 1
    avg_recall = recall_total / recall_count if recall_count > 0 else 0

    print(f"\n=== 聚合统计 ===")
    print(f"{'='*40}")
    print(f"  路由准确率: {route_ok}/{route_total} = {route_accuracy:.1f}%")
    print(f"  RAG命中率:  {rag_hit_count}/{rag_total} = {rag_accuracy:.1f}%")
    print(f"  RAG噪音率:  {rag_noise_count}/{total} = {noise_rate:.1f}%")
    print(f"  RAG平均召回率: {avg_recall:.1f}% ({recall_count} cases)")

    # 混淆矩阵
    print(f"\n=== 混淆矩阵（期望路由 x 实际路由） ===")
    all_routes = sorted(expected_routes | actual_routes)
    header = f"{'期望\\实际':<16}" + "".join(f"{r:<14}" for r in all_routes)
    print(header)
    print("-" * len(header))
    for er in sorted(expected_routes):
        row = f"{er:<16}"
        for ar in all_routes:
            row += f"{confusion[er].get(ar, 0):<14}"
        print(row)

    report = {
        "测试集名称": testset.get("测试集名称", "未命名"),
        "测试时间": time.strftime("%Y-%m-%d %H:%M:%S"),
        "总用例数": total,
        "路由准确率": f"{route_accuracy:.1f}%",
        "RAG命中率": f"{rag_accuracy:.1f}%",
        "RAG噪音率": f"{noise_rate:.1f}%",
        "RAG平均召回率": f"{avg_recall:.1f}%",
        "召回率统计用例数": recall_count,
        "混淆矩阵": {
            f"{er}": {ar: confusion[er][ar] for ar in all_routes}
            for er in sorted(expected_routes)
        },
        "详细结果": results
    }

    return report


def main():
    parser = argparse.ArgumentParser(description="aiagent 批量评估工具")
    parser.add_argument("--file", "-f", help="测试集 JSON 文件路径")
    parser.add_argument("--url", "-u", default=DEFAULT_URL, help="aiagent 服务地址 (默认: http://localhost:8081)")
    parser.add_argument("--output", "-o", default="eval_report.json", help="评估报告输出路径")
    args = parser.parse_args()

    # 加载测试集
    if args.file:
        try:
            testset = load_testset(args.file)
            print(f"已加载测试集: {args.file}")
        except Exception as e:
            print(f"加载测试集失败: {e}")
            sys.exit(1)
    else:
        testset = DEFAULT_TESTSET
        print("使用内置默认测试集")

    print(f"测试集名称: {testset.get('测试集名称', '未命名')}")
    print(f"aiagent 地址: {args.url}")

    # 执行评估
    report = evaluate(testset, args.url)

    # 保存报告
    save_report(report, args.output)
    print(f"\n评估完成！")


if __name__ == "__main__":
    main()