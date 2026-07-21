#!/usr/bin/env bash
# ============================================
# 无障碍磁贴管理器 - Termux ARM 构建脚本
# ============================================
# 使用方法:
#   pkg install openjdk-17 git unzip curl zip -y
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
mkdir -p "$BUILD_DIR"/{gen,classes,libs,dex,tmp}

echo -e "${GREEN}=== 无障碍磁贴管理器 - Termux 构建 ===${NC}"
echo ""

# ---- 检查依赖 ----
echo -e "${YELLOW}[1/8] 检查依赖...${NC}"
for cmd in java javac aapt2 jarsigner keytool zip curl unzip; do
    if ! command -v $cmd &> /dev/null; then
        echo -e "${RED}缺少 $cmd，请先安装依赖${NC}"
        echo "  pkg install openjdk-17 aapt2 zip unzip curl -y"
        exit 1
    fi
done
ZIPALIGN=""
if command -v zipalign &> /dev/null; then
    ZIPALIGN="zipalign"
fi
echo -e "  ${GREEN}Java $(java -version 2>&1 | head -1)${NC}"
echo -e "  ${GREEN}aapt2 $(aapt2 version 2>&1 | head -1)${NC}"
echo ""

# ---- 下载 build-tools (含 d8 和 zipalign) ----
echo -e "${YELLOW}[2/8] 下载 build-tools...${NC}"
if [ ! -f "$BUILD_DIR/libs/d8.jar" ]; then
    curl -L -o "$BUILD_DIR/build-tools.zip" \
        "https://dl.google.com/android/repository/build-tools_r34-linux.zip" 2>&1 | tail -1
    if [ -f "$BUILD_DIR/build-tools.zip" ] && [ -s "$BUILD_DIR/build-tools.zip" ]; then
        unzip -qo "$BUILD_DIR/build-tools.zip" -d "$BUILD_DIR/tmp/" 2>&1
        D8_JAR=$(find "$BUILD_DIR/tmp" -name "d8.jar" 2>/dev/null | head -1)
        if [ -n "$D8_JAR" ]; then
            cp "$D8_JAR" "$BUILD_DIR/libs/d8.jar"
            echo -e "  ${GREEN}d8 下载成功${NC}"
        else
            echo -e "${RED}d8.jar 未找到${NC}"
            exit 1
        fi
        if [ -z "$ZIPALIGN" ]; then
            ZA_BIN=$(find "$BUILD_DIR/tmp" -name "zipalign" 2>/dev/null | head -1)
            if [ -n "$ZA_BIN" ]; then
                cp "$ZA_BIN" "$BUILD_DIR/libs/zipalign"
                chmod +x "$BUILD_DIR/libs/zipalign"
                ZIPALIGN="$BUILD_DIR/libs/zipalign"
                echo -e "  ${GREEN}zipalign 提取成功${NC}"
            fi
        fi
        # 同时提取 android.jar (platforms/android-34/android.jar 可能不在 build-tools 中)
        # build-tools 不含 android.jar，需要单独下载
    else
        echo -e "${RED}build-tools 下载失败${NC}"
        exit 1
    fi
fi

d8() {
    java -cp "$BUILD_DIR/libs/d8.jar" com.android.tools.r8.D8 "$@"
}

# ---- 下载 android.jar ----
echo -e "${YELLOW}[3/8] 下载 android.jar...${NC}"
if [ ! -f "$BUILD_DIR/libs/android.jar" ]; then
    # 尝试多个已知可用的 platform 版本
    ANDROID_JAR_DOWNLOADED=false
    for url in \
        "https://dl.google.com/android/repository/platform-35_r02.zip" \
        "https://dl.google.com/android/repository/platform-36_r02.zip"; do
        HTTP_CODE=$(curl -sIL "$url" 2>/dev/null | grep "^HTTP" | tail -1 | awk '{print $2}')
        if [ "$HTTP_CODE" = "200" ]; then
            echo "  尝试: $(basename $url .zip) -> $HTTP_CODE"
            curl -L -o "$BUILD_DIR/platform.zip" "$url" 2>&1 | tail -1
            if [ -f "$BUILD_DIR/platform.zip" ] && [ -s "$BUILD_DIR/platform.zip" ]; then
                FILE_TYPE=$(file "$BUILD_DIR/platform.zip" | grep -o "Zip archive")
                if [ -n "$FILE_TYPE" ]; then
                    unzip -qo "$BUILD_DIR/platform.zip" -d "$BUILD_DIR/tmp/" 2>&1
                    PLATFORM_JAR=$(find "$BUILD_DIR/tmp" -name "android.jar" 2>/dev/null | head -1)
                    if [ -n "$PLATFORM_JAR" ]; then
                        cp "$PLATFORM_JAR" "$BUILD_DIR/libs/android.jar"
                        ANDROID_JAR_DOWNLOADED=true
                        echo -e "  ${GREEN}android.jar 下载成功${NC}"
                        break
                    fi
                fi
            fi
        else
            echo "  跳过: $(basename $url .zip) -> $HTTP_CODE"
        fi
    done
    if [ "$ANDROID_JAR_DOWNLOADED" = false ]; then
        echo -e "${RED}android.jar 下载失败，所有版本都不可用${NC}"
        echo "  请尝试手动获取 android.jar 放到 $BUILD_DIR/libs/ 目录"
        exit 1
    fi
fi

# ---- 下载 Shizuku API ----
echo -e "${YELLOW}[4/8] 下载 Shizuku API...${NC}"
if [ ! -f "$BUILD_DIR/libs/shizuku-api.jar" ]; then
    # Shizuku API 在 Maven Central 上是 .aar 格式，需要提取 classes.jar
    curl -L -o "$BUILD_DIR/tmp/shizuku-api.aar" \
        "https://repo1.maven.org/maven2/dev/rikka/shizuku/api/13.1.5/api-13.1.5.aar" 2>&1 | tail -1
    if [ -f "$BUILD_DIR/tmp/shizuku-api.aar" ] && [ -s "$BUILD_DIR/tmp/shizuku-api.aar" ]; then
        FILE_TYPE=$(file "$BUILD_DIR/tmp/shizuku-api.aar" | grep -o "Zip archive")
        if [ -n "$FILE_TYPE" ]; then
            unzip -qo "$BUILD_DIR/tmp/shizuku-api.aar" -d "$BUILD_DIR/tmp/shizuku/" 2>&1
            if [ -f "$BUILD_DIR/tmp/shizuku/classes.jar" ]; then
                cp "$BUILD_DIR/tmp/shizuku/classes.jar" "$BUILD_DIR/libs/shizuku-api.jar"
                echo -e "  ${GREEN}Shizuku API 下载成功$(ls -lh "$BUILD_DIR/libs/shizuku-api.jar" | awk '{print " ("$5")"}')${NC}"
            else
                echo -e "${RED}Shizuku aar 中未找到 classes.jar${NC}"
                exit 1
            fi
        else
            echo -e "${RED}Shizuku API 下载的不是有效的 zip 文件${NC}"
            exit 1
        fi
    else
        echo -e "${RED}Shizuku API 下载失败${NC}"
        exit 1
    fi
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

cp "$BUILD_DIR/resources.apk" "$BUILD_DIR/app.unsigned.apk"
cd "$BUILD_DIR"
zip -j app.unsigned.apk dex/classes.dex
cd "$SCRIPT_DIR"

if [ -n "$ZIPALIGN" ]; then
    $ZIPALIGN -f 4 "$BUILD_DIR/app.unsigned.apk" "$BUILD_DIR/app.aligned.apk"
else
    echo -e "${YELLOW}  跳过 zipalign（未找到）${NC}"
    cp "$BUILD_DIR/app.unsigned.apk" "$BUILD_DIR/app.aligned.apk"
fi

if [ ! -f "$SCRIPT_DIR/debug.keystore" ]; then
    keytool -genkeypair -v \
        -keystore "$SCRIPT_DIR/debug.keystore" \
        -storepass android -alias androiddebugkey -keypass android \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Debug,OU=Debug,O=Debug,L=Debug,S=Debug,C=US" 2>&1 | tail -1
fi

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

rm -f "$BUILD_DIR/build-tools.zip" "$BUILD_DIR/platform.zip" "$BUILD_DIR/app.unsigned.apk"
rm -rf "$BUILD_DIR/tmp" "$BUILD_DIR/bt" "$BUILD_DIR/dex"
