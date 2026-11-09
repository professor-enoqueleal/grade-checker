#!/bin/sh
# Seed of the groups (capability dynamodb-data): creates the groups of src/main/resources/seed-groups.json that do not
# exist yet. Idempotent. Default: DynamoDB Local (http://localhost:8000). For AWS: DYNAMODB_ENDPOINT= DATA_TABLE=<table>
# and the usual AWS credentials. Usage (from applications/serverless/lambda): sh scripts/seed.sh
set -e
cd "$(dirname "$0")/.."

export DATA_TABLE=${DATA_TABLE:-grade-checker-data}
export EPHEMERAL_TABLE=${EPHEMERAL_TABLE:-grade-checker-ephemeral}
export DYNAMODB_ENDPOINT=${DYNAMODB_ENDPOINT-http://localhost:8000}
if [ -n "$DYNAMODB_ENDPOINT" ]; then
  export AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY_ID:-local} AWS_SECRET_ACCESS_KEY=${AWS_SECRET_ACCESS_KEY:-local}
fi
export AWS_REGION=${AWS_REGION:-us-east-1}

mvn -q compile exec:java -Dexec.mainClass=br.com.gradechecker.tools.Seed
