# 合并模板更新

`npx init` 生成一次源码快照。对已有项目，在单独的 Git 分支上把新模板的变化合入业务代码。Git 用上一次模板快照作为共同基线，合并独立改动，并标出需要人工处理的冲突。

新版 Ktor 项目从仓库根工程生成。生成器保留模块名和目录，修改项目名、页面标题和访问前缀。

## 保留初始模板快照

新项目可以用模板提交 SHA 或发行标签初始化。将以下命令中的 `<模板版本>` 替换为已发布的实际引用。

```sh
npx --yes "github:ZeroDevi1/ZeroKotlin#<模板版本>" init --name order-service --context-url /apps/orders
```

开始写业务代码前，把刚生成的文件单独提交，提交信息里记下实际使用的模板 SHA 或标签。再从这个提交建立 `template-upstream` 分支。业务项目在自己的主分支继续开发。

```sh
cd order-service
git init -b main
git add -A
git commit -m "chore: 初始化模板" -m "Template-Ref: <模板版本>"
git branch template-upstream
```

已有项目如果保留了纯初始化提交，可以执行 `git branch template-upstream <纯初始化提交>`。分支已经存在时，继续使用该分支保存的最近一次模板快照。

## 从模板分支合并新版

1. 在现有业务仓库为 `template-upstream` 创建独立工作树，例如 `git worktree add ../order-template-upstream template-upstream`。这个工作树只维护模板生成的文件。
2. 在另一个临时父目录，用新版的标签或 SHA 运行 `npx init`。传入生成模板基线时使用的 `--name` 和 `--context-url`，使对比只反映模板变化。
3. 对照新生成的目录，把模板新增、修改和删除的文件同步到模板工作树。保留工作树自己的 `.git`。用 `git status` 和 `git diff` 核对变化，然后在模板工作树提交，提交信息记录新版模板引用。
4. 回到业务仓库，确保当前业务改动已提交，从业务分支创建升级分支。执行以下命令，先查看 Git 合并结果。

   ```sh
   git switch -c chore/template-upgrade
   git merge --no-ff --no-commit template-upstream
   ```

5. 逐项解决冲突。重点核对 `app.contextPath` 和 Vite `contextPath`、接口地址、Gradle 构建配置，以及前端 `package.json` 和 `pnpm-lock.yaml`。在升级分支核对前后端启动与示例请求后，用 `git add` 暂存解决结果，用 `git commit` 完成合并。将升级分支通过普通合并或快进合并纳入业务主分支，保留模板合并提交。下次升级时，Git 就能以上次合入的快照为基线。 [Git 合并与冲突处理](https://git-scm.com/docs/git-merge)

## 找不到纯初始化提交时

若知道原模板版本，用相同的初始化参数在两个临时目录分别生成旧版和新版。用 `git diff --no-index -- <旧版目录> <新版目录>` 查看模板差异，再按文件把需要的变化合进升级分支。业务也改过的文件，用旧模板文件、当前业务文件和新模板文件做三方合并。若原版本也无法确认，按新版变更说明逐项接入需要的功能。 [Git 文件三方合并](https://git-scm.com/docs/git-merge-file) · [npm Git 引用说明](https://docs.npmjs.com/cli/v11/using-npm/package-spec/#git-urls)

## 从旧版单模块 Ktor 升级

| 旧项目路径 | 新工程路径 |
| --- | --- |
| `src/main/kotlin/dev/zerokotlin/ktor/` | 系统路由在 `zero-system/src/dev/zerokotlin/ktor/`，通用实现按职责移入 `zero-core/src/dev/zerokotlin/core/`。 |
| `src/main/kotlin/dev/zerokotlin/codegen/` | `zero-codegen/src/dev/zerokotlin/codegen/`。 |
| `src/main/resources/` | `zero-system/resources/`，包含 `application.conf` 和 `db/`。 |
| `web/` | `zero-web/`。 |

先在升级分支把业务改动移到对应目录，再解决源码和构建冲突。保持 Ktor 与 Vite 的访问前缀一致。旧版从生成项目根目录启动时，当前默认数据库和文件仍以根目录解析。若之前在仓库中以 `-p ktor-template` 运行并把数据留在该目录，请用 `ZERO_DATABASE_SQLITE_PATH` 和 `ZERO_FILES_LOCAL_PATH` 指向原位置。

升级后的启动和代码生成命令见 [README](../README.md) 和 [CRUD 生成器](codegen.md)。
