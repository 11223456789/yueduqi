import os
import re

def update_package_declarations(root_dir):
    """更新包声明和导入语句"""
    for root, dirs, files in os.walk(root_dir):
        for file in files:
            if file.endswith('.java') or file.endswith('.kt'):
                file_path = os.path.join(root, file)
                try:
                    with open(file_path, 'r', encoding='utf-8') as f:
                        content = f.read()
                    
                    # 更新包声明
                    content = re.sub(r'package\s+io\.legado\.app\b', 'package com.peiyu.reader', content)
                    
                    # 更新导入语句
                    content = re.sub(r'import\s+io\.legado\.app\b', 'import com.peiyu.reader', content)
                    
                    with open(file_path, 'w', encoding='utf-8') as f:
                        f.write(content)
                    
                    print(f"已更新: {file_path}")
                except Exception as e:
                    print(f"处理文件 {file_path} 时出错: {e}")

if __name__ == "__main__":
    # 更新Java/Kotlin文件
    update_package_declarations("app/src/main/java/com/peiyu/reader")
    print("包声明和导入语句更新完成!")