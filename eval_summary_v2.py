#!/usr/bin/env python3
"""
全量汇总评估 v2
- 路由: 从 route_eval_results.json（semantic_route 节点提取的准确路由）
- RAG: 从 eval_data_full.json（actual_answer 文本）
合并两个数据源，输出路由准确率 + RAG 命中率 + 召回率
"""
import json, re, sys
from collections import Counter, defaultdict

sys.stdout = open(sys.stdout.fileno(), 'w', encoding='utf-8', buffering=1)

# 加载数据
with open('route_eval_results.json', 'r', encoding='utf-8') as f:
    route_data = json.load(f)  # 52条, 有路由

with open('eval_data_full.json', 'r', encoding='utf-8') as f:
    answer_data = json.load(f)  # 52条, 有 answer

# 按 query 对齐（去掉多余空白）
answer_map = {}
for d in answer_data:
    q = d.get('query', '').strip().replace('\n', ' ').replace('\r', '')
    # 归一化多个空格
    q = ' '.join(q.split())
    answer_map[q] = d.get('actual_answer', '')

# 统计
route_ok, route_total = 0, 0
loose_ok = 0
confusion = {}
expected_routes, actual_routes = set(), set()

rag_total = 0
rag_rich = 0
rag_info_sum = 0

for rd in route_data:
    expected = rd.get('expected', '')
    actual = rd.get('actual', '?')
    q = rd.get('query', '').strip().replace('\n', ' ').replace('\r', '')
    q = ' '.join(q.split())
    answer = answer_map.get(q, '')

    if not expected:
        continue

    expected_routes.add(expected)
    actual_routes.add(actual)
    confusion[(expected, actual)] = confusion.get((expected, actual), 0) + 1

    route_total += 1
    if expected == actual:
        route_ok += 1
    if expected == actual or \
       (expected == 'faq' and actual == 'knowledge_base') or \
       (expected == 'knowledge_base' and actual == 'faq'):
        loose_ok += 1

    # RAG 6 类信息检测
    has_price = any(kw in answer for kw in ['万', '元', '售价', '费用', '价格'])
    has_param = any(kw in answer for kw in ['公里', 'km', '续航', '电池', '尺寸', '功率'])
    has_policy = any(kw in answer for kw in ['质保', '保修', '保养', '售后', '维修'])
    has_action = any(kw in answer for kw in ['下单', '购买', '选购', '推荐', '选择'])
    has_stock = any(kw in answer for kw in ['现货', '发货', '交付', '提车', '送达', '库存'])
    has_compare = any(kw in answer for kw in ['对比', '区别', '优势', '升级', '提升', '新增'])
    cats = [has_price, has_param, has_policy, has_action, has_stock, has_compare]
    n = sum(1 for c in cats if c)

    rag_total += 1
    rag_info_sum += n
    if n >= 2:
        rag_rich += 1

# ===== 输出 =====
print('=' * 70)
print('                  RAG 系统全量评估报告')
print('=' * 70)

print(f'\n  总用例: {len(route_data)}')
print(f'  数据来源: 路由=route_eval_results.json | RAG=eval_data_full.json')

print(f'\n【路由准确率】')
print(f'  严格匹配: {route_ok:3d}/{route_total:3d} = {route_ok/route_total*100:5.1f}%')
print(f'  宽松匹配: {loose_ok:3d}/{route_total:3d} = {loose_ok/route_total*100:5.1f}%  (faq<->knowledge_base互通)')

print(f'\n【RAG 信息命中率】')
print(f'  定义: 回答包含 >=2 类业务信息(价格/参数/政策/引导/库存/对比)')
print(f'  命中: {rag_rich:3d}/{rag_total:3d} = {rag_rich/rag_total*100:5.1f}%')

print(f'\n【RAG 信息召回率】')
rag_recall = rag_info_sum / (rag_total * 6) * 100 if rag_total else 0
print(f'  定义: 6类业务信息的平均覆盖比例')
print(f'  召回: {rag_info_sum}/{rag_total*6} = {rag_recall:.1f}%')
print(f'  平均每case覆盖信息类数: {rag_info_sum/rag_total:.1f}/6')

# 各类信息单独统计
print(f'\n【各类业务信息命中率】')
info_names = {
    '价格/金额': '万 元 售价 费用 价格',
    '参数/规格': '公里 续航 电池 尺寸 功率',
    '政策/售后': '质保 保修 保养 售后 维修',
    '引导/行动': '下单 购买 选购 推荐 选择',
    '库存/物流': '现货 发货 交付 提车 送达',
    '对比/差异化': '对比 区别 优势 升级 提升 新增'
}
info_keywords = {
    '价格/金额': ['万', '元', '售价', '费用', '价格'],
    '参数/规格': ['公里', 'km', '续航', '电池', '尺寸', '功率'],
    '政策/售后': ['质保', '保修', '保养', '售后', '维修'],
    '引导/行动': ['下单', '购买', '选购', '推荐', '选择'],
    '库存/物流': ['现货', '发货', '交付', '提车', '送达', '库存'],
    '对比/差异化': ['对比', '区别', '优势', '升级', '提升', '新增']
}
for name, kws in info_keywords.items():
    hit = 0
    for rd in route_data:
        q = rd.get('query', '').strip().replace('\n', ' ').replace('\r', '')
        q = ' '.join(q.split())
        answer = answer_map.get(q, '')
        if any(kw in answer for kw in kws):
            hit += 1
    print(f'  {name:15s} {hit:3d}/{rag_total:3d} = {hit/rag_total*100:5.1f}%')

print(f'\n【各路由准确率】')
rs = defaultdict(lambda: {'total': 0, 'ok': 0, 'loose': 0})
for rd in route_data:
    expected = rd.get('expected', '')
    actual = rd.get('actual', '?')
    if not expected:
        continue
    rs[expected]['total'] += 1
    if expected == actual:
        rs[expected]['ok'] += 1
    if expected == actual or (expected == 'faq' and actual == 'knowledge_base') or \
       (expected == 'knowledge_base' and actual == 'faq'):
        rs[expected]['loose'] += 1

for r in ['faq', 'knowledge_base', 'business_tool', 'clarify', 'human_handoff']:
    if r in rs:
        s = rs[r]
        print(f'  {r:20s}  严格 {s["ok"]:2d}/{s["total"]:2d} ({s["ok"]/s["total"]*100:5.1f}%)  '
              f'宽松 {s["loose"]:2d}/{s["total"]:2d} ({s["loose"]/s["total"]*100:5.1f}%)')

print(f'\n【混淆矩阵 (期望 -> 实际)】')
all_routes = sorted(expected_routes | actual_routes)
header = f'{"期望\\实际":<16}' + ''.join(f'{r:<14}' for r in all_routes)
print(header)
print('-' * len(header))
for er in sorted(expected_routes):
    row = f'{er:<16}'
    for ar in all_routes:
        row += f'{confusion.get((er, ar), 0):<14}'
    print(row)