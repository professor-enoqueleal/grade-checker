#!/bin/sh
# DynamoDB Local for `sam local start-api` (capability serverless-backend-project):
# starts amazon/dynamodb-local on the Docker network grade-checker-local (the Lambda container reaches it as
# http://dynamodb-local:8000; this machine as http://localhost:8000) and creates the two tables.
# Data is in memory: it is lost when the container stops. Usage: sh scripts/local-dynamodb.sh [stop]
set -e

NETWORK=grade-checker-local
CONTAINER=dynamodb-local
ENDPOINT=http://localhost:8000
DATA_TABLE=${DATA_TABLE:-grade-checker-data}
EPHEMERAL_TABLE=${EPHEMERAL_TABLE:-grade-checker-ephemeral}
export AWS_ACCESS_KEY_ID=local AWS_SECRET_ACCESS_KEY=local AWS_DEFAULT_REGION=us-east-1 AWS_PAGER=""

if [ "$1" = "stop" ]; then
  docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
  echo "DynamoDB Local stopped."
  exit 0
fi

docker network inspect "$NETWORK" >/dev/null 2>&1 || docker network create "$NETWORK" >/dev/null
if ! docker ps --format '{{.Names}}' | grep -qx "$CONTAINER"; then
  docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
  docker run -d --name "$CONTAINER" --network "$NETWORK" -p 8000:8000 amazon/dynamodb-local:2.5.2 \
    -jar DynamoDBLocal.jar -inMemory -sharedDb >/dev/null
fi

# wait for the endpoint
i=0
until aws dynamodb list-tables --endpoint-url "$ENDPOINT" >/dev/null 2>&1; do
  i=$((i + 1)); [ $i -gt 30 ] && { echo "DynamoDB Local did not start"; exit 1; }
  sleep 1
done

tables=$(aws dynamodb list-tables --endpoint-url "$ENDPOINT" --query 'TableNames' --output text | tr '\t' ' ')

case " $tables " in *" $DATA_TABLE "*) ;; *)
  aws dynamodb create-table --endpoint-url "$ENDPOINT" --table-name "$DATA_TABLE" \
    --attribute-definitions AttributeName=PK,AttributeType=S AttributeName=SK,AttributeType=S \
    --key-schema AttributeName=PK,KeyType=HASH AttributeName=SK,KeyType=RANGE \
    --billing-mode PAY_PER_REQUEST >/dev/null ;;
esac

case " $tables " in *" $EPHEMERAL_TABLE "*) ;; *)
  aws dynamodb create-table --endpoint-url "$ENDPOINT" --table-name "$EPHEMERAL_TABLE" \
    --attribute-definitions AttributeName=PK,AttributeType=S \
    --key-schema AttributeName=PK,KeyType=HASH \
    --billing-mode PAY_PER_REQUEST >/dev/null
  aws dynamodb update-time-to-live --endpoint-url "$ENDPOINT" --table-name "$EPHEMERAL_TABLE" \
    --time-to-live-specification Enabled=true,AttributeName=expiresAt >/dev/null ;;
esac

echo "DynamoDB Local on $ENDPOINT (network $NETWORK): $DATA_TABLE, $EPHEMERAL_TABLE"
