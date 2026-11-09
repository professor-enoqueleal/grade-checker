<script setup lang="ts">
import { ref } from 'vue';
import { api } from '@/api';
import { errorMessage } from '@/api/client';
import { useModal } from '@/composables/useModal';

/** "Link to student" (openspec capability identity-aliases): commits of an identity without account count for a student. */
const props = defineProps<{ groupId: number; students: string[] }>();
const emit = defineEmits<{ linked: [] }>();

const { element, show, hide } = useModal();
const identityKey = ref('');
const label = ref('');
const login = ref('');
const error = ref('');
const saving = ref(false);

function open(key: string, identityLabel: string) {
  identityKey.value = key;
  label.value = identityLabel;
  login.value = props.students[0] ?? '';
  error.value = props.students.length === 0 ? 'No students registered in this group.' : '';
  show();
}

async function confirm() {
  saving.value = true;
  try {
    await api.linkIdentity(props.groupId, identityKey.value, login.value);
    hide();
    emit('linked');
  } catch (e) {
    error.value = errorMessage(e, 'Could not link the identity. Please try again.');
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
          <h1 class="modal-title fs-5" id="linkIdentityModalLabel">Link to student</h1>
          <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
        </div>
        <div class="modal-body">
          <p class="small">Commits of <strong id="link-identity-label">{{ label }}</strong> will be counted for the selected student in every period, including grades.</p>
          <label for="link-identity-login" class="form-label">Student</label>
          <select class="form-select" id="link-identity-login" v-model="login">
            <option v-for="s in students" :key="s" :value="s">{{ s }}</option>
          </select>
          <div v-if="error" id="link-identity-error" class="alert alert-danger mt-3 mb-0 small" role="alert">{{ error }}</div>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Cancel</button>
          <button type="button" class="btn btn-primary" id="confirm-link-identity" :disabled="saving || students.length === 0" @click="confirm">Link</button>
        </div>
      </div>
    </div>
  </div>
</template>
