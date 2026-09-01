import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import path from 'path'
import fs from 'fs'

// 修正 Download 目录下文件的 Content-Type（Vite 对 .apk 等扩展名返回空类型，导致浏览器误判为 .zip）
const downloadMimePlugin = () => {
  const mimes: Record<string, string> = {
    '.apk': 'application/vnd.android.package-archive',
    '.ipa': 'application/octet-stream',
    '.exe': 'application/octet-stream',
    '.zip': 'application/zip',
    '.pdf': 'application/pdf',
  }
  // 完整下载中间件：接管 /Download/ 路径，支持 Range 断点续传（PDA/Android 下载组件依赖）
  const downloadHandler = (req: any, res: any, next: any) => {
    const pathname = decodeURIComponent((req.url || '').split('?')[0])
    if (!pathname.startsWith('/Download/')) return next()

    const root = path.join(__dirname, 'public')
    const filePath = path.join(root, pathname)
    if (!filePath.startsWith(root)) {
      res.statusCode = 403
      return res.end('Forbidden')
    }

    fs.stat(filePath, (err, stat) => {
      if (err || !stat.isFile()) {
        res.statusCode = 404
        return res.end('Not Found')
      }

      const ext = path.extname(filePath).toLowerCase()
      const fileName = path.basename(filePath)
      const asciiName = fileName.replace(/[^\x20-\x7E]/g, '_').replace(/"/g, '')

      res.setHeader('Content-Type', mimes[ext] || 'application/octet-stream')
      res.setHeader('Content-Disposition', `attachment; filename="${asciiName}"; filename*=UTF-8''${encodeURIComponent(fileName)}`)
      res.setHeader('Accept-Ranges', 'bytes')
      res.setHeader('Cache-Control', 'public, max-age=0')
      res.setHeader('Last-Modified', stat.mtime.toUTCString())
      res.setHeader('ETag', `"${stat.size.toString(16)}-${Math.floor(stat.mtimeMs).toString(16)}"`)

      // Range 断点续传支持
      const range = req.headers.range
      if (range) {
        const m = /bytes=(\d*)-(\d*)/.exec(range)
        let start = m && m[1] ? parseInt(m[1], 10) : 0
        let end = m && m[2] ? parseInt(m[2], 10) : stat.size - 1
        if (isNaN(start) || start > end || end >= stat.size) {
          res.statusCode = 416
          res.setHeader('Content-Range', `bytes */${stat.size}`)
          return res.end()
        }
        res.statusCode = 206
        res.setHeader('Content-Range', `bytes ${start}-${end}/${stat.size}`)
        res.setHeader('Content-Length', end - start + 1)
        fs.createReadStream(filePath, { start, end }).pipe(res)
      } else {
        res.setHeader('Content-Length', stat.size)
        fs.createReadStream(filePath).pipe(res)
      }
    })
  }

  return {
    name: 'download-content-type',
    configureServer(server: any) {
      server.middlewares.use(downloadHandler)
    },
    configurePreviewServer(server: any) {
      server.middlewares.use(downloadHandler)
    },
  }
}

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd())

  return {
    base: env.VITE_BASE || '/',
    resolve: {
      alias: {
        '@': path.resolve(__dirname, 'src'),
      },
    },
    plugins: [
      vue(),
      downloadMimePlugin(),
      AutoImport({
        imports: ['vue', 'vue-router', 'pinia'],
        resolvers: [ElementPlusResolver()],
        dts: 'src/types/auto-imports.d.ts',
      }),
      Components({
        resolvers: [ElementPlusResolver()],
        dts: 'src/types/components.d.ts',
      }),
    ],
    server: {
      host: '0.0.0.0',
      port: 3000,
      open: false,
      proxy: {
        '/api': {
          target: 'http://localhost:8081',
          changeOrigin: true,
        },
      },
    },
    css: {
      preprocessorOptions: {
        scss: {
          additionalData: `@use "@/styles/variables.scss" as *;`,
        },
      },
    },
    build: {
      outDir: 'dist',
      sourcemap: false,
      chunkSizeWarningLimit: 1500,
      rollupOptions: {
        output: {
          manualChunks: {
            vue: ['vue', 'vue-router', 'pinia'],
            'element-plus': ['element-plus', '@element-plus/icons-vue'],
            echarts: ['echarts', 'vue-echarts'],
          },
        },
      },
    },
  }
})
