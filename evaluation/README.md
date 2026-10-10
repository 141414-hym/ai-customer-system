# 评测资产

本目录集中存放项目的离线评测与压测材料，避免根目录混入一次性脚本和历史输出。

```text
evaluation/
  data/          固定评测输入集
  scripts/       路由、RAG、回答质量评测脚本
  performance/   压测脚本
```

运行 Python 脚本前，请在项目根目录启动服务，并从项目根目录执行脚本，以便它们读取知识库和配置。首次运行前执行 `mkdir -p evaluation/reports`。评测生成的报告属于运行产物，不提交到仓库。
