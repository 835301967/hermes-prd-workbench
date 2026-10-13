<template>
  <div class="admin-page">
    <header class="admin-topbar">
      <div class="admin-topbar-left">
        <button class="back-btn" type="button" title="返回工作台" aria-label="返回工作台" @click="goWorkbench">
          <ArrowLeft :size="16" />
          <span>返回工作台</span>
        </button>
        <div class="admin-title-wrap">
          <span class="admin-brand-badge">ADMIN ONLY</span>
          <h1 class="admin-title">Hermes PRD 工作台 · 运营监控看板</h1>
        </div>
      </div>
      <div class="admin-topbar-right">
        <span class="live-indicator">
          <span class="pulse-dot"></span>
          <span>实时监控</span>
        </span>
        <button class="refresh-btn" type="button" :disabled="loading" @click="loadData">
          <RotateCw :size="14" :class="{ spinning: loading }" />
          <span>{{ loading ? "正在同步..." : "刷新数据" }}</span>
        </button>
        <div class="admin-user-tag">
          <Shield :size="14" />
          <span>{{ user?.displayName || user?.username }} (管理员)</span>
        </div>
      </div>
    </header>

    <!-- Toast Notification -->
    <div v-if="toastMsg" class="admin-toast" :class="toastType">
      <span>{{ toastMsg }}</span>
    </div>

    <div class="admin-content">
      <!-- Error notice if load fails -->
      <div v-if="loadError" class="admin-error-banner">
        <AlertCircle :size="18" />
        <span>{{ loadError }}</span>
        <button type="button" @click="loadData">重试</button>
      </div>

      <!-- Tab Navigation -->
      <div class="admin-nav-tabs">
        <button
          type="button"
          class="admin-nav-tab"
          :class="{ active: currentTab === 'overview' }"
          @click="currentTab = 'overview'"
        >
          <LayoutDashboard :size="16" />
          <span>核心指标大盘</span>
        </button>
        <button
          type="button"
          class="admin-nav-tab"
          :class="{ active: currentTab === 'users' }"
          @click="currentTab = 'users'"
        >
          <Users :size="16" />
          <span>用户与权限管理 ({{ users.length }})</span>
        </button>
        <button
          type="button"
          class="admin-nav-tab"
          :class="{ active: currentTab === 'features' }"
          @click="currentTab = 'features'"
        >
          <BarChart3 :size="16" />
          <span>功能频次与耗时 ({{ features.length }})</span>
        </button>
        <button
          type="button"
          class="admin-nav-tab"
          :class="{ active: currentTab === 'errors' }"
          @click="currentTab = 'errors'"
        >
          <AlertTriangle :size="16" />
          <span>生成与评审失败 ({{ overview.totalErrors }})</span>
        </button>
        <button
          type="button"
          class="admin-nav-tab"
          :class="{ active: currentTab === 'logs' }"
          @click="currentTab = 'logs'"
        >
          <FileText :size="16" />
          <span>更新日志管理 ({{ updateLogs.length }})</span>
        </button>
      </div>

      <!-- Loading skeleton / content -->
      <div v-if="loading && !dashboard" class="admin-loading-state">
        <Loader2 :size="32" class="spinning" />
        <p>正在拉取全量运营与埋点审计数据...</p>
      </div>

      <div v-else-if="dashboard" class="admin-tab-body">
        <!-- 1. OVERVIEW TAB -->
        <section v-if="currentTab === 'overview'" class="tab-pane pane-overview">
          <div class="kpi-grid">
            <div class="kpi-card">
              <div class="kpi-icon-box blue">
                <Users :size="20" />
              </div>
              <div class="kpi-meta">
                <span class="kpi-label">累计注册用户</span>
                <strong class="kpi-value">{{ overview.totalUsers }}</strong>
                <span class="kpi-subtext">系统独立注册用户数</span>
              </div>
            </div>

            <div class="kpi-card">
              <div class="kpi-icon-box green">
                <Activity :size="20" />
              </div>
              <div class="kpi-meta">
                <span class="kpi-label">今日活跃 DAU</span>
                <strong class="kpi-value">{{ overview.activeUsersToday }}</strong>
                <span class="kpi-subtext">今日有操作行为用户</span>
              </div>
            </div>

            <div class="kpi-card">
              <div class="kpi-icon-box purple">
                <Layers :size="20" />
              </div>
              <div class="kpi-meta">
                <span class="kpi-label">需求总数</span>
                <strong class="kpi-value">{{ overview.totalTasks }}</strong>
                <span class="kpi-subtext">已创建的 PRD 项目需求</span>
              </div>
            </div>

            <div class="kpi-card">
              <div class="kpi-icon-box indigo">
                <Sparkles :size="20" />
              </div>
              <div class="kpi-meta">
                <span class="kpi-label">AI 执行总次数</span>
                <strong class="kpi-value">{{ overview.totalRuns }}</strong>
                <span class="kpi-subtext">成功 {{ overview.totalRunsSucceeded }} / 失败 {{ overview.totalRunsFailed }}</span>
              </div>
            </div>

            <div class="kpi-card">
              <div class="kpi-icon-box teal">
                <CheckCircle2 :size="20" />
              </div>
              <div class="kpi-meta">
                <span class="kpi-label">任务整体成功率</span>
                <strong class="kpi-value">{{ overview.successRate.toFixed(1) }}%</strong>
                <span class="kpi-subtext">Succeeded / Total Runs</span>
              </div>
            </div>

            <div class="kpi-card">
              <div class="kpi-icon-box red">
                <AlertOctagon :size="20" />
              </div>
              <div class="kpi-meta">
                <span class="kpi-label">生成与评审失败总计</span>
                <strong class="kpi-value">{{ overview.totalErrors }}</strong>
                <span class="kpi-subtext">PRD、POC、测试用例、技术方案、评审</span>
              </div>
            </div>
          </div>

          <!-- Quick Highlights Preview in Overview -->
          <div class="overview-dual-section">
            <div class="overview-box">
              <div class="overview-box-header">
                <h3>高频功能 TOP 5</h3>
                <button type="button" class="text-link" @click="currentTab = 'features'">查看全部 &rarr;</button>
              </div>
              <table class="simple-table">
                <thead>
                  <tr>
                    <th>功能模块</th>
                    <th>调用频次</th>
                    <th>成功率</th>
                    <th>平均耗时</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="feat in topFeatures" :key="feat.module + feat.action">
                    <td>
                      <span class="feat-badge">{{ formatModuleName(feat.module) }}</span>
                      <small class="action-tag">{{ feat.action }}</small>
                    </td>
                    <td><strong>{{ feat.count }}</strong></td>
                    <td>
                      <span :class="feat.successRate >= 80 ? 'text-success' : 'text-danger'">
                        {{ feat.successRate.toFixed(0) }}%
                      </span>
                    </td>
                    <td>{{ formatDuration(feat.avgDurationMs) }}</td>
                  </tr>
                  <tr v-if="topFeatures.length === 0">
                    <td colspan="4" class="empty-cell">暂无高频功能数据</td>
                  </tr>
                </tbody>
              </table>
            </div>

            <div class="overview-box">
              <div class="overview-box-header">
                <h3>最近生成与评审失败</h3>
                <button type="button" class="text-link" @click="currentTab = 'errors'">查看全部 &rarr;</button>
              </div>
              <div class="mini-error-list">
                <div v-for="err in recentErrors.slice(0, 5)" :key="err.id" class="mini-error-item">
                  <div class="mini-error-top">
                    <span class="error-badge">{{ formatModuleName(err.module) }} · {{ err.errorCode || 'UNKNOWN' }}</span>
                    <span class="error-user">@{{ err.username || '匿名' }}</span>
                    <span class="error-time">{{ formatTime(err.createdAt) }}</span>
                  </div>
                  <div class="mini-error-msg">{{ err.errorMessage || '无详细错误摘要' }}</div>
                </div>
                <div v-if="recentErrors.length === 0" class="empty-cell">暂无生成或评审失败记录</div>
              </div>
            </div>
          </div>
        </section>

        <!-- 2. USERS & PRIVILEGES TAB -->
        <section v-if="currentTab === 'users'" class="tab-pane pane-users">
          <div class="pane-toolbar">
            <div class="pane-toolbar-left">
              <div class="search-input-wrap">
                <Search :size="14" />
                <input v-model="userFilter" placeholder="搜索用户名或显示名..." />
              </div>
              <span class="count-hint">展示 {{ filteredUsers.length }} 位用户</span>
            </div>
            <button class="action-btn-primary" type="button" @click="openCreateUserModal">
              <UserPlus :size="14" />
              <span>新增用户</span>
            </button>
          </div>

          <div class="table-container">
            <table class="admin-data-table">
              <thead>
                <tr>
                  <th>用户</th>
                  <th>权限角色</th>
                  <th>注册时间</th>
                  <th>最近活跃</th>
                  <th>需求数</th>
                  <th>AI 执行数</th>
                  <th>生成与评审失败数</th>
                  <th>活跃状态</th>
                  <th style="text-align: right;">角色权限操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="u in filteredUsers" :key="u.userId">
                  <td>
                    <div class="user-cell">
                      <div class="user-avatar-circle">{{ (u.displayName || u.username)[0] }}</div>
                      <div>
                        <strong>{{ u.displayName || u.username }}</strong>
                        <small>@{{ u.username }}</small>
                      </div>
                    </div>
                  </td>
                  <td>
                    <span :class="u.role === 'ADMIN' ? 'badge-role-admin' : 'badge-role-user'">
                      {{ u.role === 'ADMIN' ? '超级管理员 (ADMIN)' : '普通用户 (USER)' }}
                    </span>
                  </td>
                  <td>{{ formatDateTime(u.registeredAt) }}</td>
                  <td>{{ u.lastActiveAt ? formatDateTime(u.lastActiveAt) : '未记录' }}</td>
                  <td><strong>{{ u.taskCount }}</strong></td>
                  <td><strong>{{ u.runCount }}</strong></td>
                  <td>
                    <span :class="u.errorCount > 0 ? 'text-danger' : 'text-muted'">
                      {{ u.errorCount }}
                    </span>
                  </td>
                  <td>
                    <span :class="isUserActiveRecently(u.lastActiveAt) ? 'active-dot' : 'inactive-dot'"></span>
                    <span style="margin-left: 6px; font-size: 12px;">
                      {{ isUserActiveRecently(u.lastActiveAt) ? '近期活跃' : '离线' }}
                    </span>
                  </td>
                  <td style="text-align: right;">
                    <span v-if="u.userId === user?.id" class="tag-current-user">
                      当前登录账号
                    </span>
                    <button
                      v-else-if="u.role === 'ADMIN'"
                      type="button"
                      class="btn-role-revoke"
                      :disabled="operatingUserId === u.userId"
                      @click="promptRoleChange(u, 'USER')"
                    >
                      <ShieldAlert :size="12" />
                      <span>{{ operatingUserId === u.userId ? "处理中..." : "撤销管理员" }}</span>
                    </button>
                    <button
                      v-else
                      type="button"
                      class="btn-role-grant"
                      :disabled="operatingUserId === u.userId"
                      @click="promptRoleChange(u, 'ADMIN')"
                    >
                      <ShieldCheck :size="12" />
                      <span>{{ operatingUserId === u.userId ? "处理中..." : "设为管理员" }}</span>
                    </button>
                  </td>
                </tr>
                <tr v-if="filteredUsers.length === 0">
                  <td colspan="9" class="empty-cell">无匹配用户记录</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <!-- 3. FEATURES TAB -->
        <section v-if="currentTab === 'features'" class="tab-pane pane-features">
          <div class="pane-toolbar">
            <span class="count-hint">各功能模块与衍生能力调用全量审计（近 30 天）</span>
          </div>

          <div class="table-container">
            <table class="admin-data-table">
              <thead>
                <tr>
                  <th>功能模块</th>
                  <th>操作行为</th>
                  <th>调用频次</th>
                  <th>成功次数</th>
                  <th>失败次数</th>
                  <th>成功率</th>
                  <th>平均耗时</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="feat in features" :key="feat.module + feat.action">
                  <td>
                    <span class="feat-badge">{{ formatModuleName(feat.module) }}</span>
                  </td>
                  <td>
                    <code>{{ feat.action }}</code>
                  </td>
                  <td><strong>{{ feat.count }}</strong></td>
                  <td><span class="text-success">{{ feat.successCount }}</span></td>
                  <td><span :class="feat.failCount > 0 ? 'text-danger' : 'text-muted'">{{ feat.failCount }}</span></td>
                  <td>
                    <div class="progress-cell">
                      <div class="progress-bar-bg">
                        <div
                          class="progress-bar-fill"
                          :style="{ width: `${feat.successRate.toFixed(1)}%` }"
                          :class="feat.successRate >= 80 ? 'fill-success' : 'fill-warn'"
                        ></div>
                      </div>
                      <span class="progress-text">{{ feat.successRate.toFixed(1) }}%</span>
                    </div>
                  </td>
                  <td>{{ formatDuration(feat.avgDurationMs) }}</td>
                </tr>
                <tr v-if="features.length === 0">
                  <td colspan="7" class="empty-cell">暂无功能使用数据</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <!-- 4. ERRORS TAB -->
        <section v-if="currentTab === 'errors'" class="tab-pane pane-errors">
          <div class="error-tab-layout">
            <!-- Left: Error Code Aggregation -->
            <div class="error-aggregate-col">
              <h3>生成与评审失败原因 (近 30 天)</h3>
              <table class="simple-table">
                <thead>
                  <tr>
                    <th>模块</th>
                    <th>错误类型 / 错误码</th>
                    <th>发生次数</th>
                    <th>最近时间</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="err in errorStats" :key="err.module + err.errorCode">
                    <td><span class="feat-badge">{{ formatModuleName(err.module) }}</span></td>
                    <td><code>{{ err.errorCode }}</code></td>
                    <td><strong class="text-danger">{{ err.count }}</strong></td>
                    <td><small>{{ formatTime(err.lastOccurredAt) }}</small></td>
                  </tr>
                  <tr v-if="errorStats.length === 0">
                    <td colspan="4" class="empty-cell">近 30 天没有生成或评审失败</td>
                  </tr>
                </tbody>
              </table>
            </div>

            <!-- Right: Realtime Error Log Stream -->
            <div class="error-stream-col">
              <h3>最近失败记录 ({{ recentErrors.length }} 条，最多 50 条)</h3>
              <div class="error-stream-list">
                <div v-for="item in recentErrors" :key="item.id" class="error-log-card">
                  <div class="error-log-head">
                    <span class="error-tag">{{ item.errorCode }}</span>
                    <span class="log-module-tag">{{ formatModuleName(item.module) }} · {{ item.taskTitle || item.taskId }}</span>
                    <span class="log-user">触发者: @{{ item.username || '匿名/系统' }}</span>
                    <span class="log-time">{{ formatDateTime(item.createdAt) }}</span>
                  </div>
                  <div class="error-log-content">
                    {{ item.errorMessage || '无详细错误信息' }}
                  </div>
                  <small>Run ID: {{ item.id }}</small>
                </div>
                <div v-if="recentErrors.length === 0" class="empty-cell">暂无生成或评审失败记录</div>
              </div>
            </div>
          </div>
        </section>

        <!-- 5. UPDATE LOGS TAB -->
        <section v-if="currentTab === 'logs'" class="tab-pane pane-logs">
          <div class="pane-toolbar">
            <div class="pane-toolbar-left">
              <div class="search-input-wrap">
                <Search :size="14" />
                <input v-model="logSearchKeyword" placeholder="搜索版本号或标题..." />
              </div>
              <div class="filter-select-wrap">
                <select v-model="logStatusFilter" class="admin-select">
                  <option value="">全部状态</option>
                  <option value="PUBLISHED">已发布</option>
                  <option value="DRAFT">草稿</option>
                </select>
              </div>
              <span class="count-hint">展示 {{ filteredLogs.length }} 个版本</span>
            </div>
            <div class="pane-toolbar-right" style="display: flex; gap: 8px;">
              <button class="action-btn-secondary" type="button" @click="showChangelogPreview = true">
                <Eye :size="14" />
                <span>前台样式预览</span>
              </button>
              <button class="action-btn-primary" type="button" @click="openCreateLogModal">
                <Plus :size="14" />
                <span>新建版本日志</span>
              </button>
            </div>
          </div>

          <div class="table-container">
            <table class="admin-data-table">
              <thead>
                <tr>
                  <th style="width: 100px;">版本号</th>
                  <th style="width: 105px;">发布日期</th>
                  <th>核心标题与概述</th>
                  <th style="width: 180px;">核心亮点</th>
                  <th style="width: 160px;">条目统计</th>
                  <th style="width: 90px;">状态</th>
                  <th style="width: 130px;">更新时间</th>
                  <th style="text-align: right; width: 220px;">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="log in filteredLogs" :key="log.id || log.version">
                  <td>
                    <div class="version-badge-cell">
                      <strong>{{ log.version }}</strong>
                      <span v-if="log.isLatest" class="badge-latest-tag">最新</span>
                    </div>
                  </td>
                  <td>
                    <span class="table-date">{{ log.releaseDate || log.date }}</span>
                  </td>
                  <td>
                    <div class="log-title-cell">
                      <strong class="log-cell-title">{{ log.title }}</strong>
                      <p class="log-cell-summary">{{ log.summary }}</p>
                    </div>
                  </td>
                  <td>
                    <div class="log-highlights-cell">
                      <span v-for="hl in (log.highlights || []).slice(0, 3)" :key="hl" class="highlight-chip">{{ hl }}</span>
                      <span v-if="(log.highlights || []).length > 3" class="highlight-more">+{{ (log.highlights || []).length - 3 }}</span>
                    </div>
                  </td>
                  <td>
                    <div class="items-stat-cell">
                      <span class="stat-pill-sm feature" title="新功能">{{ getItemTypeCount(log, 'feature') }} 功能</span>
                      <span class="stat-pill-sm improve" title="体验优化">{{ getItemTypeCount(log, 'improve') }} 优化</span>
                      <span class="stat-pill-sm fix" title="问题修复">{{ getItemTypeCount(log, 'fix') }} 修复</span>
                    </div>
                  </td>
                  <td>
                    <span :class="log.status === 'PUBLISHED' ? 'badge-status-published' : 'badge-status-draft'">
                      {{ log.status === 'PUBLISHED' ? '已发布' : '草稿' }}
                    </span>
                  </td>
                  <td>
                    <span class="table-date">{{ formatDateTime(log.updatedAt || log.createdAt) }}</span>
                  </td>
                  <td style="text-align: right;">
                    <div class="actions-cell">
                      <button class="table-action-btn" type="button" @click="openEditLogModal(log)">编辑</button>
                      <button
                        v-if="!log.isLatest"
                        class="table-action-btn accent"
                        type="button"
                        @click="handleSetLatest(log)"
                      >
                        设为最新
                      </button>
                      <button
                        class="table-action-btn"
                        type="button"
                        @click="handleToggleStatus(log)"
                      >
                        {{ log.status === 'PUBLISHED' ? '下架' : '发布' }}
                      </button>
                      <button
                        class="table-action-btn danger"
                        type="button"
                        @click="confirmDeleteLog(log)"
                      >
                        删除
                      </button>
                    </div>
                  </td>
                </tr>
                <tr v-if="filteredLogs.length === 0">
                  <td colspan="8" class="empty-cell">暂无匹配的更新日志记录</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </div>
    </div>

    <!-- Role Change Confirm Dialog -->
    <div v-if="roleChangeTarget" class="modal-backdrop" @click="cancelRoleChange">
      <div class="modal-card" @click.stop>
        <div class="modal-header">
          <h3>{{ roleChangeNewRole === 'ADMIN' ? '授予管理员权限' : '撤销管理员权限' }}</h3>
          <button class="modal-close-btn" type="button" @click="cancelRoleChange">
            <X :size="16" />
          </button>
        </div>
        <div class="modal-body">
          <p v-if="roleChangeNewRole === 'ADMIN'">
            确定要将用户 <strong>{{ roleChangeTarget.displayName || roleChangeTarget.username }}</strong> (@{{ roleChangeTarget.username }}) 设为 <strong>超级管理员</strong> 吗？
          </p>
          <p v-else>
            确定要撤销用户 <strong>{{ roleChangeTarget.displayName || roleChangeTarget.username }}</strong> (@{{ roleChangeTarget.username }}) 的管理员权限吗？
          </p>
          <p class="modal-hint">
            {{ roleChangeNewRole === 'ADMIN'
              ? '授权后，该用户登录工作台即可查看到「管理看板」入口并拥有全量运营、用户画像与监控大盘访问权限。'
              : '撤销后，该用户将无法再访问管理看板与管理接口，工作台管理入口也将被隐藏。' }}
          </p>
        </div>
        <div class="modal-footer">
          <button class="modal-btn-cancel" type="button" @click="cancelRoleChange">取消</button>
          <button
            class="modal-btn-confirm"
            :class="roleChangeNewRole === 'ADMIN' ? 'btn-confirm-grant' : 'btn-confirm-revoke'"
            type="button"
            :disabled="operatingUserId !== null"
            @click="confirmRoleChange"
          >
            {{ operatingUserId !== null ? "提交中..." : "确认变更" }}
          </button>
        </div>
      </div>
    </div>

    <!-- Create User Dialog -->
    <div v-if="showCreateUserModal" class="modal-backdrop" @click="closeCreateUserModal">
      <div class="modal-card" @click.stop>
        <div class="modal-header">
          <h3>新增系统用户</h3>
          <button class="modal-close-btn" type="button" @click="closeCreateUserModal">
            <X :size="16" />
          </button>
        </div>
        <form @submit.prevent="submitCreateUser">
          <div class="modal-body form-body">
            <div class="form-group">
              <label>用户名 <span class="required">*</span></label>
              <input
                v-model="newUserForm.username"
                placeholder="3-32 位字母、数字或下划线"
                maxlength="32"
                required
              />
            </div>
            <div class="form-group">
              <label>显示名</label>
              <input
                v-model="newUserForm.displayName"
                placeholder="例如：产品评审专家"
                maxlength="64"
              />
            </div>
            <div class="form-group">
              <label>初始密码 <span class="required">*</span></label>
              <input
                v-model="newUserForm.password"
                type="password"
                placeholder="至少 8 位字符"
                maxlength="4096"
                title="密码至少 8 个字符，UTF-8 编码不得超过 72 字节"
                required
              />
            </div>
            <div class="form-group">
              <label>权限角色 <span class="required">*</span></label>
              <div class="role-radio-group">
                <label class="radio-label">
                  <input v-model="newUserForm.role" type="radio" value="USER" />
                  <span>普通用户 (USER)</span>
                </label>
                <label class="radio-label">
                  <input v-model="newUserForm.role" type="radio" value="ADMIN" />
                  <span>超级管理员 (ADMIN)</span>
                </label>
              </div>
            </div>
            <p v-if="createUserError" class="form-error-msg">{{ createUserError }}</p>
          </div>
          <div class="modal-footer">
            <button class="modal-btn-cancel" type="button" @click="closeCreateUserModal">取消</button>
            <button class="modal-btn-confirm btn-confirm-grant" type="submit" :disabled="creatingUser">
              {{ creatingUser ? "正在创建..." : "确认创建" }}
            </button>
          </div>
        </form>
      </div>
    </div>

    <!-- Create / Edit Update Log Modal -->
    <div v-if="showLogModal" class="modal-backdrop" @click="closeLogModal">
      <div class="modal-card modal-card-lg" @click.stop>
        <div class="modal-header">
          <h3>{{ isEditLogMode ? `编辑版本日志 (${logForm.version})` : '新建版本更新日志' }}</h3>
          <button class="modal-close-btn" type="button" @click="closeLogModal">
            <X :size="16" />
          </button>
        </div>
        <form @submit.prevent="saveLog">
          <div class="modal-body form-body modal-scroll-body">
            <div class="form-row-dual">
              <div class="form-group">
                <label>版本号 <span class="required">*</span></label>
                <input
                  v-model="logForm.version"
                  placeholder="如：V0.3.9"
                  maxlength="32"
                  required
                />
              </div>
              <div class="form-group">
                <label>发布日期 <span class="required">*</span></label>
                <input
                  v-model="logForm.releaseDate"
                  placeholder="如：2026-09-19"
                  maxlength="16"
                  required
                />
              </div>
            </div>

            <div class="form-row-dual">
              <div class="form-group">
                <label>发布状态</label>
                <select v-model="logForm.status" class="admin-select" style="width: 100%; height: 38px;">
                  <option value="PUBLISHED">已发布 (PUBLISHED)</option>
                  <option value="DRAFT">草稿 (DRAFT)</option>
                </select>
              </div>
              <div class="form-group" style="display: flex; align-items: flex-end; padding-bottom: 8px;">
                <label class="checkbox-label">
                  <input v-model="logForm.isLatest" type="checkbox" />
                  <span>设为当前最新版本横幅高亮</span>
                </label>
              </div>
            </div>

            <div class="form-group">
              <label>版本核心标题 <span class="required">*</span></label>
              <input
                v-model="logForm.title"
                placeholder="简明扼要的版本主题，如：通用中后台 原型规范全面支持与独立评审"
                maxlength="255"
                required
              />
            </div>

            <div class="form-group">
              <label>版本摘要说明 <span class="required">*</span></label>
              <textarea
                v-model="logForm.summary"
                placeholder="详细说明本版本的核心迭代背景与解决的关键业务问题..."
                rows="3"
                required
              ></textarea>
            </div>

            <div class="form-group">
              <label>核心亮点标签 (逗号或空格分隔)</label>
              <input
                v-model="logForm.highlightsStr"
                placeholder="例如：通用中后台 原型规范, 独立 PRD 评审与 Demo, 详尽模式稳定性突破"
              />
            </div>

            <!-- 动态条目列表 -->
            <div class="form-group" style="margin-top: 18px;">
              <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px;">
                <label style="margin: 0; font-weight: 600;">具体变更条目 ({{ logForm.items.length }}) <span class="required">*</span></label>
                <button class="action-btn-secondary compact" type="button" @click="addLogItemRow">
                  <Plus :size="12" />
                  <span>添加条目</span>
                </button>
              </div>

              <div class="items-editor-container">
                <div v-for="(item, idx) in logForm.items" :key="idx" class="item-edit-row">
                  <div class="item-edit-header">
                    <span class="item-index">#{{ idx + 1 }}</span>
                    <select v-model="item.type" class="admin-select item-type-select">
                      <option value="feature">新功能 (Feature)</option>
                      <option value="improve">体验优化 (Improvement)</option>
                      <option value="fix">问题修复 (Bug Fix)</option>
                      <option value="security">安全更新 (Security)</option>
                    </select>
                    <input
                      v-model="item.title"
                      placeholder="条目标题（如：交互脚本语法自愈拦截）"
                      class="item-title-input"
                      required
                    />
                    <button
                      class="item-delete-btn"
                      type="button"
                      title="删除本条"
                      :disabled="logForm.items.length <= 1"
                      @click="removeLogItemRow(idx)"
                    >
                      <Trash2 :size="14" />
                    </button>
                  </div>
                  <textarea
                    v-model="item.detail"
                    placeholder="具体详细说明（如：在演示原型提交前引入脚本语法静态解析...）"
                    rows="2"
                    class="item-detail-textarea"
                    required
                  ></textarea>
                </div>
              </div>
            </div>

            <p v-if="logFormError" class="form-error-msg">{{ logFormError }}</p>
          </div>
          <div class="modal-footer">
            <button class="modal-btn-cancel" type="button" @click="closeLogModal">取消</button>
            <button class="modal-btn-confirm btn-confirm-grant" type="submit" :disabled="savingLog">
              {{ savingLog ? "正在保存..." : "保存更新日志" }}
            </button>
          </div>
        </form>
      </div>
    </div>

    <!-- Delete Update Log Confirm Dialog -->
    <div v-if="deleteLogTarget" class="modal-backdrop" @click="cancelDeleteLog">
      <div class="modal-card" @click.stop>
        <div class="modal-header">
          <h3>删除更新日志</h3>
          <button class="modal-close-btn" type="button" @click="cancelDeleteLog">
            <X :size="16" />
          </button>
        </div>
        <div class="modal-body">
          <p>
            确定要彻底删除版本 <strong>{{ deleteLogTarget.version }}</strong>（{{ deleteLogTarget.title }}）的更新日志吗？
          </p>
          <p class="modal-hint" style="color: var(--danger, #b63a48);">
            警告：删除后该版本记录将无法在前端展示或恢复。请确认是否继续。
          </p>
        </div>
        <div class="modal-footer">
          <button class="modal-btn-cancel" type="button" @click="cancelDeleteLog">取消</button>
          <button
            class="modal-btn-confirm btn-confirm-revoke"
            type="button"
            :disabled="deletingLog"
            @click="executeDeleteLog"
          >
            {{ deletingLog ? "正在删除..." : "确认删除" }}
          </button>
        </div>
      </div>
    </div>

    <!-- User-facing Changelog Modal Preview -->
    <ChangelogModal
      v-model="showChangelogPreview"
      :user-id="user?.id || user?.username"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import {
  Activity,
  AlertCircle,
  AlertOctagon,
  AlertTriangle,
  ArrowLeft,
  BarChart3,
  CheckCircle2,
  Layers,
  LayoutDashboard,
  Loader2,
  RotateCw,
  Search,
  Shield,
  ShieldAlert,
  ShieldCheck,
  Sparkles,
  UserPlus,
  Users,
  X,
  FileText,
  Plus,
  Eye,
  Trash2,
  Pencil,
} from "lucide-vue-next";
import {
  adminCreateUser,
  adminUpdateUserRole,
  getAdminDashboard,
  getAdminUpdateLogs,
  createAdminUpdateLog,
  updateAdminUpdateLog,
  deleteAdminUpdateLog,
  toggleAdminUpdateLogStatus,
  type AdminDashboardData,
  type AnalyticsOverview,
  type ErrorStat,
  type FeatureUsageStat,
  type RecentErrorLog,
  type UserAnalyticsProfile,
  type UpdateLogEntry,
  type UpdateLogItem,
} from "../api";
import { getUser } from "../session";
import ChangelogModal from "../components/ChangelogModal.vue";

const router = useRouter();
const user = getUser();

const currentTab = ref<"overview" | "users" | "features" | "errors" | "logs">("overview");
const loading = ref(false);
const loadError = ref("");
const dashboard = ref<AdminDashboardData | null>(null);
const userFilter = ref("");

// Toast notification
const toastMsg = ref("");
const toastType = ref<"success" | "error">("success");
let toastTimer: ReturnType<typeof setTimeout> | null = null;

function showToast(msg: string, type: "success" | "error" = "success") {
  toastMsg.value = msg;
  toastType.value = type;
  if (toastTimer) clearTimeout(toastTimer);
  toastTimer = setTimeout(() => {
    toastMsg.value = "";
  }, 3000);
}

// Role change state
const roleChangeTarget = ref<UserAnalyticsProfile | null>(null);
const roleChangeNewRole = ref<"ADMIN" | "USER">("ADMIN");
const operatingUserId = ref<string | null>(null);

function promptRoleChange(u: UserAnalyticsProfile, newRole: "ADMIN" | "USER") {
  roleChangeTarget.value = u;
  roleChangeNewRole.value = newRole;
}

function cancelRoleChange() {
  roleChangeTarget.value = null;
}

async function confirmRoleChange() {
  if (!roleChangeTarget.value) return;
  const target = roleChangeTarget.value;
  const newRole = roleChangeNewRole.value;
  operatingUserId.value = target.userId;
  try {
    await adminUpdateUserRole(target.userId, newRole);
    showToast(
      `成功将用户 @${target.username} 设为 ${newRole === "ADMIN" ? "超级管理员" : "普通用户"}`,
      "success"
    );
    cancelRoleChange();
    await loadData();
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : "更新角色权限失败";
    showToast(message, "error");
  } finally {
    operatingUserId.value = null;
  }
}

// Create user state
const showCreateUserModal = ref(false);
const creatingUser = ref(false);
const createUserError = ref("");
const newUserForm = ref({
  username: "",
  displayName: "",
  password: "",
  role: "USER" as "ADMIN" | "USER",
});

function openCreateUserModal() {
  newUserForm.value = {
    username: "",
    displayName: "",
    password: "",
    role: "USER",
  };
  createUserError.value = "";
  showCreateUserModal.value = true;
}

function closeCreateUserModal() {
  showCreateUserModal.value = false;
}

async function submitCreateUser() {
  createUserError.value = "";
  creatingUser.value = true;
  try {
    await adminCreateUser(newUserForm.value);
    showToast(`用户 @${newUserForm.value.username} 创建成功`, "success");
    closeCreateUserModal();
    await loadData();
  } catch (err: unknown) {
    createUserError.value = err instanceof Error ? err.message : "创建用户失败";
  } finally {
    creatingUser.value = false;
  }
}

const overview = computed<AnalyticsOverview>(() => {
  return (
    dashboard.value?.overview || {
      totalUsers: 0,
      activeUsersToday: 0,
      totalTasks: 0,
      totalRuns: 0,
      totalRunsSucceeded: 0,
      totalRunsFailed: 0,
      successRate: 0,
      totalErrors: 0,
    }
  );
});

const users = computed<UserAnalyticsProfile[]>(() => dashboard.value?.users || []);
const features = computed<FeatureUsageStat[]>(() => dashboard.value?.features || []);
const errorStats = computed<ErrorStat[]>(() => dashboard.value?.errorStats || []);
const recentErrors = computed<RecentErrorLog[]>(() => dashboard.value?.recentErrors || []);

const topFeatures = computed(() => {
  return [...features.value].sort((a, b) => b.count - a.count).slice(0, 5);
});

const filteredUsers = computed(() => {
  const query = userFilter.value.trim().toLowerCase();
  if (!query) return users.value;
  return users.value.filter(
    (u) =>
      u.username.toLowerCase().includes(query) ||
      (u.displayName && u.displayName.toLowerCase().includes(query))
  );
});

// Update log management state
const updateLogs = ref<UpdateLogEntry[]>([]);
const logSearchKeyword = ref("");
const logStatusFilter = ref("");
const showLogModal = ref(false);
const isEditLogMode = ref(false);
const editingLogId = ref<string | null>(null);
const savingLog = ref(false);
const logFormError = ref("");
const deleteLogTarget = ref<UpdateLogEntry | null>(null);
const deletingLog = ref(false);
const showChangelogPreview = ref(false);

const logForm = ref({
  version: "",
  releaseDate: "",
  title: "",
  summary: "",
  highlightsStr: "",
  isLatest: false,
  status: "PUBLISHED",
  items: [] as { title: string; detail: string; type: string; tone: string }[],
});

async function loadUpdateLogsList() {
  try {
    const data = await getAdminUpdateLogs();
    updateLogs.value = data || [];
  } catch (err: unknown) {
    console.warn("Failed to load admin update logs", err);
  }
}

const filteredLogs = computed(() => {
  const kw = logSearchKeyword.value.trim().toLowerCase();
  const st = logStatusFilter.value;
  return updateLogs.value.filter((l) => {
    if (st && l.status !== st) return false;
    if (!kw) return true;
    return (
      l.version.toLowerCase().includes(kw) ||
      (l.title && l.title.toLowerCase().includes(kw)) ||
      (l.summary && l.summary.toLowerCase().includes(kw))
    );
  });
});

function getItemTypeCount(log: UpdateLogEntry, type: string): number {
  return (log.items || []).filter((it) => {
    if (it.type === type) return true;
    if (!it.type) {
      if (type === "fix" && (it.title.includes("修复") || it.detail.includes("修复"))) return true;
      if (type === "improve" && (it.title.includes("优化") || it.detail.includes("优化"))) return true;
      if (type === "feature") return true;
    }
    return false;
  }).length;
}

function openCreateLogModal() {
  isEditLogMode.value = false;
  editingLogId.value = null;
  logFormError.value = "";
  const today = new Date().toISOString().slice(0, 10);
  logForm.value = {
    version: "",
    releaseDate: today,
    title: "",
    summary: "",
    highlightsStr: "",
    isLatest: false,
    status: "PUBLISHED",
    items: [
      {
        type: "feature",
        title: "",
        detail: "",
        tone: "accent",
      },
    ],
  };
  showLogModal.value = true;
}

function openEditLogModal(log: UpdateLogEntry) {
  isEditLogMode.value = true;
  editingLogId.value = log.id || null;
  logFormError.value = "";
  logForm.value = {
    version: log.version,
    releaseDate: log.releaseDate || log.date || "",
    title: log.title,
    summary: log.summary,
    highlightsStr: (log.highlights || []).join(", "),
    isLatest: !!log.isLatest,
    status: log.status || "PUBLISHED",
    items: (log.items || []).map((it) => ({
      type: it.type || "feature",
      title: it.title,
      detail: it.detail,
      tone: it.tone || "accent",
    })),
  };
  if (logForm.value.items.length === 0) {
    addLogItemRow();
  }
  showLogModal.value = true;
}

function closeLogModal() {
  showLogModal.value = false;
  logFormError.value = "";
}

function addLogItemRow() {
  logForm.value.items.push({
    type: "feature",
    title: "",
    detail: "",
    tone: "accent",
  });
}

function removeLogItemRow(idx: number) {
  if (logForm.value.items.length <= 1) return;
  logForm.value.items.splice(idx, 1);
}

async function saveLog() {
  logFormError.value = "";
  if (!logForm.value.version.trim()) {
    logFormError.value = "请填写版本号";
    return;
  }
  if (!logForm.value.releaseDate.trim()) {
    logFormError.value = "请填写发布日期";
    return;
  }
  if (!logForm.value.title.trim()) {
    logFormError.value = "请填写版本核心标题";
    return;
  }
  if (!logForm.value.summary.trim()) {
    logFormError.value = "请填写版本摘要说明";
    return;
  }
  const validItems = logForm.value.items
    .filter((it) => it.title.trim() && it.detail.trim())
    .map((it) => ({
      title: it.title.trim(),
      detail: it.detail.trim(),
      type: it.type || "feature",
      tone: it.tone || "accent",
    }));
  if (validItems.length === 0) {
    logFormError.value = "至少需要包含一条有效的变更条目（标题和详细说明均不能为空）";
    return;
  }

  const highlights = logForm.value.highlightsStr
    ? logForm.value.highlightsStr
        .split(/[,，\n]/)
        .map((s) => s.trim())
        .filter(Boolean)
    : [];

  const payload = {
    version: logForm.value.version.trim(),
    releaseDate: logForm.value.releaseDate.trim(),
    title: logForm.value.title.trim(),
    summary: logForm.value.summary.trim(),
    highlights,
    items: validItems,
    isLatest: logForm.value.isLatest,
    status: logForm.value.status,
  };

  savingLog.value = true;
  try {
    if (isEditLogMode.value && editingLogId.value) {
      await updateAdminUpdateLog(editingLogId.value, payload);
      showToast(`版本 ${payload.version} 更新成功`, "success");
    } else {
      await createAdminUpdateLog(payload);
      showToast(`版本 ${payload.version} 创建成功`, "success");
    }
    closeLogModal();
    await loadUpdateLogsList();
  } catch (err: unknown) {
    logFormError.value = err instanceof Error ? err.message : "保存更新日志失败";
  } finally {
    savingLog.value = false;
  }
}

async function handleSetLatest(log: UpdateLogEntry) {
  if (!log.id) return;
  try {
    await toggleAdminUpdateLogStatus(log.id, { isLatest: true });
    showToast(`已将 ${log.version} 设为当前最新版本`, "success");
    await loadUpdateLogsList();
  } catch (err: unknown) {
    showToast(err instanceof Error ? err.message : "操作失败", "error");
  }
}

async function handleToggleStatus(log: UpdateLogEntry) {
  if (!log.id) return;
  const newStatus = log.status === "PUBLISHED" ? "DRAFT" : "PUBLISHED";
  try {
    await toggleAdminUpdateLogStatus(log.id, { status: newStatus });
    showToast(newStatus === "PUBLISHED" ? `已发布 ${log.version}` : `已下架 ${log.version} 为草稿`, "success");
    await loadUpdateLogsList();
  } catch (err: unknown) {
    showToast(err instanceof Error ? err.message : "操作失败", "error");
  }
}

function confirmDeleteLog(log: UpdateLogEntry) {
  deleteLogTarget.value = log;
}

function cancelDeleteLog() {
  deleteLogTarget.value = null;
}

async function executeDeleteLog() {
  if (!deleteLogTarget.value?.id) return;
  const ver = deleteLogTarget.value.version;
  deletingLog.value = true;
  try {
    await deleteAdminUpdateLog(deleteLogTarget.value.id);
    showToast(`已成功删除版本 ${ver} 的更新日志`, "success");
    cancelDeleteLog();
    await loadUpdateLogsList();
  } catch (err: unknown) {
    showToast(err instanceof Error ? err.message : "删除失败", "error");
  } finally {
    deletingLog.value = false;
  }
}

async function loadData() {
  loading.value = true;
  loadError.value = "";
  try {
    const [data] = await Promise.all([
      getAdminDashboard(),
      loadUpdateLogsList()
    ]);
    dashboard.value = data;
  } catch (err: unknown) {
    loadError.value = err instanceof Error ? err.message : "获取管理看板数据失败，请确认权限";
  } finally {
    loading.value = false;
  }
}

function goWorkbench() {
  router.push("/");
}

function formatModuleName(mod: string): string {
  const map: Record<string, string> = {
    PRD: "PRD 生成",
    GENERATE: "PRD 生成",
    POC: "POC 原型",
    REVIEW: "智能评审",
    DEMO: "交互原型 Demo",
    ESTIMATE: "工时评估",
    TEST_CASE: "测试用例",
    TECH_SPEC: "技术方案",
    TASK_BUNDLE: "交付大礼包",
    BUNDLE: "交付大礼包",
    TASK: "需求管理",
    SOURCE: "材料上传",
    AUTH: "账号认证",
    USER: "用户与权限",
    USER_MGMT: "用户管理",
    SYSTEM: "系统底层",
  };
  return map[mod] || mod;
}

function formatDuration(ms: number): string {
  if (!ms || ms <= 0) return "-";
  if (ms < 1000) return `${ms}ms`;
  const sec = (ms / 1000).toFixed(1);
  if (ms < 60000) return `${sec}s`;
  const min = (ms / 60000).toFixed(1);
  return `${min}m`;
}

function formatDateTime(iso?: string | null): string {
  if (!iso) return "-";
  const d = new Date(iso);
  if (isNaN(d.getTime())) return iso;
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(
    d.getMinutes()
  )}`;
}

function formatTime(iso?: string | null): string {
  if (!iso) return "-";
  const d = new Date(iso);
  if (isNaN(d.getTime())) return iso;
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function pad(n: number): string {
  return n < 10 ? `0${n}` : String(n);
}

function isUserActiveRecently(lastActive: string | null): boolean {
  if (!lastActive) return false;
  const d = new Date(lastActive).getTime();
  return Date.now() - d < 24 * 60 * 60 * 1000;
}

onMounted(() => {
  loadData();
});
</script>

<style scoped>
.admin-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--page, #eef1f3);
  color: var(--ink, #1a2024);
  overflow: hidden;
  position: relative;
}

.admin-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: var(--topbar-height, 48px);
  padding: 0 16px;
  background: var(--surface, #ffffff);
  border-bottom: 1px solid var(--line, #e1e5e8);
  flex-shrink: 0;
}

.admin-topbar-left {
  display: flex;
  align-items: center;
  gap: 14px;
}

.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  font-size: 13px;
  border: 1px solid var(--line-strong, #c9d1d6);
  border-radius: 6px;
  background: var(--surface, #ffffff);
  cursor: pointer;
  color: var(--ink-soft, #4b555c);
  transition: all 0.15s ease;
}
.back-btn:hover {
  background: var(--surface-subtle, #f6f7f8);
  color: var(--ink, #1a2024);
}

.admin-title-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
}

.admin-brand-badge {
  background: #fcecef;
  color: #b63a48;
  font-size: 10px;
  font-weight: 700;
  padding: 2px 6px;
  border-radius: 4px;
  letter-spacing: 0.5px;
}

.admin-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--ink, #1a2024);
}

.admin-topbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.live-indicator {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #0f766e;
  background: #e5f3f1;
  padding: 3px 8px;
  border-radius: 12px;
}

.pulse-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #0f766e;
  animation: pulse 1.8s infinite;
}

@keyframes pulse {
  0% { transform: scale(0.95); opacity: 0.8; }
  50% { transform: scale(1.3); opacity: 1; }
  100% { transform: scale(0.95); opacity: 0.8; }
}

.refresh-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 12px;
  font-size: 12px;
  border: 1px solid var(--line-strong, #c9d1d6);
  border-radius: 6px;
  background: var(--surface, #ffffff);
  cursor: pointer;
  color: var(--ink-soft, #4b555c);
}
.refresh-btn:hover:not(:disabled) {
  background: var(--surface-subtle, #f6f7f8);
}

.admin-user-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 6px;
  background: #f0edfb;
  color: #6d4fc2;
  font-size: 12px;
  font-weight: 500;
}

/* Toast */
.admin-toast {
  position: absolute;
  top: 60px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 1000;
  padding: 10px 20px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 500;
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.12);
  animation: fadeIn 0.2s ease;
}
.admin-toast.success {
  background: #e5f3f1;
  color: #0f766e;
  border: 1px solid #99d5cf;
}
.admin-toast.error {
  background: #fcecef;
  color: #b63a48;
  border: 1px solid #f7c5cc;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translate(-50%, -10px); }
  to { opacity: 1; transform: translate(-50%, 0); }
}

.admin-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 16px 20px 20px;
  gap: 14px;
}

.admin-error-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  background: #fcecef;
  border: 1px solid #f7c5cc;
  border-radius: 8px;
  color: #b63a48;
  font-size: 13px;
}
.admin-error-banner button {
  margin-left: auto;
  padding: 3px 10px;
  background: #b63a48;
  color: #fff;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}

.admin-nav-tabs {
  display: flex;
  min-width: 0;
  overflow-x: auto;
  gap: 8px;
  border-bottom: 1px solid var(--line, #e1e5e8);
  padding-bottom: 2px;
  flex-shrink: 0;
}

.admin-nav-tab {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  font-size: 13px;
  font-weight: 500;
  color: var(--ink-soft, #4b555c);
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  cursor: pointer;
  border-radius: 6px 6px 0 0;
  transition: all 0.15s ease;
}
.admin-nav-tab:hover {
  color: var(--ink, #1a2024);
  background: rgba(0,0,0,0.02);
}
.admin-nav-tab.active {
  color: #0f766e;
  border-bottom-color: #0f766e;
  background: var(--surface, #ffffff);
}

.admin-loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  gap: 12px;
  color: var(--ink-faint, #657178);
}

.admin-tab-body {
  flex: 1;
  overflow-y: auto;
  padding-right: 4px;
}

.tab-pane {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* KPI GRID */
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 14px;
}

.kpi-card {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 16px;
  background: var(--surface, #ffffff);
  border: 1px solid var(--line, #e1e5e8);
  border-radius: 10px;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.02);
}

.kpi-icon-box {
  width: 42px;
  height: 42px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.kpi-icon-box.blue { background: #eaf0ff; color: #2563eb; }
.kpi-icon-box.green { background: #e6f7ec; color: #16a34a; }
.kpi-icon-box.purple { background: #f0edfb; color: #6d4fc2; }
.kpi-icon-box.indigo { background: #eff6ff; color: #4338ca; }
.kpi-icon-box.teal { background: #e5f3f1; color: #0f766e; }
.kpi-icon-box.red { background: #fcecef; color: #b63a48; }

.kpi-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.kpi-label {
  font-size: 12px;
  color: var(--ink-faint, #657178);
}

.kpi-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--ink, #1a2024);
  line-height: 1.2;
}

.kpi-subtext {
  font-size: 11px;
  color: var(--ink-faint, #657178);
}

/* OVERVIEW DUAL SECTION */
.overview-dual-section {
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: 16px;
}

.overview-box {
  background: var(--surface, #ffffff);
  border: 1px solid var(--line, #e1e5e8);
  border-radius: 10px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.overview-box-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.overview-box-header h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
}

.text-link {
  background: none;
  border: none;
  color: #0f766e;
  font-size: 12px;
  cursor: pointer;
}

.simple-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}
.simple-table th, .simple-table td {
  padding: 8px 10px;
  text-align: left;
  border-bottom: 1px solid var(--line, #e1e5e8);
}
.simple-table th {
  color: var(--ink-faint, #657178);
  font-weight: 500;
  background: var(--surface-subtle, #f6f7f8);
}

.feat-badge {
  display: inline-block;
  padding: 2px 6px;
  border-radius: 4px;
  background: #eaf0ff;
  color: #2563eb;
  font-weight: 500;
}

.action-tag {
  display: block;
  font-size: 10px;
  color: var(--ink-faint, #657178);
  margin-top: 2px;
}

.mini-error-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.mini-error-item {
  padding: 8px 10px;
  background: var(--surface-subtle, #f6f7f8);
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.mini-error-top {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
}

.error-badge {
  background: #fcecef;
  color: #b63a48;
  font-weight: 600;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 10px;
}

.error-user {
  color: var(--ink-soft, #4b555c);
}

.error-time {
  margin-left: auto;
  color: var(--ink-faint, #657178);
}

.mini-error-msg {
  font-size: 12px;
  color: var(--ink, #1a2024);
  word-break: break-all;
}

/* TABLES IN TABS */
.pane-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.pane-toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.search-input-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  background: var(--surface, #ffffff);
  border: 1px solid var(--line-strong, #c9d1d6);
  border-radius: 6px;
  width: 280px;
}
.search-input-wrap input {
  border: none;
  outline: none;
  background: none;
  font-size: 13px;
  width: 100%;
}

.count-hint {
  font-size: 12px;
  color: var(--ink-faint, #657178);
}

.action-btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  background: #0f766e;
  color: #ffffff;
  border: none;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s;
}
.action-btn-primary:hover {
  background: #0a5a55;
}

.table-container {
  background: var(--surface, #ffffff);
  border: 1px solid var(--line, #e1e5e8);
  border-radius: 8px;
  overflow: hidden;
}

.admin-data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.admin-data-table th, .admin-data-table td {
  padding: 10px 14px;
  text-align: left;
  border-bottom: 1px solid var(--line, #e1e5e8);
}
.admin-data-table th {
  background: var(--surface-subtle, #f6f7f8);
  color: var(--ink-faint, #657178);
  font-weight: 500;
  font-size: 12px;
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}
.user-avatar-circle {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #e5f3f1;
  color: #0f766e;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}
.user-cell small {
  display: block;
  color: var(--ink-faint, #657178);
  font-size: 11px;
}

.badge-role-admin {
  background: #f0edfb;
  color: #6d4fc2;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
}

.badge-role-user {
  background: var(--surface-subtle, #f6f7f8);
  color: var(--ink-soft, #4b555c);
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
}

.active-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #16a34a;
}
.inactive-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #c9d1d6;
}

.tag-current-user {
  display: inline-block;
  font-size: 11px;
  color: var(--ink-faint, #657178);
  background: var(--surface-subtle, #f6f7f8);
  padding: 3px 8px;
  border-radius: 4px;
  border: 1px dashed var(--line-strong, #c9d1d6);
}

.btn-role-grant {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  background: #e5f3f1;
  color: #0f766e;
  border: 1px solid #99d5cf;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}
.btn-role-grant:hover:not(:disabled) {
  background: #0f766e;
  color: #ffffff;
}

.btn-role-revoke {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  background: #fff4df;
  color: #a55c11;
  border: 1px solid #fed898;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}
.btn-role-revoke:hover:not(:disabled) {
  background: #a55c11;
  color: #ffffff;
}

.progress-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.progress-bar-bg {
  flex: 1;
  max-width: 100px;
  height: 6px;
  background: #e1e5e8;
  border-radius: 3px;
  overflow: hidden;
}
.progress-bar-fill {
  height: 100%;
}
.fill-success { background: #0f766e; }
.fill-warn { background: #eab308; }
.progress-text {
  font-size: 12px;
  min-width: 42px;
}

/* ERROR TAB LAYOUT */
.error-tab-layout {
  display: grid;
  grid-template-columns: 1fr 1.3fr;
  gap: 16px;
}

.error-aggregate-col, .error-stream-col {
  min-width: 0;
  background: var(--surface, #ffffff);
  border: 1px solid var(--line, #e1e5e8);
  border-radius: 10px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.error-aggregate-col {
  overflow-x: auto;
}

.log-module-tag {
  overflow-wrap: anywhere;
}

.error-aggregate-col h3, .error-stream-col h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
}

.error-stream-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 580px;
  overflow-y: auto;
}

.error-log-card {
  padding: 10px 12px;
  background: var(--surface-subtle, #f6f7f8);
  border: 1px solid var(--line, #e1e5e8);
  border-left: 3px solid #b63a48;
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.error-log-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 11px;
}

.error-tag {
  background: #fcecef;
  color: #b63a48;
  font-weight: 600;
  padding: 2px 6px;
  border-radius: 4px;
}

.log-module-tag {
  background: #eaf0ff;
  color: #2563eb;
  padding: 2px 6px;
  border-radius: 4px;
}

.log-user {
  color: var(--ink-soft, #4b555c);
}

.log-time {
  margin-left: auto;
  color: var(--ink-faint, #657178);
}

.error-log-content {
  font-size: 12px;
  color: var(--ink, #1a2024);
  line-height: 1.4;
  word-break: break-all;
}

.empty-cell {
  text-align: center;
  color: var(--ink-faint, #657178);
  padding: 20px !important;
}

.text-success { color: #16a34a; font-weight: 500; }
.text-danger { color: #b63a48; font-weight: 500; }
.text-muted { color: var(--ink-faint, #657178); }

.spinning {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  100% { transform: rotate(360deg); }
}

/* Modal */
.modal-backdrop {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(24, 32, 36, 0.45);
  backdrop-filter: blur(2px);
  z-index: 999;
  display: flex;
  align-items: center;
  justify-content: center;
}

.modal-card {
  width: 440px;
  max-width: 90vw;
  background: var(--surface, #ffffff);
  border-radius: 10px;
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.16);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  animation: modalPop 0.2s ease;
}

@keyframes modalPop {
  from { opacity: 0; transform: scale(0.95); }
  to { opacity: 1; transform: scale(1); }
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  border-bottom: 1px solid var(--line, #e1e5e8);
}
.modal-header h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.modal-close-btn {
  background: none;
  border: none;
  cursor: pointer;
  color: var(--ink-faint, #657178);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 4px;
}
.modal-close-btn:hover {
  color: var(--ink, #1a2024);
}

.modal-body {
  padding: 16px 18px;
  font-size: 13px;
  line-height: 1.5;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.modal-body p {
  margin: 0;
}
.modal-hint {
  color: var(--ink-faint, #657178);
  font-size: 12px;
}

.form-body {
  gap: 14px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.form-group label {
  font-size: 12px;
  font-weight: 500;
  color: var(--ink-soft, #4b555c);
}
.form-group .required {
  color: var(--danger, #b63a48);
}
.form-group input[type="text"],
.form-group input[type="password"],
.form-group input:not([type="radio"]) {
  padding: 7px 10px;
  border: 1px solid var(--line-strong, #c9d1d6);
  border-radius: 6px;
  font-size: 13px;
  background: var(--surface, #ffffff);
  color: var(--ink, #1a2024);
}
.form-group input:focus {
  outline: 2px solid #0f766e;
  border-color: transparent;
}

.role-radio-group {
  display: flex;
  gap: 16px;
  align-items: center;
}
.radio-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  cursor: pointer;
}

.form-error-msg {
  color: var(--danger, #b63a48);
  font-size: 12px;
}

.modal-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  padding: 12px 18px;
  border-top: 1px solid var(--line, #e1e5e8);
  background: var(--surface-subtle, #f6f7f8);
}

.modal-btn-cancel {
  padding: 6px 14px;
  background: var(--surface, #ffffff);
  border: 1px solid var(--line-strong, #c9d1d6);
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
  color: var(--ink-soft, #4b555c);
}
.modal-btn-cancel:hover {
  background: var(--surface-subtle, #f6f7f8);
}

.modal-btn-confirm {
  padding: 6px 14px;
  border: none;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  color: #ffffff;
}
.btn-confirm-grant {
  background: #0f766e;
}
.btn-confirm-grant:hover:not(:disabled) {
  background: #0a5a55;
}
.btn-confirm-revoke {
  background: #a55c11;
}
.btn-confirm-revoke:hover:not(:disabled) {
  background: #87490c;
}

/* Update log admin styling */
.badge-latest-tag {
  display: inline-flex;
  align-items: center;
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--accent, #2563eb);
  color: #ffffff;
  font-size: 11px;
  font-weight: 700;
  line-height: 1.2;
}

.version-badge-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.log-title-cell {
  max-width: 380px;
}

.log-cell-title {
  display: block;
  font-size: 13px;
  color: var(--ink, #1f2937);
  line-height: 1.4;
  margin-bottom: 2px;
}

.log-cell-summary {
  margin: 0;
  color: var(--ink-soft, #6b7280);
  font-size: 12px;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.log-highlights-cell {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.highlight-chip {
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--surface-subtle, #f3f4f6);
  border: 1px solid var(--line, #e5e7eb);
  color: var(--ink-soft, #4b5563);
  font-size: 11px;
  white-space: nowrap;
}

.highlight-more {
  padding: 1px 4px;
  color: var(--ink-subtle, #9ca3af);
  font-size: 11px;
}

.items-stat-cell {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.stat-pill-sm {
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
}

.stat-pill-sm.feature {
  color: #1d4ed8;
  background: #eff6ff;
}

.stat-pill-sm.improve {
  color: #6941c6;
  background: #f4f3ff;
}

.stat-pill-sm.fix {
  color: #b54708;
  background: #fffaeb;
}

.badge-status-published {
  display: inline-flex;
  padding: 2px 8px;
  border-radius: 99px;
  font-size: 12px;
  font-weight: 600;
  background: #ecfdf3;
  color: #067647;
}

.badge-status-draft {
  display: inline-flex;
  padding: 2px 8px;
  border-radius: 99px;
  font-size: 12px;
  font-weight: 600;
  background: #fffaeb;
  color: #b54708;
}

.actions-cell {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
}

.table-action-btn {
  padding: 3px 8px;
  border: 1px solid var(--line, #d1d5db);
  border-radius: 5px;
  background: var(--surface, #ffffff);
  color: var(--ink, #374151);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;
}

.table-action-btn:hover {
  background: var(--surface-subtle, #f9fafb);
  border-color: var(--ink-soft, #9ca3af);
}

.table-action-btn.accent {
  color: var(--accent, #2563eb);
  border-color: #bfdbfe;
  background: #eff6ff;
}

.table-action-btn.accent:hover {
  background: #dbeafe;
}

.table-action-btn.danger {
  color: #b42318;
  border-color: #fecdca;
  background: #fef3f2;
}

.table-action-btn.danger:hover {
  background: #fee4e2;
}

.modal-card-lg {
  max-width: 820px !important;
  width: 92% !important;
}

.modal-scroll-body {
  max-height: 72vh;
  overflow-y: auto;
  padding: 16px 20px;
}

.form-row-dual {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.checkbox-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--ink, #374151);
  cursor: pointer;
  user-select: none;
}

.items-editor-container {
  display: grid;
  gap: 10px;
  margin-top: 6px;
}

.item-edit-row {
  padding: 10px 12px;
  border: 1px solid var(--line, #e5e7eb);
  border-radius: 8px;
  background: var(--surface-subtle, #f9fafb);
  display: grid;
  gap: 8px;
}

.item-edit-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.item-index {
  font-size: 12px;
  font-weight: 700;
  color: var(--ink-subtle, #9ca3af);
  min-width: 24px;
}

.item-type-select {
  width: 140px;
  height: 32px;
  font-size: 12px;
  border: 1px solid var(--line-strong, #c9d1d6);
  border-radius: 6px;
  background: #fff;
  padding: 0 8px;
}

.item-title-input {
  flex: 1;
  height: 32px;
  border: 1px solid var(--line-strong, #c9d1d6);
  border-radius: 6px;
  padding: 0 10px;
  font-size: 13px;
  background: #fff;
}

.item-detail-textarea {
  width: 100%;
  border: 1px solid var(--line-strong, #c9d1d6);
  border-radius: 6px;
  padding: 8px 10px;
  font-size: 12px;
  resize: vertical;
  background: #fff;
  box-sizing: border-box;
}

.item-delete-btn {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border: 1px solid #fecdca;
  border-radius: 6px;
  background: #fef3f2;
  color: #b42318;
  cursor: pointer;
  transition: all 0.15s ease;
}

.item-delete-btn:hover:not(:disabled) {
  background: #fee4e2;
}

.item-delete-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.action-btn-secondary.compact {
  padding: 4px 10px;
  font-size: 12px;
  height: 28px;
  gap: 4px;
}

@media (max-width: 1024px) {
  .overview-dual-section {
    grid-template-columns: 1fr;
  }
  .error-tab-layout {
    grid-template-columns: 1fr;
  }
}
</style>
