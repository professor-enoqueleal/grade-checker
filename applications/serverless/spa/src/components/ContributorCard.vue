<script setup lang="ts">
import type { GroupCard } from '@/api/types';

/**
 * Card of "Contributors and members" (openspec capability group-members): avatar, login, contributions, "Member" badge;
 * links GitHub and Details (commit history); administrators also get Mark / Unmark (as member).
 */
const props = defineProps<{ card: GroupCard; groupId: number; isAdmin: boolean; busy?: boolean }>();
const emit = defineEmits<{ mark: [login: string]; unmark: [login: string] }>();

const historyLink = { name: 'commits', query: { groupId: String(props.groupId), author: props.card.login } };
</script>

<template>
  <div class="card h-100 rounded-3 shadow-sm" :data-login="card.login">
    <img :src="card.avatarUrl" class="card-img-top avatar-card-img" :alt="`Avatar of ${card.login}`" loading="lazy">
    <div class="card-body">
      <h5 class="card-title text-break">{{ card.login }}</h5>
      <p class="card-text contributions-count">Contributions: {{ card.contributions ?? '—' }}</p>
      <span v-if="card.member" class="badge text-bg-success member-badge">Member</span>
    </div>
    <div class="card-footer bg-transparent border-0 pb-3 small">
      <a :href="card.htmlUrl" target="_blank" rel="noopener noreferrer" class="card-link">GitHub</a>
      <RouterLink :to="historyLink" class="card-link">Details</RouterLink>
      <template v-if="isAdmin">
        <a v-if="card.member" href="#" class="card-link unmark-member-link" :data-login="card.login" title="Unmark as member"
           :aria-label="`Unmark ${card.login} as member`" @click.prevent="!busy && emit('unmark', card.login)">Unmark</a>
        <a v-else href="#" class="card-link mark-member-link" :data-login="card.login" title="Mark as member"
           :aria-label="`Mark ${card.login} as member`" @click.prevent="!busy && emit('mark', card.login)">Mark</a>
      </template>
    </div>
  </div>
</template>

<style scoped>
/* contributor avatar: square and edge to edge */
.avatar-card-img { aspect-ratio: 1 / 1; object-fit: cover; }
</style>
