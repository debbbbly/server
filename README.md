# server
Debbly server



name: Build, Push, and Deploy Docker to EC2

on:
push:
branches: [ main ]
workflow_dispatch:

env:
AWS_REGION: us-west-2
ECR_REPOSITORY: debbly-server
# Replace with your actual EC2 Instance ID
EC2_INSTANCE_ID: i-0e3363d7ee57488ab

    jobs:
      build-and-deploy:
        runs-on: ubuntu-latest
        
        steps:
          - name: Checkout code
            uses: actions/checkout@v4

          - name: Configure AWS credentials
            uses: aws-actions/configure-aws-credentials@v1
            with:
              aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
              aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
              aws-region: ${{ env.AWS_REGION }}

          - name: Login to Amazon ECR
            id: login-ecr
            uses: aws-actions/amazon-ecr-login@v1

          - name: Build, tag, and push Docker image to ECR
            env:
              ECR_REGISTRY: ${{ steps.login-ecr.outputs.registry }}
              IMAGE_TAG: latest
            run: |
      docker build -t $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG .
      docker push $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG

      - name: Send SSM command to EC2 to update and restart Docker container
      run: |
      aws ssm send-command \
      --instance-ids "${{ env.EC2_INSTANCE_ID }}" \
      --document-name "AWS-RunShellScript" \
      --comment "Pull latest Docker image and restart service" \
      --parameters commands="sudo systemctl stop debbly-server.service && sudo systemctl start debbly-server.service" \
      --region ${{ env.AWS_REGION }}