// ESLint flat config：官方推荐组合（@eslint/js + typescript-eslint + eslint-plugin-vue），
// 末尾接 eslint-config-prettier 关掉所有与 Prettier 冲突的格式类规则——格式只归 Prettier 管，
// ESLint 只管代码质量（未使用变量、错误的 Vue 用法等）。
import js from '@eslint/js'
import prettier from 'eslint-config-prettier'
import vue from 'eslint-plugin-vue'
import globals from 'globals'
import tseslint from 'typescript-eslint'

export default tseslint.config(
  { ignores: ['dist/**', 'node_modules/**', 'coverage/**'] },
  {
    files: ['**/*.{ts,vue,js}'],
    extends: [
      js.configs.recommended,
      ...tseslint.configs.recommended,
      ...vue.configs['flat/recommended'],
    ],
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: globals.browser,
      // .vue 文件由 vue-eslint-parser 解析模板，<script lang="ts"> 部分再交给 TS 解析器
      parserOptions: { parser: tseslint.parser },
    },
    rules: {
      // 组件按目录归类（components/ views/），单词名（App、Modal）不构成与原生元素的歧义
      'vue/multi-word-component-names': 'off',
      // 允许以 _ 开头的形参/变量表示「有意忽略」
      '@typescript-eslint/no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_' },
      ],
    },
  },
  prettier,
)
