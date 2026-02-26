#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
修复剩余的旧项目引用
"""

import os
import re
import glob

def replace_in_file(file_path, replacements):
    """在文件中替换文本"""
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        original_content = content
        for old_text, new_text in replacements.items():
            content = content.replace(old_text, new_text)
        
        if content != original_content:
            with open(file_path, 'w', encoding='utf-8') as f:
                f.write(content)
            print(f"✅ 已更新: {file_path}")
            return True
        return False
    except Exception as e:
        print(f"❌ 处理文件 {file_path} 时出错: {e}")
        return False

def main():
    """主函数"""
    print("开始修复剩余的旧项目引用...\n")
    
    # 定义替换规则
    replacements = {
        'io/legado/app': 'com/peiyu/reader',
        'gedoor/legado': '11223456789/yueduqi',
        'io.legado.app': 'com.peiyu.reader',
        'io.legado.play': 'com.peiyu.reader',
        'legado://': 'peiyu://',
        'legado/releases': 'yueduqi/releases',
        'github.com/gedoor': 'github.com/11223456789',
    }
    
    # 需要检查的文件类型
    file_patterns = [
        '**/*.md',
        '**/*.json',
        '**/*.xml',
        '**/*.gradle',
        '**/*.properties',
        '**/*.kt',
        '**/*.java',
        '**/*.yml',
        '**/*.yaml',
        '**/*.ts',
        '**/*.js',
    ]
    
    updated_files = []
    
    # 遍历所有文件
    for pattern in file_patterns:
        for file_path in glob.glob(pattern, recursive=True):
            if 'node_modules' in file_path or '.git' in file_path:
                continue
                
            if replace_in_file(file_path, replacements):
                updated_files.append(file_path)
    
    print(f"\n{'='*50}")
    print(f"总共更新了 {len(updated_files)} 个文件")
    
    if updated_files:
        print("更新的文件列表:")
        for file_path in updated_files[:10]:  # 只显示前10个
            print(f"  - {file_path}")
        if len(updated_files) > 10:
            print(f"  ... 还有 {len(updated_files) - 10} 个文件")
    
    print("\n✅ 剩余引用修复完成！")

if __name__ == "__main__":
    main()