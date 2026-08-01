<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { updateMyProfile, UnauthorizedError } from '../api'
import { currentUser, gotoLogin } from '../auth'
import { locale, setLocale, t, type AppLocale } from '../i18n'
import { showToast } from '../toast'

/** 昵称长度上限，与后端 UserService.NICKNAME_MAX_LENGTH 一致 */
const NICKNAME_MAX = 30

const nickname = ref('')
const prefLocale = ref<AppLocale>(locale)
/** 进页面时的初值，用于判断有没有改动（没改就禁用保存） */
const savedNickname = ref('')
const savedLocale = ref<AppLocale>(locale)

const saving = ref(false)
const nicknameError = ref('')

const email = computed(() => currentUser.value?.email ?? '')

const dirty = computed(
  () => nickname.value.trim() !== savedNickname.value || prefLocale.value !== savedLocale.value,
)

onMounted(() => {
  // 登录态在挂载前已确定（main.ts 先 loadCurrentUser 再挂载），这里为 null 即游客
  if (!currentUser.value) {
    gotoLogin()
    return
  }
  nickname.value = currentUser.value.nickname ?? ''
  savedNickname.value = nickname.value
  // 语言初值以服务端偏好为准；服务端没存过则用本次会话语言
  const server = currentUser.value.locale
  prefLocale.value = server === 'zh-CN' || server === 'en-US' ? server : locale
  savedLocale.value = prefLocale.value
})

async function onSave() {
  const name = nickname.value.trim()
  if (!name) {
    nicknameError.value = t('settings.nicknameRequired')
    return
  }
  if (name.length > NICKNAME_MAX) {
    nicknameError.value = t('settings.nicknameTooLong', { max: NICKNAME_MAX })
    return
  }
  nicknameError.value = ''

  saving.value = true
  try {
    await updateMyProfile(name, prefLocale.value)
  } catch (e) {
    if (e instanceof UnauthorizedError) {
      gotoLogin()
      return
    }
    showToast('error', e instanceof Error ? e.message : t('api.requestFailed'))
    return
  } finally {
    saving.value = false
  }

  // 同步页头显示的昵称与后续进页面的初值
  if (currentUser.value) {
    currentUser.value.nickname = name
    currentUser.value.locale = prefLocale.value
  }
  nickname.value = name
  savedNickname.value = name

  if (prefLocale.value !== locale) {
    // 语言改了：写本地偏好并整页刷新，让界面与后端文案一起按新语言重取（刷新本身即反馈）
    setLocale(prefLocale.value)
    return
  }
  savedLocale.value = prefLocale.value
  showToast('success', t('settings.saved'))
}
</script>

<template>
  <main class="page">
    <h2 class="title">{{ $t('settings.title') }}</h2>

    <section class="card">
      <div class="field">
        <label class="label">{{ $t('settings.email') }}</label>
        <p class="readonly">{{ email }}</p>
        <p class="help">{{ $t('settings.emailHint') }}</p>
      </div>

      <div class="field">
        <label class="label" for="nickname">{{ $t('settings.nickname') }}</label>
        <input
          id="nickname"
          v-model="nickname"
          class="input"
          type="text"
          :maxlength="NICKNAME_MAX"
          :placeholder="$t('settings.nicknamePlaceholder')"
        />
        <p v-if="nicknameError" class="help error">{{ nicknameError }}</p>
      </div>

      <div class="field">
        <label class="label">{{ $t('settings.language') }}</label>
        <div class="lang-group">
          <button
            type="button"
            class="lang-option"
            :class="{ active: prefLocale === 'zh-CN' }"
            @click="prefLocale = 'zh-CN'"
          >
            中文
          </button>
          <button
            type="button"
            class="lang-option"
            :class="{ active: prefLocale === 'en-US' }"
            @click="prefLocale = 'en-US'"
          >
            English
          </button>
        </div>
        <p class="help">{{ $t('settings.languageHint') }}</p>
      </div>

      <button type="button" class="save-btn" :disabled="!dirty || saving" @click="onSave">
        {{ saving ? $t('settings.saving') : $t('settings.save') }}
      </button>
    </section>
  </main>
</template>

<style scoped>
.page {
  max-width: 720px;
  margin: 0 auto;
  padding: 24px 32px 48px;
}

.title {
  font-size: 20px;
  color: var(--color-ink);
  margin: 8px 0 16px;
}

.card {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: var(--color-bg);
  padding: 20px;
}

.field {
  margin-bottom: 20px;
}

.label {
  display: block;
  font-size: 13px;
  color: var(--color-ink-secondary);
  margin-bottom: 6px;
}

.readonly {
  font-size: 14px;
  color: var(--color-ink);
}

.help {
  margin-top: 6px;
  font-size: 12px;
  color: var(--color-ink-secondary);
}

.help.error {
  color: #b91c1c;
}

.input {
  width: 100%;
  max-width: 320px;
  padding: 8px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-button);
  background: var(--color-bg);
  color: var(--color-ink);
  font-family: inherit;
  font-size: 14px;
}

.input:focus {
  outline: none;
  border-color: var(--color-brand);
}

.lang-group {
  display: flex;
  gap: 8px;
}

.lang-option {
  padding: 6px 16px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  background: transparent;
  color: var(--color-ink);
  font-family: inherit;
  font-size: 13px;
  cursor: pointer;
}

.lang-option:hover {
  border-color: var(--color-brand);
}

.lang-option.active {
  border-color: var(--color-brand);
  background: var(--color-brand);
  color: #ffffff;
}

.save-btn {
  padding: 8px 24px;
  border: none;
  border-radius: var(--radius-pill);
  background: var(--color-brand);
  color: #ffffff;
  font-family: inherit;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
}

.save-btn:hover:not(:disabled) {
  background: var(--color-brand-deep);
}

.save-btn:disabled {
  background: var(--color-border);
  color: var(--color-ink-secondary);
  cursor: not-allowed;
}
</style>
