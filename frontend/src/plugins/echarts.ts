import { use } from 'echarts/core'
import { CanvasRenderer, SVGRenderer } from 'echarts/renderers'
import {
  LineChart, BarChart, PieChart, ScatterChart,
  RadarChart, GaugeChart
} from 'echarts/charts'
import {
  TitleComponent, TooltipComponent, LegendComponent,
  GridComponent, DatasetComponent, TransformComponent,
  DataZoomComponent, GraphicComponent, ToolboxComponent,
  PolarComponent, MarkLineComponent, MarkPointComponent,
  AriaComponent
} from 'echarts/components'
import type { App } from 'vue'

let registered = false
export function registerECharts(): void {
  if (registered) return
  registered = true
  use([
    CanvasRenderer, SVGRenderer,
    LineChart, BarChart, PieChart, ScatterChart, RadarChart, GaugeChart,
    TitleComponent, TooltipComponent, LegendComponent, GridComponent,
    DatasetComponent, TransformComponent, DataZoomComponent,
    GraphicComponent, ToolboxComponent, PolarComponent,
    MarkLineComponent, MarkPointComponent, AriaComponent,
  ])
}

export default {
  install(app: App) {
    registerECharts()
  },
}
