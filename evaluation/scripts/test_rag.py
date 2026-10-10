import json, urllib.request, sys, re
sys.stdout.reconfigure(encoding='utf-8')

# 用最简单的知识库关键词测试
for query in ["小鹏", "G6", "G9", "续航", "保修", "售后", "价格", "XPENG"]:
    body = json.dumps({"message": query, "sessionId": "rag-mini"})
    req = urllib.request.Request("http://localhost:8081/chat/ask/stream",
        data=body.encode("utf-8"), headers={"Content-Type": "application/json"})
    resp = urllib.request.urlopen(req, timeout=180)
    raw = resp.read().decode("utf-8")

    answer_final = ""
    route_final = ""
    for line in raw.split("\n"):
        if not line.startswith("data:"):
            continue
        evt = json.loads(line[5:].strip())
        state_str = str(evt.get("state", ""))
        m_route = re.search(r'"route":"([^"]+)"', state_str)
        if m_route:
            route_final = m_route.group(1)
        m_ans = re.search(r'"answer":"', state_str)
        if m_ans:
            start = m_ans.end()
            tail = state_str[start:]
            buf = []
            i = 0
            while i < len(tail):
                c = tail[i]
                if c == '"':
                    break
                if c == "\\" and i + 1 < len(tail):
                    nc = tail[i + 1]
                    if nc == "n":
                        buf.append("\n"); i += 2; continue
                    elif nc in ('"', '\\'):
                        buf.append(nc); i += 2; continue
                buf.append(c)
                i += 1
            answer_final = "".join(buf)

    has_rag = "【知识库检索结果】" in answer_final
    marker = "HIT" if has_rag else "MISS"
    print(f"[{marker}] '{query}' -> {route_final}: {answer_final[:150]}")

