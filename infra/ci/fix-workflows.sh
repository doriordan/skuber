#!/bin/bash
# Until the following issue is been fixed, this bash file will fix the ci pipeline.
# https://github.com/djspiewak/sbt-github-actions/issues/108

sed -i ''  -E 's/.*Check that workflows are up to date//' .github/workflows/ci.yml
sed -i ''  -E 's/.*githubWorkflowCheck//' .github/workflows/ci.yml
sed -i ''  -E 's/minikubeversion/minikube version/' .github/workflows/ci.yml
sed -i ''  -E 's/kubernetesversion/kubernetes version/' .github/workflows/ci.yml
sed -i ''  -E 's/githubtoken/github token/' .github/workflows/ci.yml
sed -i ''  -E 's/startargs/start args/' .github/workflows/ci.yml
sed -i ''  -E '1,/branches.*/s/branches.*//' .github/workflows/ci.yml
sed -i ''  -E "s/''/\"/g" .github/workflows/ci.yml
