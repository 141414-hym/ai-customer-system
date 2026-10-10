import json, sys, re
sys.stdout.reconfigure(encoding='utf-8')

def parse_json_line(line):
    """复制 Java parseJsonLine 的逻辑"""
    i = line.index('{')
    end = line.rindex('}')
    inner = line[i + 1:end]

    pairs = inner.split('", "')
    result = {}
    for p_idx, pair in enumerate(pairs):
        if p_idx == 0 and pair.startswith('"'):
            pair = pair[1:]
        colon = pair.find('":')
        if colon < 0:
            continue
        key = pair[:colon]
        val = pair[colon + 2:].strip()
        if val.startswith('"'):
            val = val[1:]
            if val.endswith('"'):
                val = val[:-1]
            val = (val.replace('\\"', '"')
                      .replace('\\n', '\n')
                      .replace('\\r', '\r')
                      .replace('\\t', '\t')
                      .replace('\\\\', '\\'))
            result[key] = val
        else:
            try:
                result[key] = int(val)
            except ValueError:
                result[key] = val
    return result if result else None

# 用第一行测试
line = '{"id": "xp_faq_0", "source": "xp_faq.md", "chunk_index": 0, "content": "# FAQ常见问答手册\\n\\n> 以下为客服对话中高频出现的通用问题及标准回答。内容按主题分类，用于快速匹配检索。\\n\\n---\\n\\n## 购车流程"}'

result = parse_json_line(line)
print(f"Parsed: {result}")
print(f"Keys: {list(result.keys()) if result else 'None'}")
content = result.get('content', 'MISSING') if result else 'None'
print(f"content ({len(content)}c): {content[:150]}")
