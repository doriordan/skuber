#!/bin/bash

# Create a new branch
branch_name=$(openssl rand -base64 10 | tr -d '/+=')
git checkout -b $branch_name
git push --set-upstream origin $branch_name
git status
git branch
git config --global user.email "hagay3@gmail.com"
git config --global user.name "Hagai Hillel"
git add version.sbt
git commit -m "new version"
git push

# Define base branch (replace with appropriate branch name)
base_branch="master"

# Title and body for the pull request (modify as needed)
title="Automated Pull Request - upgrading skuber version"
body="This pull request is automatically created from a script."

# Generate API URL
url_create="https://api.github.com/repos/hagay3/skuber/pulls"

# Set personal access token (replace with your token)
token=$GITHUB_TOKEN

# Prepare request body for creation
payload_create="{\"title\": \"$title\", \"body\": \"$body\", \"head\": \"$branch_name\", \"base\": \"$base_branch\"}"
echo $payload_create
# Send API request to create pull request
PULL_NUMBER=$(jq '.number' <<< $(curl -s -X POST -H "Authorization: Bearer $token" -d "$payload_create" "$url_create"))

# Check for successful creation response
if [ $? -eq 0 ]; then
  echo "Pull request created successfully!"

  url_merge="https://api.github.com/repos/hagay3/skuber/pulls/$PULL_NUMBER/merge"
  # Prepare request body for merging
  payload_merge="{\"commit_title\": \"new version\", \"commit_message\": \"new version\", \"merge_method\": \"squash\"}"

  # Send API request to merge pull request
  curl -X PUT -H "Authorization: Bearer $token" -d "$payload_merge" "$url_merge"

  if [ $? -eq 0 ]; then
    echo "Pull request merged successfully!"
  else
    echo "Error merging pull request."
  fi
else
  echo "Error creating pull request."
fi