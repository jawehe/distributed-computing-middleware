#!/bin/bash
# ./run-master.sh [--host=IP_DU_MASTER] [--console]
cd "$(dirname "$0")" && java -Dstdout.encoding=UTF-8 -cp out master.MasterMain "$@"
