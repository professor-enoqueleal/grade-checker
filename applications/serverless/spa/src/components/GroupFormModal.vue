<script setup lang="ts">
import { computed, ref } from 'vue';
import { api } from '@/api';
import { errorMessage } from '@/api/client';
import { useModal } from '@/composables/useModal';

/**
 * Add / edit a group (openspec capability group-management). The backend validates the repository on GitHub and
 * stores it with the spelling of GitHub. Editing warns when the repository changes.
 */
const emit = defineEmits<{ saved: [] }>();

const { element, show, hide } = useModal();
const id = ref<number | null>(null);
const name = ref('');
const repositoryUrl = ref('');
const originalUrl = ref('');
const error = ref('');
const saving = ref(false);

const repositoryChanged = computed(() => id.value !== null && repositoryUrl.value.trim() !== originalUrl.value);

function open(group?: { id: number; name: string; repositoryUrl: string }) {
  id.value = group?.id ?? null;
  name.value = group?.name ?? '';
  repositoryUrl.value = group?.repositoryUrl ?? '';
  originalUrl.value = group?.repositoryUrl ?? '';
  error.value = '';
  saving.value = false;
  show();
}

async function save() {
  error.value = '';
  const n = name.value.trim();
  const url = repositoryUrl.value.trim();
  if (!n || !url) {
    error.value = 'Please fill in the name and the repository URL.';
    return;
  }
  saving.value = true;
  try {
    if (id.value === null) await api.createGroup(n, url);
    else await api.updateGroup(id.value, n, url);
    hide();
    emit('saved');
  } catch (e) {
    error.value = errorMessage(e, 'Could not save the group. Please try again.');
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
            <h1 class="modal-title fs-5" id="groupModalLabel">{{ id === null ? 'Add a new group' : 'Edit group' }}</h1>
            <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
          </div>
          <div class="modal-body">
            <div class="mb-3">
              <label for="group-name" class="form-label">Name</label>
              <input type="text" class="form-control" id="group-name" maxlength="255" required v-model="name">
            </div>
            <div class="mb-3">
              <label for="repository-url" class="form-label">Repository URL</label>
              <input type="url" class="form-control" id="repository-url" maxlength="255"
                     placeholder="https://github.com/owner/repository" required v-model="repositoryUrl">
            </div>
            <div v-show="repositoryChanged" id="repository-change-warning" class="alert alert-warning small" role="status">
              Contributors and grades will come from the new repository. Members and linked identities stay.
              If this group comes from the seed script, update the seed too, or the group with the old URL is created
              again at the next seed.
            </div>
            <div v-if="error" id="group-form-error" class="alert alert-danger mb-0 small" role="alert">{{ error }}</div>
          </div>
          <div class="modal-footer">
            <button type="submit" class="btn btn-primary" id="save-group" :disabled="saving">Save</button>
          </div>
        </div>
      </div>
    </form>
  </div>
</template>
