import json, urllib.request, re, sys
sys.stdout.reconfigure(encoding='utf-8')

body = json.dumps({"message": "小鹏G6价格", "sessionId": "rag-final"})
req = urllib.request.Request("http://localhost:8081/chat/ask/stream",
    data=body.encode("utf-8"), headers={"Content-Type": "application/json"})
resp = urllib.request.urlopen(req, timeout=180)
raw = resp.read().decode("utf-8")

for line in raw.split("\n"):
    if not line.startswith("data:"):
        continue
    evt = json.loads(line[5:].strip())
    node = evt.get("node", "?")
    state_str = str(evt.get("state", ""))
    m_route = re.search(r'"route":"([^"]+)"', state_str)
    route = m_route.group(1) if m_route else "?"

    # find answer
    m_ans = re.search(r'"answer":"', state_str)
    answer = ""
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

    print(f"[{node}] route={route}")
    if answer:
        print(f"  answer: {answer[:200]}")
    print()
