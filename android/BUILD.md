# SkyGoto Android App 构建指南

## 环境要求

- **JDK 17** 或更高版本
- **Android SDK** (API 34)
- **Gradle 8.2** 或更高版本 (项目已包含 wrapper)

## 快速构建

### 方式一：使用 Gradle Wrapper (推荐)

```bash
cd android

# Linux/macOS
chmod +x gradlew
./gradlew assembleDebug

# Windows
gradlew.bat assembleDebug
```

APK 输出位置: `app/build/outputs/apk/debug/app-debug.apk`

### 方式二：使用 Android Studio

1. 打开 Android Studio
2. 选择 "Open an existing project"
3. 选择 `android` 文件夹
4. 等待 Gradle sync 完成
5. 点击 Run > Run 'app' 或使用 Build > Build APK

## Gradle Wrapper 说明

项目已包含 Gradle Wrapper，无需单独安装 Gradle。Wrapper 文件：
- `gradle/wrapper/gradle-wrapper.properties` - 已配置 Gradle 8.2

## 构建变体

| 命令 | 说明 |
|------|------|
| `./gradlew assembleDebug` | Debug 构建 |
| `./gradlew assembleRelease` | Release 构建 (需要签名) |
| `./gradlew clean` | 清理构建 |
| `./gradlew build` | 完整构建 |

## 签名配置 (Release)

在 `app/build.gradle.kts` 中添加：

```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("your-keystore.jks")
            storePassword = "your-password"
            keyAlias = "your-alias"
            keyPassword = "your-key-password"
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

## 依赖安装 (无 Android Studio)

如果只有命令行：

```bash
# 下载 Android command line tools
# https://developer.android.com/studio#command-line-tools-only

# 设置环境变量
export ANDROID_HOME=/path/to/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

# 安装 SDK 组件
sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"

# 构建
./gradlew assembleDebug
```

## 故障排除

### Wrapper 权限错误
```bash
chmod +x gradlew
```

### SDK 许可问题
```bash
yes | sdkmanager --licenses
```

### 内存不足
修改 `gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
```

## 输出 APK

构建成功后，APK 文件位于:
```
android/app/build/outputs/apk/debug/app-debug.apk
```

可直接安装到 Android 设备进行测试。
