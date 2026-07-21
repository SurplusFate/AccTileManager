#!/usr/bin/env bash
# ============================================
# 无障碍磁贴管理器 - Termux ARM 构建脚本
# ============================================
# 使用方法:
#   pkg install openjdk-17 git unzip curl wget -y
#   pkg install aapt2 -y
#   bash build.sh
# ============================================

set -u

GREEN='\033[0;32m'
YELLOW='\033[0;33m'
RED='\033[0;31m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

BUILD_DIR="$SCRIPT_DIR/build"
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"/{gen,classes,libs,dex}

echo -e "${GREEN}=== 无障碍磁贴管理器 - Termux 构建 ===${NC}"
echo ""

# ---- 检查依赖 ----
echo -e "${YELLOW}[1/8] 检查依赖...${NC}"
for cmd in java javac aapt2 zipalign jarsigner keytool zip; do
    if ! command -v $cmd &> /dev/null; then
        echo -e "${RED}缺少 $cmd，请先安装依赖${NC}"
        echo "  pkg install openjdk-17 aapt2 -y"
        exit 1
    fi
done
echo -e "  ${GREEN}Java $(java -version 2>&1 | head -1)${NC}"
echo -e "  ${GREEN}aapt2 $(aapt2 version 2>&1 | head -1)${NC}"
echo ""

# ---- 下载 d8 (纯 Java, ARM 兼容) ----
echo -e "${YELLOW}[2/8] 下载 d8 工具...${NC}"
if [ ! -f "$BUILD_DIR/libs/d8.jar" ]; then
    # d8 在 build-tools 中，是纯 Java jar
    curl -L -o "$BUILD_DIR/build-tools.zip" \
        "https://dl.google.com/android/repository/build-tools_r34-linux.zip" 2>&1 | tail -1
    if [ -f "$BUILD_DIR/build-tools.zip" ] && [ -s "$BUILD_DIR/build-tools.zip" ]; then
        unzip -qo "$BUILD_DIR/build-tools.zip" -d "$BUILD_DIR/bt/" 2>&1
        # d8.jar 在 build-tools 里
        D8_JAR=$(find "$BUILD_DIR/bt" -name "d8.jar" 2>/dev/null | head -1)
        if [ -n "$D8_JAR" ]; then
            cp "$D8_JAR" "$BUILD_DIR/libs/d8.jar"
            echo -e "  ${GREEN}d8 下载成功${NC}"
        else
            echo -e "  ${YELLOW}d8.jar 未在压缩包中找到，尝试备用方案...${NC}"
            # 备用: 直接下载 d8.jar
            curl -L -o "$BUILD_DIR/libs/d8.jar" \
                "https://raw.githubusercontent.com/nicholasgasior/gmern/master/files/d8.jar" 2>/dev/null
        fi
    else
        echo -e "${RED}build-tools 下载失败${NC}"
        exit 1
    fi
fi

if [ ! -s "$BUILD_DIR/libs/d8.jar" ]; then
    echo -e "${RED}无法获取 d8.jar，构建终止${NC}"
    echo "请检查网络连接后重试"
    exit 1
fi

d8() {
    java -cp "$BUILD_DIR/libs/d8.jar" com.android.tools.r8.D8 "$@"
}

# ---- 下载 android.jar ----
echo -e "${YELLOW}[3/8] 下载 android.jar...${NC}"
if [ ! -f "$BUILD_DIR/libs/android.jar" ]; then
    # 从 Android SDK 下载 platform
    curl -L -o "$BUILD_DIR/platform.zip" \
        "https://dl.google.com/android/repository/platform-34_r03.zip" 2>&1 | tail -1
    if [ -f "$BUILD_DIR/platform.zip" ] && [ -s "$BUILD_DIR/platform.zip" ]; then
        unzip -qo "$BUILD_DIR/platform.zip" -d "$BUILD_DIR/" 2>&1
        cp "$BUILD_DIR/android-34/android.jar" "$BUILD_DIR/libs/android.jar"
        rm -rf "$BUILD_DIR/android-34"
        echo -e "  ${GREEN}android.jar 下载成功${NC}"
    else
        echo -e "${RED}android.jar 下载失败${NC}"
        exit 1
    fi
fi

# ---- 下载 Shizuku API ----
echo -e "${YELLOW}[4/8] 下载 Shizuku API...${NC}"
if [ ! -f "$BUILD_DIR/libs/shizuku-api.jar" ]; then
    curl -L -o "$BUILD_DIR/libs/shizuku-api.jar" \
        "https://jitpack.io/com.github.RikkaApps/Shizuku-API/13.1.5/Shizuku-API-13.1.5.jar" 2>&1 | tail -1
fi
if [ -s "$BUILD_DIR/libs/shizuku-api.jar" ]; then
    echo -e "  ${GREEN}Shizuku API 下载成功$(ls -lh "$BUILD_DIR/libs/shizuku-api.jar" | awk '{print " ("$5")"}')${NC}"
else
    echo -e "${RED}Shizuku API 下载失败${NC}"
    exit 1
fi

# ---- 编译资源 ----
echo -e "${YELLOW}[5/8] 编译资源...${NC}"
aapt2 compile --dir app/src/main/res -o "$BUILD_DIR/resources.zip" 2>&1
if [ $? -ne 0 ]; then echo -e "${RED}资源编译失败${NC}"; exit 1; fi

aapt2 link \
    -o "$BUILD_DIR/resources.apk" \
    -I "$BUILD_DIR/libs/android.jar" \
    --manifest app/src/main/AndroidManifest.xml \
    -R "$BUILD_DIR/resources.zip" \
    --auto-add-overlay \
    --java "$BUILD_DIR/gen" \
    --target-sdk-version 24 2>&1
if [ $? -ne 0 ]; then echo -e "${RED}资源链接失败${NC}"; exit 1; fi
echo -e "  ${GREEN}资源编译完成${NC}"

# ---- 编译 Java ----
echo -e "${YELLOW}[6/8] 编译 Java...${NC}"
find app/src/main/java -name "*.java" > "$BUILD_DIR/sources.txt"
find "$BUILD_DIR/gen" -name "*.java" >> "$BUILD_DIR/sources.txt"

CLASSPATH="$BUILD_DIR/libs/android.jar:$BUILD_DIR/libs/shizuku-api.jar"

javac \
    -source 11 -target 11 \
    -d "$BUILD_DIR/classes" \
    -classpath "$CLASSPATH" \
    @"$BUILD_DIR/sources.txt" 2>&1

if [ $? -ne 0 ]; then
    echo -e "${RED}Java 编译失败${NC}"
    exit 1
fi
echo -e "  ${GREEN}Java 编译完成 ($(find "$BUILD_DIR/classes" -name '*.class' | wc -l) 个 class 文件)${NC}"

# ---- 生成 DEX ----
echo -e "${YELLOW}[7/8] 生成 DEX...${NC}"
ALL_CLASS_FILES=$(find "$BUILD_DIR/classes" -name "*.class")

d8 --output "$BUILD_DIR/dex" \
    --lib "$BUILD_DIR/libs/android.jar" \
    $ALL_CLASS_FILES 2>&1

if [ $? -ne 0 ] || [ ! -f "$BUILD_DIR/dex/classes.dex" ]; then
    echo -e "${RED}DEX 生成失败${NC}"
    exit 1
fi
echo -e "  ${GREEN}DEX 生成完成${NC}"

# ---- 打包签名 ----
echo -e "${YELLOW}[8/8] 打包 & 签名...${NC}"

# 创建未签名 APK
cp "$BUILD_DIR/resources.apk" "$BUILD_DIR/app.unsigned.apk"
cd "$BUILD_DIR"
zip -j app.unsigned.apk dex/classes.dex
cd "$SCRIPT_DIR"

# 对齐
zipalign -f 4 "$BUILD_DIR/app.unsigned.apk" "$BUILD_DIR/app.aligned.apk"

# 生成签名密钥
if [ ! -f "$SCRIPT_DIR/debug.keystore" ]; then
    keytool -genkeypair -v \
        -keystore "$SCRIPT_DIR/debug.keystore" \
        -storepass android -alias androiddebugkey -keypass android \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Debug,OU=Debug,O=Debug,L=Debug,S=Debug,C=US" 2>&1 | tail -1
fi

# 签名
jarsigner -sigalg SHA256withRSA -digestalg SHA-256 \
    -keystore "$SCRIPT_DIR/debug.keystore" \
    -storepass android -keypass android \
    "$BUILD_DIR/app.aligned.apk" androiddebugkey 2>&1

if [ $? -ne 0 ]; then
    echo -e "${RED}签名失败${NC}"
    exit 1
fi

mv "$BUILD_DIR/app.aligned.apk" "$BUILD_DIR/AccTileManager.apk"

APK_SIZE=$(ls -lh "$BUILD_DIR/AccTileManager.apk" | awk '{print $5}')
echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN} 构建成功!${NC}"
echo -e "${GREEN}========================================${NC}"
echo "APK: $BUILD_DIR/AccTileManager.apk"
echo "大小: $APK_SIZE"
echo ""
echo "安装:"
echo "  cp $BUILD_DIR/AccTileManager.apk ~/storage/downloads/"
echo "  然后用文件管理器安装"
echo ""

# 清理临时文件
rm -f "$BUILD_DIR/build-tools.zip" "$BUILD_DIR/platform.zip" "$BUILD_DIR/app.unsigned.apk"
rm -rf "$BUILD_DIR/bt" "$BUILD_DIR/dex"
