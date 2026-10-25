# Security and privacy review

Static source and configuration review of GameNative. This is not an antivirus verdict: bundled native and Windows executables were not dynamically analyzed or submitted to a malware scanner, and dependency versions were not checked against a live vulnerability database.

## Findings and remediation

| Severity | Finding | Status |
| --- | --- | --- |
| Medium | Exported game-launch intents could start a game with caller-supplied launch arguments or container overrides without asking the user. | External launch requests now require an explicit confirmation before dispatch. |
| Medium | PostHog analytics was enabled by default, and the SDK opt-out state was not synchronized with the settings toggle. | Analytics now defaults to off, the SDK opt-in/out follows the setting at startup and when changed, and PostHog person profiles are disabled. |
| Medium | The update installer accepted server-supplied URLs without validating the host or filename and did not check the APK identity before installation. | Only HTTPS URLs from the configured download hosts and the expected versioned APK filename are accepted. The package name and signing identity are checked against the installed app before opening the installer. |
| Low-Medium | Android cleartext networking was globally allowed. | Cleartext is disabled by default; HTTP is allowed only for loopback (`127.0.0.1` and `localhost`) used by local integrations. |
| Informational | The app bundles native libraries and Windows `.dll`/`.exe` files, plus a vendored JAR. Their provenance and correspondence to source builds could not be confirmed during static review. | No virus claim is made. Review upstream provenance or scan release artifacts before distribution. |
| Informational | A mutable JavaSteam snapshot dependency and missing Gradle dependency-verification metadata were observed. No CVE check was performed. | Resolve and verify dependencies in CI, and run Dependabot or OSV against the resolved dependency graph. |
| Informational | A repository `keystore` data file was present, but its type and whether it is a release signing key could not be established from static inspection. | Confirm its ownership and purpose; remove it from source control if it contains a private signing key and rotate that key if exposed. |
| Informational | No standalone APK or DEX payload was identified in the repository inventory. The app does request package installation permission for its own update flow. | The update path now validates the expected source, package, and signing identity before launching Android's package installer. |
| Low | Several third-party login/store WebViews enable JavaScript; some use JavaScript interfaces, and the allowed-origin constraints were not fully verified. | Keep JavaScript bridges limited to trusted provider origins and review them when those login flows change. |

## Data and permissions

- Account tokens are stored encrypted using Android Keystore-backed AES; Android app backup is disabled.
- Network requests go to GameNative services, PostHog (only when the user opts in), store/account providers, and billing or integrity SDKs. Compatibility requests include device/GPU identifiers and related hardware fields. User-triggered support reports can include logs and performance data.
- `INTERNET` is required for sign-in, downloads, updates, and game services. `RECORD_AUDIO` supports the optional microphone bridge and is requested at runtime when enabled. `REQUEST_INSTALL_PACKAGES` supports the in-app updater. `READ_LOGS` is signature-level on current Android and is used for support diagnostics where available. Bluetooth permissions support bonded Steam Controller connections. Media access is constrained, and backup is disabled.
- No obvious malicious source code or suspicious payload behavior was found in the static review. Runtime network capture, binary malware scanning, and current CVE assessment remain necessary for a stronger assurance statement.

## Build artifact

`bash tools/build-with-nerdctl.sh` builds the `modernDebug` APK in a container and copies it to `GameNative-modern-debug.apk` in the repository root. The container build excludes Git metadata, local Gradle state, local properties, and signing-key files from its build context.
