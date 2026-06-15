#!/bin/bash

ROLE_NAME="JenkinsMongoPITRRole"
PROFILE_NAME="JenkinsMongoPITRInstanceProfile"
BUCKET_NAME="my-company-mongo-pitr-backups"

echo "1. Creating EC2 Trust Policy..."
cat << 'EOF' > trust-policy.json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Service": "ec2.amazonaws.com"
      },
      "Action": "sts:AssumeRole"
    }
  ]
}
EOF

echo "2. Creating IAM Role ($ROLE_NAME)..."
aws iam create-role \
    --role-name "$ROLE_NAME" \
    --assume-role-policy-document file://trust-policy.json > /dev/null

echo "3. Applying S3 PITR Permissions Policy..."
cat << EOF > s3-pitr-policy.json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "s3:ListBucket",
                "s3:CreateBucket",
                "s3:GetBucketLocation"
            ],
            "Resource": "arn:aws:s3:::$BUCKET_NAME"
        },
        {
            "Effect": "Allow",
            "Action": [
                "s3:PutObject",
                "s3:GetObject",
                "s3:DeleteObject"
            ],
            "Resource": "arn:aws:s3:::$BUCKET_NAME/*"
        }
    ]
}
EOF

aws iam put-role-policy \
    --role-name "$ROLE_NAME" \
    --policy-name "S3PITRBackupRestoreAccess" \
    --policy-document file://s3-pitr-policy.json

echo "4. Creating Instance Profile ($PROFILE_NAME)..."
aws iam create-instance-profile --instance-profile-name "$PROFILE_NAME" 2>/dev/null || true

echo "5. Attaching Role to Instance Profile..."
aws iam add-role-to-instance-profile \
    --instance-profile-name "$PROFILE_NAME" \
    --role-name "$ROLE_NAME" 2>/dev/null || true

rm -f trust-policy.json s3-pitr-policy.json

echo "=========================================================="
echo "SUCCESS! The IAM Role and Instance Profile are ready."
echo "You can now attach this role to your Jenkins EC2 instance:"
echo "aws ec2 associate-iam-instance-profile --instance-id <YOUR_JENKINS_INSTANCE_ID> --iam-instance-profile Name=$PROFILE_NAME"
echo "=========================================================="