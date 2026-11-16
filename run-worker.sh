#!/bin/bash
# ./run-worker.sh [IP_MASTER] [--id=W1] [--threads=2] [--crash-after=N]
cd "$(dirname "$0")" && java -Dstdout.encoding=UTF-8 -cp out worker.WorkerMain "$@"
