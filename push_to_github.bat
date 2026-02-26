@echo off
echo 正在推送佩宇书屋项目到GitHub仓库...

:: 初始化Git仓库（如果不存在）
if not exist .git (
    git init
)

:: 添加所有文件
git add .

:: 提交更改
git commit -m "初始化佩宇书屋项目 - 基于开源阅读器定制，采用渊墨金阁UI系统"

:: 添加远程仓库
git remote add origin https://github.com/11223456789/yueduqi.git

:: 推送到main分支
git branch -M main
git push -u origin main

echo 项目已成功推送到GitHub仓库！
echo 访问: https://github.com/11223456789/yueduqi
pause