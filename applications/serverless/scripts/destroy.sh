#!/bin/sh
# Removal of the serverless stack (capability aws-stack): empties the SPA bucket (CloudFormation does not delete a
# bucket with files) and deletes the stack, waiting for the end. CloudFront takes 5 to 15 minutes.
# The data table is kept only when the stack was deployed with DATA_TABLE_DELETION_POLICY=Retain.
# The SSM parameter of the client secret is not part of the stack and is kept.
#
#   sh scripts/destroy.sh
set -eu

STACK=${STACK_NAME:-grade-checker-serverless}
REGION=${AWS_REGION:-us-east-1}

if ! aws cloudformation describe-stacks --region "$REGION" --stack-name "$STACK" > /dev/null 2>&1; then
  echo "Stack $STACK does not exist in $REGION: nothing to delete."
  exit 0
fi

BUCKET=$(aws cloudformation describe-stacks --region "$REGION" --stack-name "$STACK" \
  --query "Stacks[0].Outputs[?OutputKey=='SpaBucketName'].OutputValue" --output text)
if [ -n "$BUCKET" ] && [ "$BUCKET" != "None" ] && aws s3api head-bucket --bucket "$BUCKET" 2> /dev/null; then
  echo "==> 1/2 emptying s3://$BUCKET"
  aws s3 rm "s3://$BUCKET" --recursive --region "$REGION" --only-show-errors
else
  echo "==> 1/2 no SPA bucket to empty"
fi

echo "==> 2/2 deleting stack $STACK (CloudFront takes a while)"
aws cloudformation delete-stack --region "$REGION" --stack-name "$STACK"
aws cloudformation wait stack-delete-complete --region "$REGION" --stack-name "$STACK"
echo "Stack $STACK deleted."
