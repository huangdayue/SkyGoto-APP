#!/bin/bash
# SkyGoto Android App 一键构建脚本

set -e

ANDROID_PROJECT_DIR="$(cd "$(dirname "$0")/android" && pwd)"
cd "$ANDROID_PROJECT_DIR"

echo "=========================================="
echo "  SkyGoto Android 构建脚本"
echo "=========================================="
echo ""

# 检查 Java
if ! command -v java &> /dev/null; then
    echo "❌ 错误: 未找到 Java"
    echo "请安装 JDK 17: https://adoptium.net/"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -1 | cut -d'"' -f2)
echo "✓ Java 版本: $JAVA_VERSION"

# 检查 Gradle
if command -v gradle &> /dev/null; then
    GRADLE_VERSION=$(gradle --version | head -1 | cut -d' ' -f2)
    echo "✓ Gradle 版本: $GRADLE_VERSION"
    echo ""
    echo "使用系统 Gradle 构建..."
    gradle assembleDebug
else
    echo "✓ 使用 Gradle Wrapper"
    echo ""
    if [ ! -f "gradlew" ]; then
        echo "❌ 错误: 未找到 gradlew"
        exit 1
    fi
    chmod +x gradlew
    ./gradlew assembleDebug
fi

echo ""
echo "=========================================="
if [ -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
    APK_SIZE=$(du -h app/build/outputs/apk/debug/app-debug.apk | cut -f1)
    echo "✓ 构建成功!"
    echo "APK: app/build/outputs/apk/debug/app-debug.apk"
    echo "大小: $APK_SIZE"
else
    echo "❌ 构建失败"
    exit 1
fi
echo "=========================================="
