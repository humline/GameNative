FROM eclipse-temurin:17-jdk-jammy

ENV ANDROID_SDK_ROOT=/opt/android-sdk \
    ANDROID_HOME=/opt/android-sdk \
    PATH=/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:$PATH

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl unzip \
    && rm -rf /var/lib/apt/lists/*

RUN curl -fsSL https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -o /tmp/android-command-line-tools.zip \
    && mkdir -p /tmp/android-command-line-tools /opt/android-sdk/cmdline-tools/latest \
    && unzip -q /tmp/android-command-line-tools.zip -d /tmp/android-command-line-tools \
    && mv /tmp/android-command-line-tools/cmdline-tools/* /opt/android-sdk/cmdline-tools/latest/ \
    && rm -rf /tmp/android-command-line-tools /tmp/android-command-line-tools.zip \
    && yes | sdkmanager --sdk_root=/opt/android-sdk --licenses >/dev/null \
    && sdkmanager --sdk_root=/opt/android-sdk \
        "platform-tools" \
        "platforms;android-36" \
        "build-tools;36.0.0" \
        "ndk;27.3.13750724"

WORKDIR /workspace
COPY . .

RUN ./gradlew --no-daemon :app:assembleModernDebug \
    && test -s app/build/outputs/apk/modern/debug/app-modern-debug.apk
