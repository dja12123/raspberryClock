#!/bin/bash

for ((i=0;i<6;i++))
do
	ps -ef | grep raspberryClockProcess |grep -v grep > /dev/null
	if [ $? != 0 ]
	then
		echo "strat!"

		cd /root/raspberryClock && screen -dmS raspberryClockProcess java -Du=raspberryClockProcess -jar ./target/raspberryClock.jar "$@"
	fi
	
	sleep 10;
done
