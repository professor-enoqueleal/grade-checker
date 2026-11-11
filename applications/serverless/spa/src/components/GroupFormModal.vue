<script setup lang="ts">
import { computed, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { api } from '@/api';
import { errorMessage } from '@/api/client';
import { useModal } from '@/composables/useModal';

/**
 * Add / edit a group (openspec capability group-management). The backend validates the repository on GitHub and
 * stores it with the spelling of GitHub. Editing warns when the repository changes.
 */
const emit = defineEmits<{ saved: [] }>();

const { element, show, hide } = useModal();
const { t } = useI18n();
const id = ref<number | null>(null);
const name = ref('');
const repositoryUrl = ref('');
const originalUrl = ref('');
// computed on render, so it follows a change of language
const error = ref<(() => string) | null>(null);
const saving = ref(false);

const repositoryChanged = computed(() => id.value !== null && repositoryUrl.value.trim() !== originalUrl.value);

function open(group?: { id: number; name: string; repositoryUrl: string }) {
  id.value = group?.id ?? null;
  name.value = group?.name ?? '';
  repositoryUrl.value = group?.repositoryUrl ?? '';
  originalUrl.value = group?.repositoryUrl ?? '';
  error.value = null;
  saving.value = false;
  show();
}

async function save() {
  error.value = null;
  const n = name.value.trim();
  const url = repositoryUrl.value.trim();
  if (!n || !url) {
    error.value = () => t('groupForm.required');
    return;
  }
  saving.value = true;
  try {
    if (id.value === null) await api.createGroup(n, url);
    else await api.updateGroup(id.value, n, url);
    hide();
    emit('saved');
  } catch (e) {
    error.value = () => errorMessage(e, t('groupForm.saveError'));
  } finally {
    saving.value = false;
  }
}

defineExpose({ open });
</script>

<template>
  <div ref="element" class="modal fade" id="groupModal" tabindex="-1" aria-labelledby="groupModalLabel" aria-hidden="true">
    <form id="group-form" novalidate @submit.prevent="save">
      <div class="modal-dialog">
        <div class="modal-content">
          <div class="modal-header">
            <h1 class="modal-title fs-5" id="groupModalLabel">{{ t(id === null ? 'groupForm.addTitle' : 'groupForm.editTitle') }}</h1>
            <button type="button" class="btn-close" data-bs-dismiss="modal" :aria-label="t('common.close')"></button>
          </div>
          <div class="modal-body">
            <div class="mb-3">
              <label for="group-name" class="form-label">{{ t('common.name') }}</label>
              <input type="text" class="form-control" id="group-name" maxlength="255" required v-model="name">
            </div>
            <div class="mb-3">
              <label for="repository-url" class="form-label">{{ t('groupForm.repositoryUrl') }}</label>
              <input type="url" class="form-control" id="repository-url" maxlength="255"
                     :placeholder="t('groupForm.repositoryPlaceholder')" required v-model="repositoryUrl">
            </div>
            <div v-show="repositoryChanged" id="repository-change-warning" class="alert alert-warning small" role="status">
              {{ t('groupForm.repositoryChanged') }}
            </div>
            <div v-if="error" id="group-form-error" class="alert alert-danger mb-0 small" role="alert">{{ error() }}</div>
          </div>
          <div class="modal-footer">
            <button type="submit" class="btn btn-primary" id="save-group" :disabled="saving">{{ t('common.save') }}</button>
          </div>
        </div>
      </div>
    </form>
  </div>
</template>
