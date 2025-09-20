#!/bin/bash

echo "=== Memory Monitoring Script ==="
echo "Press Ctrl+C to stop"
echo ""

# Function to get container stats
get_stats() {
    echo "=== $(date) ==="
    echo "Container Memory Usage:"
    docker stats --no-stream --format "table {{.Container}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}" | grep -E "(CONTAINER|celebrity)"
    echo ""
    
    # Get detailed memory info from inside container
    CONTAINER_ID=$(docker ps --filter "name=celebrity" --format "{{.ID}}" | head -1)
    if [ ! -z "$CONTAINER_ID" ]; then
        echo "Inside Container Memory:"
        docker exec $CONTAINER_ID sh -c "free -h && echo 'Java Process Memory:' && ps aux | grep java | grep -v grep"
        echo ""
    fi
}

# Initial stats
get_stats

# Monitor every 10 seconds
while true; do
    sleep 10
    get_stats
done
