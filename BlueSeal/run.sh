#!/bin/bash

if [ $# -lt 2 ];then
  echo "[BlueSeal]:too few argument"
  echo "[BlueSeal]-usage: ./run.sh [-d dir-path] [-f apk-path] [-l apklist-file-paht -D apks directory path]"
  exit 1
fi

dflag=false
fflag=true
listflag=false
dir=
file=
list=
listdir=.
#[-d]: directory to process, followed by the dir path
#[-f]: process a single file, followed by the file path
#[-l]: process a list of files,followed by the list file
while getopts d:f:l:D: opt; do
  case $opt in 
    d) echo "-d triggered!"
       dflag=true
       fflag=false
       dir=$OPTARG
      ;;
    f) echo "-f triggered!"
      fflag=true
      file=$OPTARG
      ;;
    l) echo "-l triggered!"
      listflag=true
      fflag=false
      list=$OPTARG
      ;;
    D) echo "list apks directory"
      if [ "$listflag" = flase ];then
        echo "eable [-l] first!"
        exit 1
      fi
      listdir=$OPTARG
      ;;
    ?) echo "invalid option: -$OPTARG"
       exit 1
      ;;
    :) echo "option -$OPTARG requires an argument."
      exit 1
      ;;
  esac
done

echo "Analysing:$2"

if [ -d "BlueSealOutput" ]; then
  echo "output folder exists!"
else
  mkdir BlueSealOutput
fi

#path=$1/$2
#apk=$2



#process the whole directory
if [ "$dflag" = true ];then
  for apk in $dir/*
  do
    apkname=$(basename $apk ".apk")
    ant -Darg0=$apk runTimeOut >> ./BlueSealOutput/$apkname.txt
  done
fi


#process a single apk file
if [ "$fflag" = true ];then
  apkname=$(basename $file ".apk")
  ant -Darg0=$file runTimeOut >> ./BlueSealOutput/$apkname.txt
fi


#process a list of apks from file list
if [ "$listflag" = true ];then
  while read line
  do
    echo "$line"
    apk=$listdir/$line
    apkname=$(basename $line ".apk")
    ant -Darg0=$apk runTimeOut >> ./BlueSealOutput/$apkname.txt
  done<$list
fi
