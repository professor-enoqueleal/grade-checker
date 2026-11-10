#!/bin/sh
# Deploy of the whole serverless stack (capability serverless-aws-stack): API, Lambda, tables, SPA bucket, CloudFront and tags.
# The client secret is never read here: it stays in the SSM SecureString created once by the administrator.
#
#   GITHUB_CLIENT_ID=Iv1.xxx GRADE_CHECKER_ADMINS=login sh scripts/deploy.sh
#
# Variables: GITHUB_CLIENT_ID (required), GRADE_CHECKER_ADMINS, ENVIRONMENT (default test), BUDGET_EMAIL,
# BUDGET_AMOUNT, MAX_CONCURRENCY, DATA_TABLE_DELETION_POLICY (Delete or Retain), STACK_NAME, AWS_REGION,
# GITHUB_CLIENT_SECRET_PARAMETER, SKIP_BUILD (1 = deploy the build already in .aws-sam/build and spa/dist, as the
# workflow serverless-deploy does with the build of a Release). Needs the AWS CLI, the SAM CLI, Maven (seed), Node 22
# (SPA build) and the library grade-checker-core in ~/.m2 (published version or mvn -f ../library-core/pom.xml install).
set -eu
cd "$(dirname "$0")/.."

STACK=${STACK_NAME:-grade-checker-serverless}
REGION=${AWS_REGION:-us-east-1}
SECRET_PARAMETER=${GITHUB_CLIENT_SECRET_PARAMETER:-/grade-checker/github-client-secret}
ENVIRONMENT=${ENVIRONMENT:-test}
# APP_CLIENT_ID: same value, for GitHub Actions (variables with the prefix GITHUB_ are reserved there)
GITHUB_CLIENT_ID=${GITHUB_CLIENT_ID:-${APP_CLIENT_ID:-}}
: "${GITHUB_CLIENT_ID:?set GITHUB_CLIENT_ID to the client ID of the production GitHub App}"

output() {
  aws cloudformation describe-stacks --region "$REGION" --stack-name "$STACK" \
    --query "Stacks[0].Outputs[?OutputKey=='$1'].OutputValue" --output text
}

echo "==> 1/6 SSM parameter $SECRET_PARAMETER (only its existence is checked, never its value)"
FOUND=$(aws ssm describe-parameters --region "$REGION" \
  --parameter-filters "Key=Name,Values=$SECRET_PARAMETER" --query 'length(Parameters)' --output text)
if [ "$FOUND" != "1" ]; then
  echo "The SSM parameter $SECRET_PARAMETER does not exist in $REGION. Create it first (SecureString with the" >&2
  echo "client secret of the production GitHub App); see 'Publicar na AWS' in applications/serverless/README.md." >&2
  exit 1
fi

if [ "${SKIP_BUILD:-}" = "1" ]; then
  echo "==> 2/6 build already done (SKIP_BUILD=1): .aws-sam/build and spa/dist"
  [ -f .aws-sam/build/template.yaml ] && [ -f spa/dist/index.html ] || {
    echo "SKIP_BUILD=1 needs .aws-sam/build/template.yaml and spa/dist/index.html" >&2; exit 1; }
else
  echo "==> 2/6 sam build"
  sam build
fi

echo "==> 3/6 sam deploy (stack $STACK, $REGION, environment $ENVIRONMENT)"
# every parameter is always passed: sam deploy keeps the previous value of a parameter that is left out, so an
# omitted variable (for example, no administrators) would silently keep the old setting. A file accepts empty values
# and commas, which the Key=Value form of --parameter-overrides does not. Nothing secret goes in it.
PARAMS_DIR=$(mktemp -d "${TMPDIR:-/tmp}/grade-checker-deploy.XXXXXX")
trap 'rm -rf "$PARAMS_DIR"' EXIT
PARAMS="$PARAMS_DIR/parameters.yaml"            # sam deploy only accepts a .yaml/.yml/.json file
param() { printf "%s: '%s'\n" "$1" "$(printf '%s' "$2" | sed "s/'/''/g")" >> "$PARAMS"; }
param GitHubClientId "$GITHUB_CLIENT_ID"
param GitHubClientSecretParameter "$SECRET_PARAMETER"
param Environment "$ENVIRONMENT"
param GradeCheckerAdmins "${GRADE_CHECKER_ADMINS:-}"
param BudgetEmail "${BUDGET_EMAIL:-}"
param BudgetAmount "${BUDGET_AMOUNT:-5}"
param MaxConcurrency "${MAX_CONCURRENCY:-0}"
param DataTableDeletionPolicy "${DATA_TABLE_DELETION_POLICY:-Delete}"
# hash of the values above: any change publishes a new version of the function (template, AutoPublishCodeSha256)
param ConfigurationHash "$(shasum -a 256 "$PARAMS" | cut -c1-16)"
# stack tags: CloudFormation copies them to every resource that supports tags (reports and cost allocation)
sam deploy --stack-name "$STACK" --region "$REGION" \
  --parameter-overrides "file://$PARAMS" \
  --tags Application=grade-checker Component=serverless Environment="$ENVIRONMENT" \
         ManagedBy=cloudformation Repository=professor-enoqueleal/grade-checker

BUCKET=$(output SpaBucketName)
DISTRIBUTION=$(output DistributionId)
DATA=$(output DataTableName)
EPHEMERAL=$(output EphemeralTableName)

echo "==> 4/6 SPA: build and upload to s3://$BUCKET"
[ "${SKIP_BUILD:-}" = "1" ] || (cd spa && npm ci --no-audit --no-fund && npm run build)
# 1. new hashed assets first (old ones stay, for pages still open); 2. index.html and the other files, never cached
# by the browser; 3. only then the old assets are removed
aws s3 sync spa/dist/assets "s3://$BUCKET/assets" --region "$REGION" \
  --cache-control "public, max-age=31536000, immutable"
aws s3 sync spa/dist "s3://$BUCKET" --region "$REGION" --delete --exclude "assets/*" \
  --cache-control "no-cache"
aws s3 sync spa/dist/assets "s3://$BUCKET/assets" --region "$REGION" --delete \
  --cache-control "public, max-age=31536000, immutable"

echo "==> 5/6 CloudFront invalidation /*"
aws cloudfront create-invalidation --distribution-id "$DISTRIBUTION" --paths "/*" \
  --query 'Invalidation.Id' --output text

echo "==> 6/6 seed of the groups (idempotent)"
DYNAMODB_ENDPOINT= DATA_TABLE="$DATA" EPHEMERAL_TABLE="$EPHEMERAL" AWS_REGION="$REGION" sh lambda/scripts/seed.sh

echo
echo "Grade Checker: $(output AppUrl)"
echo "Callback URL of the production GitHub App (first in the list): $(output CallbackUrl)"
