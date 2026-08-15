import json, urllib.request, re, sys
sys.stdout.reconfigure(encoding='utf-8')

queries = [
    "小鹏G6的续航里程是多少？",
    "XPENG G9价格多少？",
    "怎么申请售后保修？",
    "这款车多少钱？",
    "有优惠吗？",
    "G6配置",
]

for query in queries:
    body = json.dumps({"message": query, "sessionId": "rag-test2"})
    req = urllib.request.Request("http://localhost:8081/chat/ask/stream",
        data=body.encode("utf-8"), headers={"Content-Type": "application/json"})
    resp = urllib.request.urlopen(req, timeout=180)
    raw = resp.read().decode("utf-8")

    answer = ""
    route = ""
    for line in raw.split("\n"):
        if not line.startswith("data:"):
            continue
        evt = json.loads(line[5:].strip())
        node = evt.get("node", "?")
        state_str = str(evt.get("state", ""))
        m_route = re.search(r'"route":"([^"]+)"', state_str)
        if m_route:
            route = m_route.group(1)
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
                    elif nc == '"' or nc == '\\':
                        buf.append(nc); i += 2; continue
                buf.append(c)
                i += 1
            answer = "".join(buf)

    has_rag = "【知识库检索结果】" in answer
    marker = "HIT" if has_rag else "MISS"
    print(f"[{marker}] {query}")
    print(f"  route={route}")
    preview = answer[:300].replace("\n", "\\n")
    print(f"  answer({len(answer)}c): {preview}")
    print()
