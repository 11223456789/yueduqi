# 佩宇书屋 - 精美阅读软件

<div align="center">
<img width="125" height="125" src="app/src/main/res/drawable/ic_launcher_peiyu.xml" alt="peiyu reader"/>  
  
佩宇书屋 / Peiyu Reader
<br>
基于开源项目定制的高质量阅读器
<br>
采用「渊墨金阁」UI系统，提供极致阅读体验
</div>

## 主要功能

1. 自定义书源，支持搜索及发现功能
2. 多种书架布局：列表、网格自由切换
3. 支持本地TXT、EPUB阅读
4. 高度自定义阅读界面：字体、颜色、背景、行距等
5. 多种翻页模式：覆盖、仿真、滑动、滚动等
6. 支持替换净化，去除广告
7. 订阅功能，可订阅想看的内容
8. 精美「渊墨金阁」UI主题系统

## 界面预览

佩宇书屋采用独特的「渊墨金阁」设计风格，融合传统与现代元素，为用户带来沉浸式的阅读体验。

## 下载与安装

最新版本可在 [GitHub Releases](https://github.com/11223456789/yueduqi/releases) 页面下载。

## 开发

本项目基于开源阅读器进行二次开发，专注于UI美化和用户体验优化。

### 构建

```bash
# 构建发布版本
./gradlew assembleRelease

# 构建调试版本
./gradlew assembleDebug
```

## GitHub Actions

项目已配置GitHub Actions自动构建流程：
- 推送至`master`分支自动触发构建
- 生成APK并上传到Release
- 支持自动签名和发布

## 免责声明

本软件仅供学习交流使用，请勿用于商业用途。软件不提供任何内容，所有内容需用户自行添加。

## 开源协议

本项目遵循原开源项目的协议。