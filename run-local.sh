#!/usr/bin/env bash

set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${project_dir}"

if [[ -f .env ]]; then
  set -a
  source .env
  set +a
fi

: "${AI_DASHSCOPE_API_KEY:?请先设置AI_DASHSCOPE_API_KEY，或复制.env.example为.env后填写}"

export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
export PATH="${JAVA_HOME}/bin:${PATH}"

exec ./mvnw spring-boot:run
