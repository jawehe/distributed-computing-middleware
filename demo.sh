#!/bin/bash
# Démo automatique en local : 1 Master, 3 Workers (W3 tombe en panne), 1 client.
cd "$(dirname "$0")"
./build.sh || exit 1
mkdir -p logs
OPTS="-Dhb.interval=1000 -Dhb.timeout=4000"   # heartbeat accéléré pour la démo (par défaut : 3 s / 10 s)
java -Dstdout.encoding=UTF-8 $OPTS -cp out master.MasterMain --console > logs/master.log 2>&1 &  MP=$!
sleep 2
java -Dstdout.encoding=UTF-8 $OPTS -cp out worker.WorkerMain --id=W1 --threads=2 > logs/w1.log 2>&1 & P1=$!
java -Dstdout.encoding=UTF-8 $OPTS -cp out worker.WorkerMain --id=W2 --threads=2 > logs/w2.log 2>&1 & P2=$!
java -Dstdout.encoding=UTF-8 $OPTS -cp out worker.WorkerMain --id=W3 --threads=2 --crash-after=2 > logs/w3.log 2>&1 & P3=$!
sleep 2
java -Dstdout.encoding=UTF-8 -cp out client.ClientMain localhost gen:1200x800 logs/result.png BRIGHTNESS 60 700 50
sleep 5
kill $MP $P1 $P2 $P3 2>/dev/null
echo; echo "=== Journal du Master (extraits) ==="
grep -E "REGISTER|JOB|FAILED|REASSIGN|RECOVERED|ABORT" logs/master.log
echo; echo "Tâches terminées par Worker :"; grep -c "DONE .* W1" logs/master.log | sed 's/^/  W1: /'; grep -c "DONE .* W2" logs/master.log | sed 's/^/  W2: /'; grep -c "DONE .* W3" logs/master.log | sed 's/^/  W3: /'
