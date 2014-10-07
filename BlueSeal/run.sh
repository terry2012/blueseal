#!/bin/bash

if [ $# -lt 2 ];then
  echo "[BlueSeal]:too few argument"
  echo "[BlueSeal]: please provide path to apk and apk name"
  exit 1
fi

echo "Analysing:$2"

if [ -d "BlueSealOutput" ]; then
  echo "output folder exists!"
else
  mkdir BlueSealOutput
fi

path=$1/$2
apk=$2

ant -Darg0=$path runTimeOut >> ./BlueSealOutput/$apk.txt
