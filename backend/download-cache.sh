#!/bin/bash

# Download cache file if it doesn't exist
CACHE_FILE="/tmp/graph-cache.bin"
CACHE_URL="https://github.com/cjprice2/CelebrityShortestPathFinder/releases/download/v1.1.0/graph-cache.bin"

if [ ! -f "$CACHE_FILE" ]; then
    echo "Downloading graph cache..."
    # Use wget with resume capability and faster options
    wget -c -O "$CACHE_FILE" "$CACHE_URL" --timeout=30 --tries=3
    if [ $? -eq 0 ]; then
        echo "Cache downloaded successfully"
    else
        echo "Failed to download cache, will build from scratch"
    fi
else
    echo "Cache file already exists"
fi
