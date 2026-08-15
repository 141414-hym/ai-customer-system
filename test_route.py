import json, urllib.request, re, sys
sys.stdout.reconfigure(encoding='utf-8')

# 看看 "G6" 这种明确知识库关键词到底路由到哪
for query in ["小鹏G6价格", "G6", "价格"]:
    body = json.dumps({"message": query, "sessionId": "route-debug"})
    req = urllib.request.Request("http://localhost:8081/chat/ask/stream",
        data=body.encode("utf-8"), headers={"Content-Type": "application/json"})
    resp = urllib.request.urlopen(req, timeout=180)
    raw = resp.read().decode("utf-8")

    chain = []
    answer = ""
    for line in raw.split("\n"):
        if not line.startswith("data:"):
            continue
        evt = json.loads(line[5:].strip())
        node = evt.get("node", "?")
        state_str = str(evt.get("state", ""))
        m_route = re.search(r'"route":"([^"]+)"', state_str)
        route = m_route.group(1) if m_route else "?"
        chain.append(f"{node}")
        # 看 finalScores
        m_scores = re.search(r'"finalScores":\{([^}]+)\}', state_str)
        scores = m_scores.group(1) if m_scores else ""
        m_ans = re.search(r'"answer":"', state_str)
        if m_ans:
            start = m_ans.end()
            tail = state_str[start:]
            buf = []
            i = 0
            while i < len(tail):
                c = tail[i]
                if c == '"': break
                if c == "\\" and i + 1 < len(tail):
                    nc = tail[i+1]
                    if nc == "n": buf.append("\n"); i+=2; continue
                    elif nc in ('"','\\'): buf.append(nc); i+=2; continue
                buf.append(c); i+=1
            answer = "".join(buf)
        if scores:
            print(f"[{node}] route={route}  scores={{{scores}}}")

    has_rag = "【知识库检索结果】" in answer
    print(f"Q='{query}'  chain={' -> '.join(chain)}  RAG={'HIT' if has_rag else 'MISS'}")
    print(f"  answer: {answer[:200]}")
    print()
