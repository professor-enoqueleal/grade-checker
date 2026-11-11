<script setup lang="ts">
import { useI18n } from 'vue-i18n';
import type { GradingExplanation } from '@/api/types';

/**
 * "How grades are calculated" / "Como as notas são calculadas", for every user, in the user's language; every number
 * comes from grading.properties / milestones.properties, formatted by the backend (openspec capabilities
 * milestone-grading and serverless-i18n).
 */
defineProps<{ gx: GradingExplanation }>();
const { t, locale } = useI18n();
</script>

<template>
  <section class="card mb-5" id="grading-explanation" :lang="locale" aria-labelledby="grading-explanation-title">
    <div class="card-body">
      <h5 class="card-title" id="grading-explanation-title">{{ t('gx.title') }}</h5>
      <p class="mb-0">{{ t('gx.intro') }}</p>

      <h6>{{ t('gx.milestones') }}</h6>
      <ul id="grading-explanation-milestones">
        <li v-for="(m, i) in gx.milestones" :key="i">{{ t('gx.milestone', { label: t('period.milestone', { n: i + 1 }), start: m.start, end: m.end }) }}</li>
      </ul>
      <p>{{ t('gx.points', { max: gx.maxPoints, total: gx.totalPoints }) }}</p>

      <h6>{{ t('gx.whoTitle') }}</h6>
      <ul>
        <li>{{ t('gx.whoMembers') }}</li>
        <li>{{ t('gx.whoEmail') }}</li>
        <li>{{ t(gx.excludesLogins ? 'gx.whoMergeTeacher' : 'gx.whoMerge') }}</li>
      </ul>

      <h6>{{ t('gx.factorsTitle') }}</h6>
      <ul id="grading-explanation-factors">
        <li><strong>{{ t('gx.linesName', { weight: gx.weightEffectiveLines }) }}</strong>
          {{ t('gx.linesText', { cap: gx.lineCap, files: gx.reorganizationMinFiles, balance: gx.reorganizationBalance, factor: gx.reorganizationFactor }) }}</li>
        <li><strong>{{ t('gx.daysName', { weight: gx.weightActiveDays }) }}</strong>
          {{ t('gx.daysText', { timeZone: gx.timeZone }) }}</li>
        <li><strong>{{ t('gx.relevantName', { weight: gx.weightRelevantCommits }) }}</strong>
          {{ t('gx.relevantText', { min: gx.relevantCommitMinLines }) }}</li>
      </ul>

      <h6>{{ t('gx.gradeTitle') }}</h6>
      <ul>
        <li>{{ t('gx.share') }}</li>
        <li>{{ t('gx.fairShare', { tolerance: gx.tolerance, max: gx.maxPoints }) }}</li>
        <li id="grading-explanation-example">{{ t('gx.example', {
          students: gx.exampleStudents, fair: gx.exampleFairShare, full: gx.exampleFullShare,
          fullGrade: gx.exampleFullGrade, low: gx.exampleLowShare, lowGrade: gx.exampleLowGrade,
        }) }}</li>
        <li>{{ t('gx.hover') }}</li>
      </ul>

      <h6>{{ t('gx.tipsTitle') }}</h6>
      <ul class="mb-0">
        <li>{{ t('gx.tipFrequent') }}</li>
        <li>{{ t('gx.tipSmall', { cap: gx.lineCap }) }}</li>
        <li>{{ t('gx.tipEmail') }}
          <code>{{ t('gx.tipEmailCommand') }}</code></li>
      </ul>
    </div>
  </section>
</template>

<style scoped>
#grading-explanation h6 { margin-top: 1rem; }
#grading-explanation ul { margin-bottom: 0.5rem; }
</style>
