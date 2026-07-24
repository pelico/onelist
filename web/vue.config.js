module.exports = {
  // 构建输出目录，开发时输出到 ../public/dist 供 Go embed 使用
  // Docker 构建时通过 --outputDir 覆盖
  outputDir: process.env.OUTPUT_DIR || '../public/dist',
  // 生产环境关闭 source map 减小体积
  productionSourceMap: false,
  devServer: {
    proxy: {
      '/v1/api': {
        target: 'http://localhost:5245',
        changeOrigin: true
      },
      '/file': {
        target: 'http://localhost:5245',
        changeOrigin: true
      },
      '/t': {
        target: 'http://localhost:5245',
        changeOrigin: true
      },
      '/gallery': {
        target: 'http://localhost:5245',
        changeOrigin: true
      },
      '/onelist': {
        target: 'http://localhost:5245',
        changeOrigin: true
      }
    }
  }
};
