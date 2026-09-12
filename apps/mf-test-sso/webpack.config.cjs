const path = require('path');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const { container: { ModuleFederationPlugin } } = require('webpack');
const { ResourceManifestWebpackPlugin, MicroFrontendManifestWebpackPlugin } = require('../../tools/resource-manifest-webpack-plugin.cjs');
module.exports = {
  entry: './src/index.ts', output: { path: path.resolve(__dirname, 'dist'), publicPath: 'auto', uniqueName: 'aurevia_test_sso', clean: true, filename: '[name].[contenthash].js', chunkFilename: '[name].[contenthash].js' },
  resolve: { extensions: ['.tsx', '.ts', '.js'] },
  module: { rules: [{ test: /\.tsx?$/, use: 'ts-loader', exclude: /node_modules/ }] },
  plugins: [new ResourceManifestWebpackPlugin('./resource-manifest.json'), new MicroFrontendManifestWebpackPlugin('./mf-manifest.json'),
    new HtmlWebpackPlugin({ title: 'SSO Test Microfrontend' }),
    new ModuleFederationPlugin({ name: 'aurevia_test_sso', filename: 'remoteEntry.js', exposes: { './plugin': './src/bootstrap.tsx' },
      shared: { react: { singleton: true, requiredVersion: '19.1.1' }, 'react-dom': { singleton: true, requiredVersion: '19.1.1' }, '@aurevia/contracts': { singleton: true } } })],
  devServer: { host: '127.0.0.1', port: 3011, historyApiFallback: true,
    proxy: [{ context: ['/api', '/auth', '/oauth2', '/login'], target: process.env.AUREVIA_BFF_URL || 'http://localhost:8443', changeOrigin: true }] }
};
