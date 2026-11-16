#!/bin/bash
# ./run-client.sh <IP_MASTER> <image|gen:1200x800> <sortie.png> <FILTRE> [param] [delayMs] [chunkRows]
cd "$(dirname "$0")" && java -Dstdout.encoding=UTF-8 -cp out client.ClientMain "$@"
