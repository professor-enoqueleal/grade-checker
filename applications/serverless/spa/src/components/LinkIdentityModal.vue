<script setup lang="ts">
import { ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { api } from '@/api';
import { errorMessage } from '@/api/client';
import { useModal } from '@/composables/useModal';

/** "Link to student" (openspec capability identity-aliases): commits of an identity without account count for a student. */
const props = defineProps<{ groupId: number; students: string[] }>();
const emit = defineEmits<{ linked: [] }>();

const { element, show, hide } = useModal();
const { t } = useI18n();
const identityKey = ref('');
const label = ref('');
const login = ref('');
// computed on render, so it follows a change of language
const error = ref<(() => string) | null>(null);
const saving = ref(false);

function open(key: string, identityLabel: string) {
  identityKey.value = key;
  label.value = identityLabel;
  login.value = props.students[0] ?? '';
  error.value = props.students.length === 0 ? () => t('linkIdentity.noStudents') : null;
  show();
}

async function confirm() {
  saving.value = true;
  try {
    await api.linkIdentity(props.groupId, identityKey.value, login.value);
    hide();
    emit('linked');
  } catch (e) {
    error.value = () => errorMessage(e, t('linkIdentity.error'));
  } finally {
    saving.value = false;
  }
}

defineExpose({ open });
</script>

<template>
  <div ref="element" class="modal fade" id="linkIdentityModal" tabindex="-1" aria-labelledby="linkIdentityModalLabel" aria-hidden="true">
    <div class="modal-dialog">
      <div class="modal-content">
        <div class="modal-header">
          <h1 class="modal-title fs-5" id="linkIdentityModalLabel">{{ t('linkIdentity.title') }}</h1>
          <button type="button" class="btn-close" data-bs-dismiss="modal" :aria-label="t('common.close')"></button>
        </div>
        <div class="modal-body">
          <p class="small">{{ t('linkIdentity.textBefore') }} <strong id="link-identity-label">{{ label }}</strong> {{ t('linkIdentity.textAfter') }}</p>
          <label for="link-identity-login" class="form-label">{{ t('linkIdentity.student') }}</label>
          <select class="form-select" id="link-identity-login" v-model="login">
            <option v-for="s in students" :key="s" :value="s">{{ s }}</option>
          </select>
          <div v-if="error" id="link-identity-error" class="alert alert-danger mt-3 mb-0 small" role="alert">{{ error() }}</div>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">{{ t('common.cancel') }}</button>
          <button type="button" class="btn btn-primary" id="confirm-link-identity" :disabled="saving || students.length === 0" @click="confirm">{{ t('linkIdentity.link') }}</button>
        </div>
      </div>
    </div>
  </div>
</template>
