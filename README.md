# 无障碍磁贴管理器 (Accessibility Tile Manager)

通过状态栏磁贴一键开关任意 App 的无障碍服务权限。

## 功能

- 3 个可配置的 QS 磁贴，每个独立控制一个 App 的无障碍权限
- 点击磁贴：自动开启无障碍服务 + 启动目标 App
- 再次点击：自动关闭无障碍服务 + 停止目标 App
- 完全基于 Shizuku shell 命令，不需要 Root

## 前提

- Android 7.0+ (API 24+)
- 已安装并启动 [Shizuku](https://github.com/RikkaApps/Shizuku)
- Shizuku 已授予 WRITE_SECURE_SETTINGS 权限

## 使用方法

### 方法 1: Termux 构建（手机端）

1. 安装 Termux (从 F-Droid 安装)
2. 将整个项目文件夹复制到手机
3. 在 Termux 中执行:

```bash
pkg install openjdk-17 git unzip curl -y
cd /path/to/AccessibilityTileManager
bash build.sh
```

4. 构建完成后安装 APK

### 方法 2: Android Studio（电脑端）

直接用 Android Studio 打开项目，点击 Run。

## 配置示例

以 Universal Copy (全局复制) 为例:

| 字段 | 值 |
|------|------|
| 磁贴名称 | 全局复制 |
| 无障碍服务 | `com.forfan.bigbang/.service.UniversalCopyService` |
| App 包名 | `com.forfan.bigbang` |
| 开启时启动App | 勾选 |
| 关闭时停止App | 勾选 |

> 无障碍服务组件名的格式为: `包名/服务完整类名`

## 如何查找服务组件名

1. 用 Shizuku 执行: `settings get secure enabled_accessibility_services`
2. 如果服务已在列表中，会显示完整组件名
3. 也可以用 `dumpsys accessibility` 查看所有已注册的无障碍服务
