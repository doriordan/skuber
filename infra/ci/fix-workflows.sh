#!/bin/bash

sed -i ''  -E 's/.*Check that workflows are up to date//' .github/workflows/ci.yml
sed -i ''  -E 's/.*githubWorkflowCheck//' .github/workflows/ci.yml
sed -i ''  -E '1,/branches.*/s/branches.*//' .github/workflows/ci.yml
sed -i ''  -E "s/''/\"/g" .github/workflows/ci.yml
sed -i ''  '35d' .github/workflows/ci.yml
