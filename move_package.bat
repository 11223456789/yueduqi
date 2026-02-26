@echo off
echo 正在迁移包名从 io.legado.app 到 com.peiyu.reader...

:: 创建新的包目录结构
mkdir "app\src\main\java\com\peiyu\reader"
mkdir "app\src\main\java\com\peiyu\reader\ui"
mkdir "app\src\main\java\com\peiyu\reader\data"
mkdir "app\src\main\java\com\peiyu\reader\help"
mkdir "app\src\main\java\com\peiyu\reader\model"
mkdir "app\src\main\java\com\peiyu\reader\service"
mkdir "app\src\main\java\com\peiyu\reader\utils"
mkdir "app\src\main\java\com\peiyu\reader\lib"

:: 复制所有源文件到新的包结构
xcopy "app\src\main\java\io\legado\app\*" "app\src\main\java\com\peiyu\reader\" /E /Y

:: 更新所有Java/Kotlin文件中的包声明
echo 正在更新文件中的包声明...
powershell -Command "Get-ChildItem -Path 'app\src\main\java\com\peiyu\reader' -Recurse -Include *.java,*.kt | ForEach-Object { (Get-Content $_.FullName) -replace 'package io\.legado\.app', 'package com.peiyu.reader' | Set-Content $_.FullName }"

:: 更新所有导入语句
echo 正在更新导入语句...
powershell -Command "Get-ChildItem -Path 'app\src\main\java\com\peiyu\reader' -Recurse -Include *.java,*.kt | ForEach-Object { (Get-Content $_.FullName) -replace 'import io\.legado\.app', 'import com.peiyu.reader' | Set-Content $_.FullName }"

:: 删除旧的包结构
echo 正在清理旧的包结构...
rmdir /s /q "app\src\main\java\io"

echo 包名迁移完成！
pause