#!/usr/bin/env node
import { chmodSync, cpSync, existsSync, lstatSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { basename, dirname, join, resolve } from 'node:path'
import { parseArgs } from 'node:util'
import { fileURLToPath } from 'node:url'

const templateRoot = fileURLToPath(new URL('../', import.meta.url))
const usage = `用法：zerokotlin init --name <项目名> --context-url <路径或 HTTP(S) URL>

  --name         必填。用作项目名和新建目录名
  --context-url  必填。指定访问前缀，例如 /apps/orders 或 https://example.com/apps/orders
  --help         显示帮助

在当前目录下创建以项目名命名的新目录。`

function parseContextPath(value) {
  if (!value) throw new Error('缺少 --context-url')
  if (/[\s\\?#]/.test(value)) throw new Error('--context-url 不能包含空白、反斜杠、查询参数或片段')
  const absolute = /^https?:\/\/[^/@]+(\/.*)?$/i.exec(value)
  if (absolute && !URL.canParse(value)) throw new Error('--context-url 的 HTTP(S) URL 无效')

  // 校验原始路径，避免 URL 解析折叠点段后绕过路径检查。
  const path = absolute ? absolute[1] ?? '/' : value
  const contextPath = path.replace(/\/$/, '')
  if (!path.startsWith('/') || (contextPath && !/^(?:\/[A-Za-z0-9][A-Za-z0-9_-]*)+$/.test(contextPath))) {
    throw new Error('--context-url 需要绝对路径，每段以字母或数字开头，只允许字母、数字、连字符和下划线')
  }
  return contextPath
}

function updateProject(destination, name, contextPath) {
  function edit(relativePath, change) {
    const path = join(destination, relativePath)
    const original = readFileSync(path, 'utf8')
    const updated = change(original)
    if (updated !== original) writeFileSync(path, updated, 'utf8')
  }

  function replaceSettings(relativePath, replacements) {
    edit(relativePath, (content) => {
      for (const [setting, value] of replacements) {
        if (!content.includes(setting)) throw new Error(`模板配置项缺失：${relativePath} 中的 ${setting.trim()}`)
        content = content.replace(setting, value)
      }
      return content
    })
  }

  replaceSettings('settings.gradle.kts', [['rootProject.name = "ZeroKotlin"', `rootProject.name = "${name}"`]])
  replaceSettings('zero-system/resources/application.conf', [
    ['name = "ZeroKotlin"', `name = "${name}"`],
    ['contextPath = "/"', `contextPath = "${contextPath || '/'}"`],
  ])
  replaceSettings('zero-web/vite.config.ts', [["const contextPath = ''", `const contextPath = '${contextPath}'`]])
  replaceSettings('zero-web/index.html', [['<title>ZeroKotlin 管理工作台</title>', `<title>${name}</title>`]])
  edit('README.md', (content) => content
    .replace('# ZeroKotlin', `# ${name}`)
    .replaceAll('http://127.0.0.1:8080/', `http://127.0.0.1:8080${contextPath}/`)
    .replaceAll('http://127.0.0.1:5173/', `http://127.0.0.1:5173${contextPath}/`))
}

function initProject(name, contextPath) {
  const { files } = JSON.parse(readFileSync(join(templateRoot, 'package.json'), 'utf8'))
  const destination = resolve(name)
  try {
    mkdirSync(destination)
  } catch (error) {
    if (error.code === 'EEXIST') throw new Error(`目标已存在：${destination}`)
    throw error
  }

  try {
    for (const file of files) {
      const source = join(templateRoot, file)
      // npm 解包时可能将模板的 .gitignore 改名为 .npmignore。
      const templateFile = basename(file) === '.gitignore' && !existsSync(source)
        ? join(dirname(source), '.npmignore')
        : source
      cpSync(templateFile, join(destination, file), {
        recursive: true,
        force: false,
        errorOnExist: true,
        filter: (source) => !lstatSync(source).isSymbolicLink() && !/^\.env(?:\.|$)/.test(basename(source)),
      })
    }
    if (process.platform !== 'win32') chmodSync(join(destination, 'gradlew'), 0o755)
    updateProject(destination, name, contextPath)
  } catch (error) {
    try {
      rmSync(destination, { recursive: true, force: true })
    } catch (cleanupError) {
      throw new Error(`${error.message}。无法清理本次创建的目录 ${destination}：${cleanupError.message}`)
    }
    throw error
  }
  return destination
}

function main(args) {
  if (args.length === 0 || args[0] === '--help') {
    console.log(usage)
    return
  }
  const [command, ...flags] = args
  if (command !== 'init') throw new Error('未知命令，请使用 --help 查看用法')
  const { values } = parseArgs({
    args: flags,
    options: { 'context-url': { type: 'string' }, name: { type: 'string' }, help: { type: 'boolean' } },
  })
  if (values.help) {
    console.log(usage)
    return
  }

  const name = values.name
  if (name === undefined) throw new Error('缺少 --name')
  if (!/^[A-Za-z0-9][A-Za-z0-9_-]{0,79}$/.test(name) || /^(?:con|prn|aux|nul|com[0-9]|lpt[0-9])$/i.test(name)) {
    throw new Error('项目名须为 1–80 个字符，以字母或数字开头，可包含连字符和下划线，且须能用作目录名')
  }
  const contextPath = parseContextPath(values['context-url'])
  const destination = initProject(name, contextPath)
  console.log(`已初始化 ${name}：${destination}\n访问前缀：${contextPath || '/'}`)
}

try {
  main(process.argv.slice(2))
} catch (error) {
  console.error(`初始化失败：${error.message}`)
  process.exitCode = 1
}
