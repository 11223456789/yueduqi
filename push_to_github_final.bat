@echo off
echo 正在推送佩宇书屋项目到GitHub仓库...

:: 切换到项目目录
cd /d %~dp0

:: 初始化Git仓库（如果不存在）
if not exist .git (
    git init
)

:: 检查是否有远程仓库
git remote get-url origin >nul 2>&1
if %errorlevel% neq 0 (
    echo 添加远程仓库...
    git remote add origin https://github.com/11223456789/yueduqi.git
)

:: 添加所有文件
echo 添加所有更改的文件...
git add .

:: 检查是否有更改需要提交
git diff --cached --quiet
if %errorlevel% neq 0 (
    echo 提交更改...
    git commit -m "初始化佩宇书屋项目 - 基于开源阅读器定制，采用渊墨金阁UI系统

主要更改：
- 重命名应用为「佩宇书屋」
- 更新包名为 com.peiyu.reader
- 添加「渊墨金阁」UI系统
- 创建「PY书屋金印」SVG图标
- 配置GitHub Actions自动构建
- 更新所有文档和引用"
    
    :: 推送到main分支
    echo 推送到GitHub仓库...
    git branch -M main
    git push -u origin main
    
    echo.
    echo ✅ 项目已成功推送到GitHub仓库！
    echo 访问: https://github.com/11223456789/yueduqi
) else (
    echo 没有需要提交的更改。
)

echo.
echo 触发GitHub Actions构建...
echo 请访问 https://github.com/11223456789/yueduqi/actions 查看构建状态
pause