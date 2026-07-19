#!/bin/bash
pid=$(ps aux | grep redis-server | grep -v grep | awk '{print $2}')
if [ -n "$pid" ]; then
    kill -9 $pid
    echo "Killed Redis server with PID: $pid"
else
    echo "No Redis server found"
fi
