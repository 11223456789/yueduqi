#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
测试佩宇书屋项目的更改
"""

import os
import re
import subprocess
import sys

def test_package_names():
    """测试包名是否正确"""
    print("测试包名...")
    
    # 检查build.gradle
    with open('app/build.gradle', 'r', encoding='utf-8') as f:
        content = f.read()
        if 'com.peiyu.reader' not in content:
            print("❌ app/build.gradle 中未找到 com.peiyu.reader")
            return False
        print("✅ app/build.gradle 包名正确")
    
    # 检查AndroidManifest.xml
    with open('app/src/main/AndroidManifest.xml', 'r', encoding='utf-8') as f:
        content = f.read()
        if 'com.peiyu.reader' not in content:
            print("❌ AndroidManifest.xml 中未找到 com.peiyu.reader")
            return False
        print("✅ AndroidManifest.xml 包名正确")
    
    return True

def test_strings():
    """测试字符串资源"""
    print("\n测试字符串资源...")
    
    with open('app/src/main/res/values/strings.xml', 'r', encoding='utf-8') as f:
        content = f.read()
        
        # 检查应用名称
        if '<string name="app_name">佩宇书屋</string>' not in content:
            print("❌ 应用名称未正确设置")
            return False
        print("✅ 应用名称正确")
        
        # 检查关于描述
        if '佩宇书屋是一款精美的阅读软件' not in content:
            print("❌ 关于描述未正确更新")
            return False
        print("✅ 关于描述正确")
    
    return True

def test_package_structure():
    """测试包结构"""
    print("\n测试包结构...")
    
    # 检查新的包目录是否存在
    if not os.path.exists('app/src/main/java/com/peiyu/reader'):
        print("❌ 新的包目录不存在")
        return False
    print("✅ 新的包目录存在")
    
    # 检查旧的包目录是否被删除
    if os.path.exists('app/src/main/java/io/legado/app'):
        print("❌ 旧的包目录仍然存在")
        return False
    print("✅ 旧的包目录已删除")
    
    return True

def test_github_actions():
    """测试GitHub Actions工作流"""
    print("\n测试GitHub Actions工作流...")
    
    # 检查build.yml是否存在
    if not os.path.exists('.github/workflows/build.yml'):
        print("❌ build.yml 不存在")
        return False
    print("✅ build.yml 存在")
    
    # 检查release.yml是否存在
    if not os.path.exists('.github/workflows/release.yml'):
        print("❌ release.yml 不存在")
        return False
    print("✅ release.yml 存在")
    
    # 检查工作流内容
    with open('.github/workflows/build.yml', 'r', encoding='utf-8') as f:
        content = f.read()
        if 'peiyu-reader' not in content:
            print("❌ build.yml 中未找到 peiyu-reader")
            return False
        print("✅ build.yml 内容正确")
    
    return True

def test_ui_resources():
    """测试UI资源"""
    print("\n测试UI资源...")
    
    # 检查主题配置
    if not os.path.exists('app/src/main/assets/defaultData/themeConfig.json'):
        print("❌ 主题配置文件不存在")
        return False
    print("✅ 主题配置文件存在")
    
    # 检查图标
    if not os.path.exists('app/src/main/res/drawable/ic_launcher_peiyu.xml'):
        print("❌ 应用图标不存在")
        return False
    print("✅ 应用图标存在")
    
    return True

def test_readme():
    """测试README"""
    print("\n测试README...")
    
    if not os.path.exists('README.md'):
        print("❌ README.md 不存在")
        return False
    
    with open('README.md', 'r', encoding='utf-8') as f:
        content = f.read()
        if '佩宇书屋' not in content:
            print("❌ README.md 中未找到 佩宇书屋")
            return False
        print("✅ README.md 内容正确")
    
    return True

def main():
    """主测试函数"""
    print("开始测试佩宇书屋项目更改...\n")
    
    tests = [
        test_package_names,
        test_strings,
        test_package_structure,
        test_github_actions,
        test_ui_resources,
        test_readme
    ]
    
    passed = 0
    total = len(tests)
    
    for test in tests:
        if test():
            passed += 1
        else:
            print(f"\n❌ 测试 {test.__name__} 失败")
    
    print(f"\n{'='*50}")
    print(f"测试结果: {passed}/{total} 通过")
    
    if passed == total:
        print("✅ 所有测试通过！项目已准备好推送到GitHub。")
        return True
    else:
        print("❌ 部分测试未通过，请检查问题。")
        return False

if __name__ == "__main__":
    success = main()
    sys.exit(0 if success else 1)