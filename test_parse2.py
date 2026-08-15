import json, sys
sys.stdout.reconfigure(encoding='utf-8')

line = '{"id": "xp_faq_0", "source": "xp_faq.md", "chunk_index": 0, "content": "# FAQ手册\\n\\n## 购车流程"}'

print("=== 原始 JSONL 行 ===")
print(line)
print()

# 1. 标准 JSON 解析 —— 正确
correct = json.loads(line)
print("=== 正确解析 (Python json.loads / Java ObjectMapper) ===")
for k, v in correct.items():
    print(f"  {k} = {repr(v)[:80]}")
print()

# 2. 手写解析 —— Java parseJsonLine 的逻辑
print("=== 手写解析 (当前 Java 代码的 parseJsonLine) ===")
i = line.index('{')
end = line.rindex('}')
inner = line[i + 1:end]
print(f"  去掉花括号后: {inner}")
print()

pairs = inner.split('", "')
print(f"  按 '\", \"' 切割后 ({len(pairs)} 段):")
for idx, p in enumerate(pairs):
    print(f"    [{idx}] {p[:80]}")
print()

# 问题：chunk_index 的 value "0" 后面紧跟 ", "，然后 "content" 被错误合并
# 第2段变成: chunk_index": 0, "content": "# FAQ...  —— key变成了chunk_index，value变成了0, "content"...
print("  结果: content 字段丢失，text 为空")
