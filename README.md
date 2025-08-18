いいですね。まずは**要点サマリ → 構成図 → 構築手順**の順で、一気に通せる形で提示します。CDKはTypeScript前提です。

---

# 要点サマリ（最初に見る版）

* **目的**：Vue(フロント) と Spring Boot(API) のアプリを、**GitHub Actions → AWS**で自動テスト・自動デプロイできる最小〜実務寄り構成を作る。
* **配信**：フロントは **S3 + CloudFront(OAC)**、APIは **ECS Fargate + ALB**。
* **認証情報**：**GitHub OIDC** で AWS にロール委譲（長期アクセスキーは使わない）。
* **IaC**：**AWS CDK(TypeScript)** でインフラをコード化（将来Blue/Green等へ拡張容易）。
* **テスト**：Vueは Vitest（＋後でPlaywright）、Springは JUnit5（＋Testcontainers推奨）。
* **運用**：CloudWatch Logs/Alarms、ALBヘルスチェック `/actuator/health`、SPAは`/index.html`にフォールバック。

---

# 構成図（最小構成 → 実務寄りに拡張しやすい）

```
                 +---------------------- GitHub ----------------------+
                 |  PR: lint/test   main: build/deploy (Actions)     |
                 +---------------------------+------------------------+
                                             | OIDC AssumeRole
                                      +------+------+
                                      |    AWS     |
+-------------------+   CloudFront    |             |    ALB (HTTP/HTTPS)
|  User (Browser)   | <-------------- |  S3 (SPA)   |  <------------------+
+-------------------+                 |             |                     |
                                      |  ECS(Fargate)  ← ECR(Image)       |
                                      |     (Spring Boot API)            |
                                      |             |                     |
                                      |   CloudWatch Logs/Alarms         |
                                      +----------------------------------+
```

> ルーティング：
>
> * `/*` → S3（SPA）
> * `/api/*` → ALB（ECSのSpring Bootへ） ※CloudFrontでオリジン分岐 or フロントから ALB/独自ドメインへCORS

---

# 構築手順（コピペで進める実践フロー）

## 0. 前提インストール

* Node.js 18+ / npm（pnpm推奨）
* AWS CLI v2（`aws configure`は不要：OIDCで実行するため）
* AWS CDK v2（`npm i -g aws-cdk`）
* Docker（バックエンドのビルド用）

---

## 1. CDK プロジェクト作成（インフラ基盤）

```bash
mkdir app-cdk && cd app-cdk
npm init -y
npm i -D aws-cdk aws-cdk-lib constructs typescript ts-node
npx cdk init app --language typescript
```

`bin/app-cdk.ts` に **フロント用 Stack** と **バックエンド用 Stack** を読み込む形でエントリを用意。
（※先に雛形だけでOK。後でlib配下にファイルを置きます）

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

`lib/frontend-spa-stack.ts` を作成（**OAC**・SPAフォールバック込み）。

> ※このファイルは前メッセージで提示した実装をそのまま使えます。迷ったらそのまま貼り付けでOK。

### 1-2. バックエンド（ECR + ECS Fargate + ALB）

`lib/backend-api-stack.ts` を作成（**VPC, ALB, ECS, ECR, SecurityGroup** 一式）。

> ※こちらも前メッセージのコードをそのまま利用可能。実運用は `ContainerImage.fromEcrRepository` に後で更新。

### 1-3. 初回デプロイ

```bash
# CDKのブートストラップ（1アカウント/1リージョンにつき最初の1回）
cdk bootstrap aws://<ACCOUNT_ID>/ap-northeast-1

# スタック作成
cdk deploy FrontendSpaStack
cdk deploy BackendApiStack
```

**控える出力**（後でGitHub Actionsにセット）：

* `SpaBucketName` / `SpaDistributionId` / `SpaUrl`
* `AlbDns`（ALBのDNS）
* `EcrRepo`（ECRのURI）

---

## 2. GitHub → AWS OIDC ロール作成（長期キー不要）

### 2-1. IAM ロール（信頼ポリシー）

* サービス：`sts.amazonaws.com`（web identity）
* プロバイダ：`token.actions.githubusercontent.com`
* 条件例（最小化例）：

  * `aud: sts.amazonaws.com`
  * `sub: repo:<YOUR_GITHUB_OWNER>/<YOUR_REPO>:*`（対象リポに限定）

### 2-2. ロールに付与する権限（当面）

* フロント配信用：`s3:PutObject/DeleteObject/ListBucket`（対象Bucketだけ）、`cloudfront:CreateInvalidation`
* API配信用：`ecr:*`（ログイン・pushに必要な範囲）、`ecs:UpdateService`, `ecs:Describe*`
* （必要に応じてスコープをさらに最小化）

> 作成した **ロールARN** を控える：`arn:aws:iam::<ACCOUNT_ID>:role/github-oidc-deploy`

---

## 3. アプリケーション雛形

### 3-1. フロント（Vue）

```bash
# リポジトリの frontend/ に作成
pnpm create vue@latest
pnpm i
pnpm i -D vitest @vitest/ui @testing-library/vue eslint prettier
```

* `package.json` 例（抜粋）

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

* SPA の 404/403 は CloudFront 側で `/index.html` にフォールバック済み。

### 3-2. バックエンド（Spring Boot）

* `backend/` に Gradle プロジェクト。`spring-boot-starter-web` / `actuator`。
* `application.yml` に `management.endpoints.web.exposure.include=health`。
* **Dockerfile（例）**：

```dockerfile
# backend/Dockerfile
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

* ヘルスチェック：`/actuator/health`（CDKのTargetGroupで設定済み）

---

## 4. GitHub Actions（CI/CDの設定）

### 4-1. リポジトリSecrets/Variablesに登録

* `AWS_ROLE_ARN`：先ほどの OIDCロールARN
* `AWS_REGION`：`ap-northeast-1`
* `SPA_BUCKET`：`SpaBucketName`
* `SPA_DIST_ID`：`SpaDistributionId`
* `ECR_REPO`：`spring-api`（CDK出力に合わせる）
* `ECS_CLUSTER` / `ECS_SERVICE`：CDKで作成した名称（マネコン or `cdk deploy`出力で確認）

### 4-2. フロント用ワークフロー（`.github/workflows/deploy-frontend.yml`）

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
      - run: npm i -g pnpm
      - run: cd frontend && pnpm i --frozen-lockfile && pnpm run build
      - uses: aws-actions/configure-aws-credentials@v4
        with:
          role-to-assume: ${{ vars.AWS_ROLE_ARN }}
          aws-region: ${{ vars.AWS_REGION }}
      - run: aws s3 sync frontend/dist s3://${{ vars.SPA_BUCKET }} --delete
      - run: aws cloudfront create-invalidation --distribution-id ${{ vars.SPA_DIST_ID }} --paths "/*"
```

### 4-3. バックエンド用ワークフロー（`.github/workflows/deploy-backend.yml`）

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

> ここまでで、`main` に push すればフロントは S3/CloudFront、バックエンドは ECS まで自動反映。

---

## 5. 動作確認（チェックリスト）

* [ ] **フロント**：`SpaUrl` にアクセスし、ビルド内容が反映される。
* [ ] **API**：`http://<AlbDns>/actuator/health` が `{"status":"UP"}`。
* [ ] **ルーティング**：フロントから `/api/...` にアクセスして CORS/エンドポイントが機能。

  * CloudFrontのオリジン分岐 or フロント `.env` で API ベースURL 指定（例：`VITE_API_BASE=https://<AlbDns>`）
* [ ] **CI**：GitHub Actions が OIDC でロールを Assume している（長期キー未使用）。
* [ ] **ログ**：CloudWatch Logs にアプリログが流れている。

---

## 6. 次の一歩（業務寄り拡張）

* **ステージング環境**：`stg` 用のStack/パラメータを追加 → PRマージ前にE2E（Playwright）合格で自動デプロイ。
* **Secrets/SSM**：DBパス等は Secrets Manager/SSM 参照、環境変数に直接書かない。
* **Blue/Green**：`aws-codedeploy` をCDKに組込み、ECSのトラフィック切替と自動ロールバック。
* **監視**：ALB 5xx・p95レイテンシ・ECS CPU/Mem の Alarm を Slack 通知。
* **WAF**：重要APIはCloudFront/ALBの前段にWAF。
* **ドメイン/証明書**：Route53 + ACM で独自ドメイン・HTTPS化。

---

必要なら、**CloudFrontのオリジン分岐（`/*`→S3, `/api/*`→ALB）をCDKで書く例**や、**CDK側のECRイメージ参照（`fromEcrRepository`）版**にすぐ展開します。
まずは上の手順で最小構成を一度**通して動かす**のがおすすめです。
