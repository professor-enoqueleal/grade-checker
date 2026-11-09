<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import type { ChartDataset, TooltipItem } from 'chart.js';
import { Chart, chartHeight, GRID_COLOR, LABEL_COLOR, shortLabel, TICK_COLOR } from '@/utils/charts';

/** Horizontal stacked bars, one bar per author (grades and lines changed of the group dashboard). */
const props = defineProps<{
  canvasId: string;
  labels: string[];
  datasets: ChartDataset<'bar', (number | null)[]>[];
  max?: number;
  chartLabel: string;
  tooltipLabel: (item: TooltipItem<'bar'>) => string;
  tooltipFooter?: (items: TooltipItem<'bar'>[]) => string;
}>();

const canvas = ref<HTMLCanvasElement | null>(null);
let chart: Chart<'bar', (number | null)[], string> | null = null;

function draw() {
  chart?.destroy();
  if (!canvas.value) return;
  const legend = props.datasets.length > 1;
  chart = new Chart<'bar', (number | null)[], string>(canvas.value, {
    type: 'bar',
    data: { labels: props.labels, datasets: props.datasets },
    options: {
      indexAxis: 'y',
      responsive: true,
      maintainAspectRatio: false,
      animation: false,
      scales: {
        x: { stacked: true, min: 0, max: props.max, grid: { color: GRID_COLOR }, border: { display: false },
             ticks: { color: TICK_COLOR, precision: 0 } },
        y: { stacked: true, grid: { display: false }, border: { display: false },
             ticks: { color: LABEL_COLOR, callback(value) { return shortLabel(this.getLabelForValue(Number(value)), this.chart.width); } } },
      },
      plugins: {
        legend: { display: legend, position: 'bottom', labels: { boxWidth: 12, boxHeight: 12 } },
        tooltip: { callbacks: { label: props.tooltipLabel, ...(props.tooltipFooter ? { footer: props.tooltipFooter } : {}) } },
      },
    },
  });
}

onMounted(draw);
watch(() => [props.labels, props.datasets, props.max], draw);
onBeforeUnmount(() => { chart?.destroy(); chart = null; });
</script>

<template>
  <div class="chart-box" :id="`${canvasId}-box`" :style="{ height: chartHeight(labels.length, datasets.length > 1) + 'px' }">
    <canvas ref="canvas" :id="canvasId" role="img" :aria-label="chartLabel"></canvas>
  </div>
</template>
