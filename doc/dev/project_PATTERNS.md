# CareMemo 実装パターン集 (project_PATTERNS.md)

このドキュメントでは、CareMemo プロジェクトで推奨される具体的なコード実装例を定義します。設計思想やルールについては `project_RULES.md` を参照してください。

---

# 1. 操作ログ (監査ログ) の実装パターン

データの変更を伴う操作（CRUD）における、Repository と ViewModel の具体的な連携例です。

## Repository の実装
`AuditLogRepository` を使用し、業務処理の後にログを記録します。

```kotlin
// Repositoryの実装例
suspend fun insertData(item: Data, screenName: String = "", operation: String = "") {
    dao.insert(item)
    // 最後にログを記録
    try {
        auditLogRepository.log(
            tableName = "data_table",
            actionType = "INSERT",
            affectedId = item.id.toString(),
            screenName = screenName,
            operation = operation
        )
    } catch (e: Exception) {
        // ログ記録の失敗が本来の業務処理を妨げないようにする
        e.printStackTrace()
    }
}
```

## ViewModel からの呼び出し
UIイベント発生時に、論理的な画面名と操作内容を渡します。

```kotlin
// ViewModelの実装例
fun saveData() {
    viewModelScope.launch {
        repository.insertData(
            item = currentData,
            screenName = "データ登録画面",  // 日本語で論理的な画面名を指定
            operation = "保存ボタン押下"    // 「何をしたか」を指定
        )
    }
}
```
# ソースファイル・コメントテンプレート

新規作成や大規模な修正時には、以下のテンプレートをファイルヘッダー等に適用してください。

## ui/screens 用
```kotlin
/*
Screen :
【画面名】：
【役割】：
【主な機能】：
【遷移】：
【使用するViewModel】：
【使用するComponents】：
【備考】：
*/
```

## ui/components 用
```kotlin
/*
Component：
【役割】：
【主な機能】：
【想定する利用場所】：
【このコンポーネントでは行わないこと】：
【公開composable】：
*/

---

最終更新日: 2026/07/12
