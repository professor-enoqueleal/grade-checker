/**
 * Chart.js setup shared by the charts (openspec change graficos-e-explicacao-da-nota and the commit frequency chart).
 * One color per series, never reused: milestones are an ordered blue ramp (M1 light -> M3 dark), outside the
 * milestones is neutral gray, lines changed are orange / violet (palette validated for color vision deficiency).
 */
import {
  BarController, BarElement, CategoryScale, Chart, Legend, LinearScale, Tooltip,
  type ChartDataset, type ScriptableContext,
} from 'chart.js';

Chart.register(BarController, BarElement, CategoryScale, LinearScale, Tooltip, Legend);

// the E2E tests read the charts with Chart.getChart(canvasId), as with the Chart.js of the JSP pages
(window as unknown as { Chart: typeof Chart }).Chart = Chart;

export { Chart };

export const CHART_COLORS: Record<string, string> = {
  m1: '#86b6ef', m2: '#3987e5', m3: '#1c5cab',
  outside: '#868e96',
  additions: '#eb6834', deletions: '#6250d6',
};
export const CHART_SURFACE = '#ffffff';   // 2px gap between stacked segments
export const GRID_COLOR = '#e9ecef';
export const TICK_COLOR = '#6c757d';
export const LABEL_COLOR = '#212529';

/** 40px per bar plus room for the axis and the legend, at least 160px. */
export function chartHeight(bars: number, legend: boolean): number {
  return Math.max(160, bars * 40 + (legend ? 80 : 50));
}

/** Long author names are shortened with "…" on narrow charts; the tooltip shows the full name. */
export function shortLabel(label: string, chartWidth: number): string {
  const max = chartWidth < 576 ? 16 : 36;
  return label.length > max ? label.substring(0, max - 1) + '…' : label;
}

/** Horizontal stacked bars: round only the end of the last non-empty segment of each bar. */
function barEndRadius(datasetCount: number) {
  return (ctx: ScriptableContext<'bar'>) => {
    const chart = ctx.chart;
    for (let d = datasetCount - 1; d > ctx.datasetIndex; d--) {
      const v = chart.data.datasets[d].data[ctx.dataIndex] as number | null | undefined;
      if (v !== null && v !== undefined && v > 0 && chart.isDatasetVisible(d)) return 0;
    }
    return { topRight: 4, bottomRight: 4, topLeft: 0, bottomLeft: 0 };
  };
}

export function barDataset(label: string, data: (number | null)[], color: string, datasetCount: number): ChartDataset<'bar', (number | null)[]> {
  return {
    label,
    data,
    backgroundColor: color,
    hoverBackgroundColor: color,
    borderColor: CHART_SURFACE,
    borderWidth: { top: 0, bottom: 0, left: 0, right: 2 },
    borderSkipped: false,
    borderRadius: barEndRadius(datasetCount),
    maxBarThickness: 18,
  };
}

/** Vertical stacked columns: round only the top of the last non-empty segment of each column. */
export function columnTopRadius(ctx: ScriptableContext<'bar'>) {
  const datasets = ctx.chart.data.datasets;
  for (let d = datasets.length - 1; d > ctx.datasetIndex; d--) {
    if ((datasets[d].data[ctx.dataIndex] as number) > 0) return 0;
  }
  return { topLeft: 3, topRight: 3, bottomLeft: 0, bottomRight: 0 };
}
