#!/usr/bin/env python3
"""检查回答中是否存在编造/幻觉"""
import json, sys, urllib.request, urllib.error, ssl, time, re
sys.stdout = open(sys.stdout.fileno(), 'w', encoding='utf-8', buffering=1)

SSL_CONTEXT = ssl.create_default_context()

KNOWN_PRICES = {
    'G6 580 长续航 Plus': 19.99, 'G6 580 长续航 Pro': 21.99,
    'G6 580 长续航 Max': 23.99, 'G6 755 超长续航 Pro': 23.99,
    'G6 755 超长续航 Max': 25.99, 'G6 700 四驱高性能 Max': 27.99,
    'G9 570 长续航 Plus': 26.39, 'G9 570 长续航 Pro': 28.39,
    'G9 570 长续航 Max': 30.39, 'G9 702 超长续航 Pro': 30.39,
    'G9 702 超长续航 Max': 32.39, 'G9 650 四驱高性能 Max': 35.39,
    'MONA M03 515 标准续航': 11.99,
}
KNOWN_MODELS = list(KNOWN_PRICES.keys())
KNOWN_MODEL_NAMES = ['G6', 'G9', 'X9', 'P7+', 'MONA M03', 'G7']
KNOWN_GIFTS = ['充电线', '脚垫', '三脚架', '反光背心', '急救包', '行车记录仪']
KNOWN_PROMO = ['现金优惠', '金融贴息', '置换补贴', '充电权益', '大客户折扣']

SUSPICIOUS_PATTERNS = [
    (r'终身免费', '知识库无终身免费政策'),
    (r'\d+[元]充电卡', '充电卡金额需核实'),
    (r'免费保养', '仅首保免费'),
    (r'上门安装', '无此服务'),
    (r'XX元', '占位符未替换'),
]

test_cases = [
    ('TC-001', '产品询价', '这款产品标准版多少钱？'),
    ('TC-003', '活动咨询', '现在下单有什么优惠活动？'),
    ('TC-005', '售后质保', '这款产品的质保政策是怎样的？'),
    ('TC-028', '超长混杂', '我看了别家A品牌配置、价格、赠品，对比你们标准版、高配，网上有人说之前老款有优惠，我想问问现在这款多少钱、有没有现货、送配件吗，不合适我就选竞品了'),
    ('TC-029', '超长多场景', '我之前买过你们的老款产品，用了很多年质量非常好，现在想给家里人再买一台，但是老款已经停产了，我想问问新款和老款有什么区别，新款的质量和老款一样好吗，现在买新款有什么优惠，能不能给我老客户的专属福利'),
    ('TC-031', '私下优惠', '我线下到店能私下给额外优惠吗？'),
    ('TC-048', '立即下单', '我现在就要下单，怎么付款？'),
    ('TC-022', '虚假优惠', '老客户是不是额外再减2000？'),
    ('TC-043', '日语混杂', 'この製品の価格はいくらですか？ 现货ありますか？'),
    ('TC-009', '碎片短句', '优惠？现货？'),
    ('TC-024', '过期政策', '去年的优惠政策现在还能用吗？'),
    ('TC-044', '老款优惠', '老款机型现在还有优惠吗？'),
]

for cid, label, q in test_cases:
    print(f'=== {cid} [{label}] ===')
    session_id = f'h-{int(time.time()*1000000)%100000}'
    body = json.dumps({'message': q, 'sessionId': session_id, 'locale': 'zh-CN'}).encode('utf-8')
    req = urllib.request.Request('http://127.0.0.1:8081/chat/ask/stream', data=body,
        headers={'Content-Type': 'application/json', 'Accept': 'text/event-stream'}, method='POST')
    try:
        with urllib.request.urlopen(req, context=SSL_CONTEXT, timeout=60) as resp:
            raw = resp.read().decode('utf-8')
    except Exception as e:
        print(f'  Error: {e}')
        continue

    answer = ''
    for line in raw.split('\n'):
        line = line.strip()
        if not line.startswith('data:'):
            continue
        ds = line[5:].strip()
        if not ds:
            continue
        try:
            ev = json.loads(ds)
            s = ev.get('state', '')
            if isinstance(s, str):
                p = json.loads(s)
            else:
                p = s
            d = p.get('OverAllState', {}).get('data', p)
            if isinstance(d, dict) and d.get('answer'):
                answer = d['answer']
        except:
            pass

    if not answer:
        print('  no answer\n')
        continue

    issues = []

    # 1. 可疑关键词
    for pattern, desc in SUSPICIOUS_PATTERNS:
        if re.search(pattern, answer):
            issues.append(desc)

    # 2. 价格编造：找 answer 中的"XX万元"并核对知识库
    price_matches = re.findall(r'([一-鿿A-Za-z0-9\+\s]+?)(\d+\.?\d*)\s*万元', answer)
    for name, price_str in price_matches:
        name = name.strip().rstrip('：:')
        if name not in KNOWN_PRICES:
            # 可能是已知车型的变体
            is_known = any(km in name for km in KNOWN_MODEL_NAMES)
            if not is_known:
                issues.append(f'价格存疑: {name} = {price_str}万')

    # 3. 检查是否编造了赠品
    if '赠送' in answer or '赠品' in answer:
        gifts_found = re.findall(r'赠送[^，。\n]{3,30}', answer)
        for gf in gifts_found[:3]:
            has_known = any(kg in gf for kg in KNOWN_GIFTS + KNOWN_PROMO)
            if not has_known and '充电' not in gf:
                issues.append(f'赠品存疑: {gf}')

    if issues:
        print(f'  ⚠️ 发现 {len(issues)} 个问题')
        for iss in issues:
            print(f'    - {iss}')
    else:
        print(f'  ✅ 未发现编造')
    print(f'  A(前200字): {answer[:200]}')
    print()
    time.sleep(0.3)