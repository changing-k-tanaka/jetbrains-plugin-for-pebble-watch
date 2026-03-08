# CLAUDE.md

このファイルは、リポジトリ内のコードを操作する際に Claude Code (claude.ai/code) へのガイダンスを提供します。

**重要: ユーザーへの回答はすべて日本語で行うこと。**

## プロジェクト概要

[IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template) をベースとした、Pebble Watch 向け JetBrains IntelliJ Platform プラグインです。IntelliJ IDEA 2025.2+（ビルド 252+）を対象とし、JVM 21 を target とした Kotlin で実装されています。

現在は開発初期段階であり、テンプレートのスキャフォールドが整っている状態ですが、Pebble Watch 固有の機能はまだ実装されていません。サンプルコードファイル（`MyBundle`、`MyProjectService`、`MyProjectActivity`、`MyToolWindowFactory`）は今後置き換えるためのテンプレートプレースホルダーです。

## コマンド

```bash
# サンドボックス化された IDE インスタンスでプラグインを実行
./gradlew runIde

# 配布用プラグイン zip をビルド
./gradlew buildPlugin

# 全テストを実行
./gradlew check

# テストのみ実行（Kover カバレッジなし）
./gradlew test

# 対象 IDE バージョンとのプラグイン互換性を検証
./gradlew verifyPlugin

# UI テスト用 IDE を起動（robot-server ポート 8082）
./gradlew runIdeForUiTests

# JetBrains Marketplace へ公開（環境変数が必要: PUBLISH_TOKEN, CERTIFICATE_CHAIN, PRIVATE_KEY, PRIVATE_KEY_PASSWORD）
./gradlew publishPlugin
```

## アーキテクチャ

### プラグイン登録（`plugin.xml`）

拡張ポイント、サービス、アクションはすべて `src/main/resources/META-INF/plugin.xml` に宣言する必要があります。現在このプラグインは `com.intellij.modules.platform` のみに依存しており、言語固有の依存関係はありません。

### 使用している主な拡張パターン

- **`@Service(Service.Level.PROJECT)`** — プロジェクトスコープのサービス。`project.service<T>()` で注入する
- **`ToolWindowFactory`** — ツールウィンドウ UI を作成する。`plugin.xml` の `<toolWindow>` タグで登録する
- **`ProjectActivity`** — コルーチンベースの起動フック（プロジェクトを開いた際に実行される）
- **`DynamicBundle`** — i18n ラッパー。UI 文字列はすべて `src/main/resources/messages/MyBundle.properties` に記述する

### ビルド設定

- **`gradle.properties`** — バージョン番号、プラグインメタデータ、プラットフォームバージョン、バンドルプラグイン・モジュールの依存関係
- **`gradle/libs.versions.toml`** — 依存関係のバージョンカタログ
- **`build.gradle.kts`** — プラグインの説明は `README.md` の `<!-- Plugin description -->` と `<!-- Plugin description end -->` の間から抽出される。これらのコメントを削除しないこと
- Gradle Configuration Cache および Build Cache はいずれも有効になっている

### テスト

テストは `BasePlatformTestCase`（JUnit 4）を継承します。テストデータファイルは `src/test/testData/` に置き、`@TestDataPath("\$CONTENT_ROOT/src/test/testData")` で参照します。単一テストクラスの実行:

```bash
./gradlew test --tests "com.github.pebbleloveru.jetbrainspluginforpebblewatch.MyPluginTest"
```

### リリースワークフロー

1. `gradle.properties` の `pluginVersion` を更新する
2. `CHANGELOG.md` の `[Unreleased]` 下にリリースノートを追加する
3. `main` へプッシュ — CI がビルド・テスト・検証を行い、GitHub ドラフトリリースを作成する
4. ドラフトリリースを公開するとリリースワークフローが起動し、`./gradlew publishPlugin` が実行される