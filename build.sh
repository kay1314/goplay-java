#!/bin/bash

# Build script for GoPlay Java Client

echo "Building GoPlay Java Client..."
echo "==============================="

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo "Error: Maven is not installed. Please install Maven first."
    exit 1
fi

# Display Maven version
echo "Using Maven:"
mvn --version
echo ""

# Run Maven build
echo "Running Maven build..."
mvn clean install

if [ $? -eq 0 ]; then
    echo ""
    echo "Build successful!"
    echo "JAR file generated: target/goplay-java-client-0.1.0.jar"
else
    echo ""
    echo "Build failed!"
    exit 1
fi
