# SpringBoot+PostgreSQL化に備えたデータベース変更とデータ移行

## 現状

- スタンドアロン動作(ひとりで、ひとつの端末で使用中)
- ひとりでも、スマホとタブレットで利用したいと思うと、データのエクスポート→インポートが必要で、且つ、データの不整合が起きないよう、一方の「記録・参照」、もう一方を「参照のみ」と意識して利用する必要がある

## 将来

- Raspberry Pi5＋Ubuntu24.04＋Nginx(リバースプロキシ)＋Apache2環境で、既にApache2にてWordPressやNextCloudが稼働している
- このRaspberry Pi5サーバ上のSpringBoot＋PostgreSQLにデータを保存し参照できるようにしたい

## 当面のゴールの前提

- ひとりでアプリ使用、ひとりで複数の端末(スマホとタブレット)を使用
- PostgreSQL側では主キーのデータ型をUUIDにしたいので、Androidアプリ側も主キーはUUID(要：String変換？)に変更
- SpringBoot＋PostgreSQL化した場合も「Androidアプリ側で主キー(UUID)を生成」する

## 当面のゴール

- SpringBoot＋PostgreSQL化の準備として「Entity変更」を済ませておきたい
- 現在、Roomデータベースに「現行Entity」のフォーマットでデータが格納されているので、データ移行作業を済ませておきたい

## 当面のゴールの仕様

- GitHubでブランチを分け、Migrationのトラブルがあれば、一からやり直せるようにする
- PostgreSQL側の主キーは「UUID型」を使うが、Room側ではStringとして保持する
- Room側は、UUID.randomUUID().toString()をデフォルト値として自動採番する
- 既存データがあるので、Migrationクラスを実装する
  - 新テーブルを一時的な名前で作成し、PKをUUID(をStringに変換)したもので定義
  - 旧テーブルからデータを読込、旧IDも保持したまま新しいUUIDを発行し、マッピングテーブルを作る
  - マッピングテーブルに基づき、外部キーの整合性を保ちながら新テーブルへInsertする
  - 旧テーブルのリレーションは以下の通りとし、Migration作業の際に「旧テーブルに不備があれば、新テーブルで解消」する
    - [person_db] -> [height_and_weight_db]
    - [person_db] -> [bp_and_pulse_db]
    - [person_db] -> [glucose_and_hba1c_db]
    - [person_db] -> [condition_at_visit_db] -> [condition_photo_db]
- 本来は、オフライン時を考慮し、SpringBoot＋PostgreSQLに保存できなかったデータを保持する新たなEntityが必要だが、現時点では実装せず今後の課題・後日設計する
- Migrationに必要なEntityは、作成してよいものとし、正常に移行できたことを確認できたときに、後日Dropするかどうか検討し実行に移す
- Migration作業は「新テーブル作成」→「データ移行」→「旧テーブル削除」→「リネーム」→「旧テーブルにあった一意制約を新テーブルに実装」
- 既存の「バックアップ」「リストア」機能は、Migration作業を終えてから作り直す、
- 旧エクスポートデータの「新テーブルへのインポータ」は不要(私しかこのアプリを使っていないので、旧データを読み込ませる操作はしないよう徹底することができます)
- 新テーブル用のインポート処理で旧テーブルのエクスポート・データが読み込まれた場合は、エラーにして読込処理をしない
- 同期用に、各「新テーブル」に@ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()を追加し、移行時は「このMigrationが実行された日時」をセットする

## 当面のゴールの仕様（その他）
### 外部キー制約と「親のない子」の扱い（データ整合性）
- 移行時に、親（例：Person）が存在しない子レコード（例：HeightAndWeight）が見つかった場合、それらを「破棄する」
### 写真ファイル名の一意性と重複
- 写真は移行しない(動作検証で3～4枚撮影し保存しただけなので、最初は「空」でも問題無い)
### Migration時の「SQLite 特有の制約」への対処
1. PRAGMA foreign_keys = OFF; (制約の一次無効化)
1. 新テーブル作成 (UUID型)
1. データ移行
1. 旧テーブル削除
1. 新テーブルを元の名前にリネーム
1. PRAGMA foreign_keys = ON;
1. これを Room の Migration クラス内で supportDatabase.execSQL() を用いて実行する



## 作業の手順

1. Entityの定義変更: id を Int から String (UUID) に変更。
2. TypeConverterの作成: (UUIDクラスを直接使う場合)
3. Migrationクラスの実装: SQLを用いたデータの詰め替えロジック。
4. Repository/ViewModelの修正: IDの型変更に伴うコンパイルエラーの解消。
5. UIレイヤー（画面遷移の引数など）への影響があれば解消
5. テストコードの修正: 現在あるユニットテストが通ることを確認。