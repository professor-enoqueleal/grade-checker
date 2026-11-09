<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue';
import type { CommitActivity } from '@/api/types';
import { CHART_COLORS, CHART_SURFACE, Chart, columnTopRadius, GRID_COLOR, TICK_COLOR } from '@/utils/charts';

/**
 * Commit frequency (openspec capability commit-history-page): same commits as the table, per day or per week,
 * one series per milestone (blue ramp) and "outside the milestones" (gray). The backend sends the columns.
 */
const props = defineProps<{ activity: CommitActivity }>();
const canvas = ref<HTMLCanvasElement | null>(null);
let chart: Chart<'bar', number[], string> | null = null;

const plural = (n: number) => n + (n === 1 ? ' commit' : ' commits');

onMounted(() => {
  if (!canvas.value) return;
  const { labels, series, tooltips } = props.activity;
  chart = new Chart<'bar', number[], string>(canvas.value, {
    type: 'bar',
    data: {
      labels,
      datasets: series.map((s) => ({
        key: s.key,                      // milestone of the series (m1, m2, m3, outside), read by the E2E tests
        label: s.label,
        data: s.counts,
        backgroundColor: CHART_COLORS[s.key] ?? CHART_COLORS.outside,
        hoverBackgroundColor: CHART_COLORS[s.key] ?? CHART_COLORS.outside,
        borderColor: CHART_SURFACE,
        borderWidth: { top: 2, bottom: 0, left: 0, right: 0 },
        borderSkipped: false,
        borderRadius: columnTopRadius,
        maxBarThickness: 28,
      })),
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      animation: false,
      scales: {
        x: { stacked: true, grid: { display: false }, border: { display: false }, ticks: { color: TICK_COLOR, autoSkip: true, maxRotation: 0 } },
        y: { stacked: true, beginAtZero: true, grid: { color: GRID_COLOR }, border: { display: false }, ticks: { color: TICK_COLOR, precision: 0 } },
      },
      plugins: {
        legend: { display: series.length > 1, position: 'bottom', labels: { boxWidth: 12, boxHeight: 12 } },
        tooltip: {
          filter: (item) => (item.raw as number) > 0,
          callbacks: {
            title: (items) => tooltips[items[0].dataIndex],
            label: (item) => `${item.dataset.label}: ${plural(item.raw as number)}`,
            footer: (items) => {
              if (series.length < 2) return '';
              const i = items[0].dataIndex;
              return 'Total: ' + plural(series.reduce((sum, s) => sum + s.counts[i], 0));
            },
          },
        },
      },
    },
  });
});
onBeforeUnmount(() => { chart?.destroy(); chart = null; });
</script>

<template>
  <section class="mb-4" id="commit-activity" aria-labelledby="commit-activity-title">
    <h6 class="mb-1" id="commit-activity-title">{{ activity.title }}</h6>
    <p class="small text-body-secondary mb-2" id="commit-activity-summary">{{ activity.summary }}</p>
    <div class="commit-activity-box">
      <canvas ref="canvas" id="commit-activity-chart" role="img" :aria-label="activity.ariaLabel"></canvas>
    </div>
  </section>
</template>

<style scoped>
.commit-activity-box { position: relative; width: 100%; height: 220px; }
</style>
