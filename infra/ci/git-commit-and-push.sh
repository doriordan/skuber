echo "Committing and pushing to git version: $1"
git status
git branch
git add .
git commit -m $1
git push
