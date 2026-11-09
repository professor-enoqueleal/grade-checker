<script setup lang="ts">
import { ref } from 'vue';
import { api } from '@/api';
import { errorMessage } from '@/api/client';
import { useModal } from '@/composables/useModal';

/** Add / edit a member: name and GitHub login (openspec capability group-members). Linked identities follow the login. */
const props = defineProps<{ groupId: number }>();
const emit = defineEmits<{ saved: [] }>();

const { element, show, hide } = useModal();
const currentLogin = ref<string | null>(null);
const name = ref('');
const login = ref('');
const error = ref('');
const saving = ref(false);

function open(member?: { login: string; name: string }) {
  currentLogin.value = member?.login ?? null;
  name.value = member?.name ?? '';
  login.value = member?.login ?? '';
  error.value = '';
  saving.value = false;
  show();
}

async function save() {
  error.value = '';
  const n = name.value.trim();
  const l = login.value.trim();
  if (!n || !l) {
    error.value = 'Please fill in the name and the GitHub username.';
    return;
  }
  saving.value = true;
  try {
    if (currentLogin.value === null) await api.addMember(props.groupId, l, n, true);
    else await api.updateMember(props.groupId, currentLogin.value, l, n);
    hide();
    emit('saved');
  } catch (e) {
    error.value = errorMessage(e, 'Could not save the member. Please try again.');
  } finally {
    saving.value = false;
  }
}

defineExpose({ open });
</script>

<template>
  <div ref="element" class="modal fade" id="memberModal" tabindex="-1" aria-labelledby="memberModalLabel" aria-hidden="true">
    <form id="member-form" novalidate @submit.prevent="save">
      <div class="modal-dialog">
        <div class="modal-content">
          <div class="modal-header">
            <h1 class="modal-title fs-5" id="memberModalLabel">{{ currentLogin === null ? 'Add group member' : 'Edit member' }}</h1>
            <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
          </div>
          <div class="modal-body">
            <div class="mb-3">
              <label for="user-name" class="form-label">Name</label>
              <input type="text" class="form-control" id="user-name" maxlength="255" required v-model="name">
            </div>
            <div class="mb-3">
              <label for="github-username" class="form-label">GitHub username</label>
              <input type="text" class="form-control" id="github-username" maxlength="39" required v-model="login">
            </div>
            <div v-if="error" id="member-form-error" class="alert alert-danger mb-0 small" role="alert">{{ error }}</div>
          </div>
          <div class="modal-footer">
            <button type="submit" class="btn btn-primary" id="save-member" :disabled="saving">Save</button>
          </div>
        </div>
      </div>
    </form>
  </div>
</template>
