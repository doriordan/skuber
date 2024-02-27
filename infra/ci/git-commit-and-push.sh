#!/bin/bash

git config --global user.email "hagay3@gmail.com"
git config --global user.name "Hagai Hillel"
git checkout master
git status
git branch
git add version.sbt
git commit -m "new version"
git push