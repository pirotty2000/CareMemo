# 重要操作時の再認証 (Step-up Authentication) 実装プラン

PDF出力という機密情報の持ち出し操作に対し、実行直前の再認証（生体認証または端末認証）を強制します。認証は `MainActivity` に委譲されている既存の仕組みを利用します。

## Proposed Changes

### [Resources]

#### [MODIFY] [strings.xml](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/res/values/strings.xml)
- 認証ダイアログに表示する「理由」の文字列を追加します。
  - `security_auth_reason_pdf_export`: 「PDFを出力するために認証を行ってください」

---

### [UI Components]

#### [MODIFY] [PdfExportActionHandler.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/ui/components/common/PdfExportActionHandler.kt)
- `PdfSettingsDialog` の `onExport` コールバック内で、`onRequireAuthentication` を呼び出すように修正します。
- 認証に成功した（`onSuccess` が呼ばれた）場合のみ、実際の PDF 生成と共有処理（`viewModel.safeLaunch`）を開始します。

#### [MODIFY] [PdfSettingsDialog.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/ui/components/common/PdfSettingsDialog.kt)
- `onRequireAuthentication` パラメータのデフォルト値を `{ _, _, _ -> }` から `{ _, _, onSuccess -> onSuccess() }` に変更します。
  - これにより、明示的に認証ハンドラーが渡されない環境（Compose Preview 等）でも、動作がブロックされないように安全性を確保します。

---

## Verification Plan

### Automated Tests
- `PdfExportActionHandler` の動作確認（認証成功時に処理が継続されること）。
- `PdfSettingsDialog` の Preview が正常に表示・動作すること。

### Manual Verification
1. アプリを起動し、任意の利用者の健康記録画面を開く。
2. ツールバーの「PDF出力」をタップ。
3. ダイアログで設定を行い「PDFを作成」ボタンをタップ。
4. **生体認証（またはPIN）のプロンプトが表示されることを確認。**
5. 認証をキャンセルした場合、PDFが出力されないことを確認。
6. 認証に成功した場合、PDF生成が開始され共有シートが表示されることを確認。
7. 共有シートから戻った際、アプリがロックされずに元の画面が表示されることを確認（既存のバイパス機能の維持確認）。
