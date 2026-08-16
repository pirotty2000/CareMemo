# アーキテクチャと設計構造

## 1. レイヤー別責務定義

プロジェクトの各レイヤーは、以下の原則に基づき独立した責務を負います。

| レイヤー / パッケージ                 | 主要な役割                        | 注意事項                    |
|:-----------------------------|:-----------------------------|:------------------------|
| **`data/AppSpecifications`** | **数値の源泉**。定数管理。              | ロジック記述禁止。               |
| **`logic/common`**           | **ドメインルール（脳）**。判定ロジック。       | **[MUST] Android依存禁止**。 |
| **`logic/feature`**          | **機能ロジック**。UiState/Entity変換。 | 副作用（Repo呼び出し、UI操作）禁止。   |
| **`ui/utils`**              | **UI固有ユーティリティ**。           | VisualTransformation 等の Compose 依存部品。 |
| **`viewmodel`**              | **状態管理と調整**。非同期制御。           | 複雑な判定は `logic` へ委譲。     |
| **`data/repository`**        | **データアクセス**。DB操作。            | 業務判断禁止。                 |
| **`ui/screens`**             | **エントリポイント**。遷移の実行。          | 詳細なレイアウト構築禁止。           |
| **`ui/components`**          | **Stateless部品**。             | ViewModel依存禁止。          |

## 2. 依存方向の管理 (Dependency Matrix)

レイヤー間の依存関係を単方向に保ち、循環参照や責務の混入を防ぐため、以下の依存許可マトリックスを遵守してください。

| レイヤー                        | 依存可能な対象                            | 依存してはならない対象                                                                    |
|:----------------------------|:-----------------------------------|:-------------------------------------------------------------------------------|
| **UI (Screens/Components)** | ViewModel, Mapping, Theme          | Repository, Database, Logic (原則)                                               |
| **ViewModel**               | Logic, Repository, Mapping, Entity | Activity, Context, Composable, Android Framework クラス (Uri, BiometricManager 等) |
| **Logic (Common/Feature)**  | AppSpecifications, Entity          | ViewModel, Repository, Android API                                             |
| **Repository**              | Database (DAO), AuditLog, Entity   | ViewModel, Logic, UI, Android Logic (原則)                                       |
| **Mapping / Theme**         | AppSpecifications                  | ViewModel, Repository, Logic                                                   |

- **[MUST] 依存の単方向性**: 依存は常に「上位 (UI)」から「下位 (Data/Spec)」に向かって流れるようにし、下位レイヤーが上位レイヤー（例：Logic が ViewModel）を直接参照することは厳禁とします。
- **[MUST] UI からの飛び越し禁止**: UI コンポーネントが ViewModel を介さずに Repository や Database に直接アクセスすることを禁止します。
- **[MUST] Repository の純粋化**: Repository はデータの永続化に専念し、ビジネスルールやデータのクレンジング（正規化・重複回避等）を行ってはなりません。これらのロジックは必ず Logic レイヤーへ集約し、Repository は加工済みのデータを受け取るか、コールバックを介して外部からロジックを注入する設計としてください。
- **[MUST] ViewModel の純粋化 (Pure Kotlin)**: ViewModel はユニットテスト容易性を最大化するため、Android フレームワーク（Context, Application, Uri 等）に依存してはなりません。物理操作（ファイル、リソース）は Repository へ、システム状態判定（生体認証可否等）は Activity へ委譲してください。

## 3. 詳細画面の設計原則 (A/B/C共通)

### 2.1. 構成ファイルの統一 (4ファイル構成)
- `Screen.kt`: WindowSize判定、ViewModel管理、遷移の実行。
- `ScreenPhone.kt`: スマホ用レイアウト。
- `ScreenTablet.kt`: タブレット用レイアウト。
- `ScreenContent.kt`: 共有の表示・入力ロジック（Stateless）。

### 2.2. ViewModel の二段構え (Dual-ViewModel)
- `PersonDetailUiStateViewModel`: 共通フレームワーク。
- 専門 ViewModel: 各カテゴリ固有の操作（`PersonHealthViewModel` 等）。
- **自律的初期化**: ViewModel が `SavedStateHandle` から引数を自ら取得し、ロードを開始する構造を維持します。

## 3. プラットフォーム機能（OS UI）との委譲ルール

ウィンドウ管理やライフサイクルの不整合を防ぐため、OS連携の実行場所を厳格に管理します。

- **Activity へ委譲すべきもの**:
    - **生体認証 (BiometricPrompt)**
    - **システム状態の判定 (BiometricManager.canAuthenticate 等)**
    - **ランタイム権限要求**
- **Repository へ委譲すべきもの**:
    - **物理ファイル操作 (ImageUtils 呼び出し等)**
    - **ContentResolver を用いた副作用の実行**
- **Composable で起動を許可するもの**:
    - **ActivityResult API**（カメラ、ファイル選択）
    - ※ただし、結果の処理（保存・ロジック）は ViewModel へ委譲すること。

## 4. 健康記録プロセッサ基盤 (Processor Pattern)

- カテゴリ追加に対する拡張性を高めるため、横断的なロジックでは必ず **`HealthCategoryProcessor`** インターフェースを介して操作してください。
- ViewModel からカテゴリ固有の Repo メソッドを直接呼ぶことを制限し、プロセッサに委譲します。

---
最終更新日: 2026/08/15
