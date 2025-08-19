了解！ご要望どおり **pnpm 前提**に直し、**AWS CLI/CDK の環境構築**と **Docker の環境構築**を追記。最後に **ローカル環境図**と**AWS本番図**も追加しました。
（そのまま上からコピペ実行できる構成にしてあります）

---

# 要点サマリ（最初に見る版）

* **目的**：Vue(フロント) と Spring Boot(API) を **GitHub Actions → AWS** で自動テスト・自動デプロイ（最小→実務寄りへ拡張容易）。
* **配信**：フロント **S3 + CloudFront(OAC)**、API **ECS Fargate + ALB**。
* **認証情報**：**GitHub OIDC** で AWS にロール委譲（長期アクセスキー不使用）。ローカル CDK は **AWS SSO** を推奨。
* **IaC**：**AWS CDK(TypeScript)**。
* **テスト**：Vue=Vitest（＋後でPlaywright）、Spring=JUnit5（＋Testcontainers推奨）。
* **運用**：CloudWatch Logs/Alarms、ALBヘルスチェック `/actuator/health`、SPA は `/index.html` フォールバック。

---

# 構成図（2枚）

## 1) ランタイム（AWS側）

```
                 +---------------------- GitHub ----------------------+
                 |  PR: lint/test   main: build/deploy (Actions)     |
                 +---------------------------+------------------------+
                                             | OIDC AssumeRole
                                      +------+------+      Route53/ACM(任意)
                                      |    AWS     |--------------------+
+-------------------+   CloudFront    |             |                    |
|  User (Browser)   | <-------------- |  S3 (SPA)   |                    |
+-------------------+                 |             |                    |
                                      |  ALB (HTTPS, /api/*)  <---+      |
                                      |             ^             |      |
                                      |   ECS(Fargate)            |      |
                                      |    (Spring Boot API)      |      |
                                      |             |             |      |
                                      |   CloudWatch Logs/Alarms  |      |
                                      |        ECR (Image) -------+------+
                                      +---------------------------+
```

**ルーティング例**

* `/*` → S3（SPA）
* `/api/*` → ALB（ECSのSpring Bootへ）

  * CloudFrontのオリジン分岐、またはフロントから ALB/独自ドメインへ CORS

---

## 2) ローカル＆CI（開発側）

```
+---------------------+        git push        +---------------------+
| Dev PC (Windows)    |----------------------->|   GitHub Repository |
| - Node.js + pnpm    |                        | - Actions           |
| - Java 21 + Gradle  |    assume role (OIDC)  |   - Front: build->S3
| - Docker Desktop    |<---------------------->|   - Back : build->ECR
| - AWS CLI + SSO     |                        |           ->ECS/ALB |
| - CDK CLI           |   cdk deploy (SSO)     +---------------------+
+---------------------+------------------------------> AWS
```

* **ローカル**：AWS SSOで一時クレデンシャルを取得して `cdk deploy` 実行
* **CI**：GitHub OIDCでAWSロールをAssume（長期キー不要）

---

# 構築手順（コピペで進める実践フロー・pnpm版）

## 0. 事前準備（ローカル環境）

### 0-1. Node.js & pnpm

* Node.js 18+（推奨 20）
* pnpm（Corepack 経由が楽）

```bash
# Windows PowerShell / macOS / Linux 共通
corepack enable
corepack prepare pnpm@latest --activate
pnpm -v
```

### 0-2. Java & Gradle

* OpenJDK 21（Temurin など）
* Gradle は **プロジェクト同梱の wrapper**（`./gradlew`）を使用

### 0-3. Docker

* **Windows**：Docker Desktop + **WSL2** 有効化（推奨）

  * Windows機能で「仮想マシンプラットフォーム」「Linux 用 Windows サブシステム」をON → 再起動
  * Microsoft Store から Ubuntu などを導入（WSL2）
  * Docker Desktop をインストールして「Use the WSL 2 based engine」にチェック
* **macOS**：Docker Desktop をインストール
* 動作確認：

```bash
docker version
docker run --rm hello-world
```

* 便利設定（任意）：

```bash
# BuildKit有効（高速化）
setx DOCKER_BUILDKIT 1          # Windows PowerShell
export DOCKER_BUILDKIT=1        # macOS/Linux(一時)
```

### 0-4. AWS CLI v2 & SSO

* AWS CLI v2 をインストール後、**AWS SSO** 設定（ローカルの `cdk deploy` 用）

```bash
aws --version

# 初回のみ：SSO設定を作成（プロファイル名は dev 例）
aws configure sso
# 対話で SSO開始URL / SSOリージョン / アカウント / ロール を選択
# プロファイル名: dev (例)

# 認証（有効期限切れ時もこれで再ログイン）
aws sso login --profile dev
```

> ※ローカルは SSO、CI は OIDC の二刀流が安全＆快適です。

### 0-5. CDK CLI

```bash
# グローバルにCDK CLIを入れる（pnpm）
pnpm add -g aws-cdk

cdk --version
```

---

## 1. CDK プロジェクト作成（インフラ基盤）

```bash
mkdir app-cdk && cd app-cdk
pnpm init -y
pnpm add -D aws-cdk aws-cdk-lib constructs typescript ts-node
# cdk init はCLI。pnpm dlxでも可:
# pnpm dlx aws-cdk cdk init app --language typescript
cdk init app --language typescript
```

`bin/app-cdk.ts` を修正（フロント/バックの2スタックを起動）：

```ts
#!/usr/bin/env node
import 'source-map-support/register';
import { App, Tags } from 'aws-cdk-lib';
import { FrontendSpaStack } from '../lib/frontend-spa-stack';
import { BackendApiStack } from '../lib/backend-api-stack';

const app = new App();
const env = { account: process.env.CDK_DEFAULT_ACCOUNT, region: 'ap-northeast-1' };

new FrontendSpaStack(app, 'FrontendSpaStack', { env });
new BackendApiStack(app, 'BackendApiStack', { env });

Tags.of(app).add('Project', 'VueSpringSample');
```

### 1-1. フロント（S3 + CloudFront OAC）

`lib/frontend-spa-stack.ts` に **OAC** と **SPAフォールバック** を実装（前回提示コードをそのまま利用可）。

### 1-2. バックエンド（ECR + ECS Fargate + ALB）

`lib/backend-api-stack.ts` に **VPC, ALB, ECS, ECR, SG** を実装（前回提示コードをそのまま利用可）。

### 1-3. 初回デプロイ（ローカルからSSOで）

```bash
# SSOプロファイルを明示（例: dev）
setx AWS_PROFILE dev        # Windows
export AWS_PROFILE=dev      # macOS/Linux(一時)

# 1アカウント1リージョンで最初の1回
cdk bootstrap aws://<ACCOUNT_ID>/ap-northeast-1

# スタック作成
cdk deploy FrontendSpaStack
cdk deploy BackendApiStack
```

**控える出力**（後でActionsに設定）：

* `SpaBucketName` / `SpaDistributionId` / `SpaUrl`
* `AlbDns`
* `EcrRepo`（URI）

---

## 2. GitHub → AWS OIDC ロール（長期キー不要）

### 2-1. 信頼ポリシー（例）

* プロバイダ：`token.actions.githubusercontent.com`
* 条件：

  * `aud = sts.amazonaws.com`
  * `sub = repo:<OWNER>/<REPO>:*`（対象リポに限定）

### 2-2. 付与権限（最小化の目安）

* フロント：`s3:{PutObject,DeleteObject,ListBucket}`, `cloudfront:CreateInvalidation`
* バック：`ecr:*`（pushに必要な範囲）、`ecs:{UpdateService,Describe*}`
* `iam:PassRole` が要る構成なら対象ロールを限定

> できた **ロールARN** をメモ：`arn:aws:iam::<ACCOUNT_ID>:role/github-oidc-deploy`

---

## 3. アプリ雛形

### 3-1. フロント（Vue + Vitest）

```bash
# ルート直下に frontend/ 作成
pnpm create vue@latest frontend
cd frontend
pnpm i
pnpm add -D vitest @vitest/ui @testing-library/vue eslint prettier
```

`package.json`（抜粋）

```json
{
  "scripts": {
    "build": "vite build",
    "dev": "vite",
    "test:unit": "vitest run",
    "lint": "eslint ."
  }
}
```

* SPAの404/403は CloudFront 側で `/index.html` へフォールバック済み。

### 3-2. バックエンド（Spring Boot + Dockerfile）

* `backend/` に Gradle プロジェクト（`spring-boot-starter-web`, `actuator`）
* `application.yml`：`management.endpoints.web.exposure.include=health`
* `backend/Dockerfile`

```dockerfile
FROM gradle:8.8-jdk21-alpine AS build
WORKDIR /app
COPY . .
RUN ./gradlew clean bootJar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
```

* ヘルスチェック：`/actuator/health`（TargetGroupで設定）

---

## 4. GitHub Actions（CI/CD・pnpm版）

### 4-1. リポジトリ Variables/Secrets

* `AWS_ROLE_ARN`：OIDCロールARN
* `AWS_REGION`：`ap-northeast-1`
* `SPA_BUCKET`：`SpaBucketName`
* `SPA_DIST_ID`：`SpaDistributionId`
* `ECR_REPO`：`spring-api`（CDKのECR名に合わせる）
* `ECS_CLUSTER` / `ECS_SERVICE`：CDKで作った名称

### 4-2. フロント：`.github/workflows/deploy-frontend.yml`

```yaml
name: deploy-frontend
on:
  push:
    branches: [main]
    paths: ["frontend/**"]

permissions: { id-token: write, contents: read }

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: '20' }
      - run: corepack enable && corepack prepare pnpm@latest --activate
      - run: cd frontend && pnpm i --frozen-lockfile && pnpm run build
      - uses: aws-actions/configure-aws-credentials@v4
        with:
          role-to-assume: ${{ vars.AWS_ROLE_ARN }}
          aws-region: ${{ vars.AWS_REGION }}
      - run: aws s3 sync frontend/dist s3://${{ vars.SPA_BUCKET }} --delete
      - run: aws cloudfront create-invalidation --distribution-id ${{ vars.SPA_DIST_ID }} --paths "/*"
```

### 4-3. バック：`.github/workflows/deploy-backend.yml`

```yaml
name: deploy-backend
on:
  push:
    branches: [main]
    paths: ["backend/**"]

permissions: { id-token: write, contents: read }

jobs:
  build-push-deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: 'temurin', java-version: '21' }
      - run: cd backend && ./gradlew clean test bootJar
      - uses: aws-actions/configure-aws-credentials@v4
        with:
          role-to-assume: ${{ vars.AWS_ROLE_ARN }}
          aws-region: ${{ vars.AWS_REGION }}
      - uses: aws-actions/amazon-ecr-login@v2
        id: ecr
      - name: Build & Push Image
        run: |
          TAG=${GITHUB_SHA::7}
          REPO_URI=${{ steps.ecr.outputs.registry }}/${{ vars.ECR_REPO }}
          docker build -t $REPO_URI:$TAG backend
          docker push $REPO_URI:$TAG
          echo "IMAGE=$REPO_URI:$TAG" >> $GITHUB_ENV
      - name: Update ECS Service
        run: |
          aws ecs update-service \
            --cluster ${{ vars.ECS_CLUSTER }} \
            --service ${{ vars.ECS_SERVICE }} \
            --force-new-deployment
```

> これで `main` への push で、**フロントは S3/CloudFront**、**バックは ECS** まで自動反映。

---

## 5. 動作確認チェックリスト

* [ ] **フロント**：`SpaUrl` にアクセスして反映確認
* [ ] **API**：`http(s)://<AlbDns>/actuator/health` が `{"status":"UP"}`
* [ ] **連携**：フロントから `/api/...` が動く（CORS/ルーティングOK）
* [ ] **CI**：Actions が OIDC で AssumeRole（長期キー未使用）
* [ ] **ログ**：CloudWatch Logs にアプリログが出力

---

## 6. 次の一歩（実務寄り拡張）

* **ステージング環境**：`stg` Stack + Playwright E2E 合格で自動デプロイ
* **Secrets/SSM**：DB接続等は Secrets Manager/SSM 経由
* **Blue/Green**：Codedeploy + ECS で段階切替/自動ロールバック
* **監視**：ALB 5xx、p95 レイテンシ、ECS CPU/Mem で Alarm → Slack
* **WAF**：重要なエンドポイントはWAFで保護
* **独自ドメイン**：Route53 + ACM で HTTPS 化

---

### 補足：ローカルでの ECR/ECS テスト小ワザ

```bash
# SSOでログイン済みのプロファイルを使ってECRログイン（ローカル検証用）
aws ecr get-login-password --profile dev --region ap-northeast-1 \
| docker login --username AWS --password-stdin <ACCOUNT_ID>.dkr.ecr.ap-northeast-1.amazonaws.com
```

---

必要なら、**CloudFrontのオリジン分岐（`/*`→S3, `/api/*`→ALB）をCDKで書く具体コード**や、**CDKをECRの既存イメージ参照（`fromEcrRepository`）に切り替える版**もすぐ出します。
まずはこの手順で **pnpm 前提**の最小構成を一度通してみてください。
