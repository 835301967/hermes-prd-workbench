<template>
  <el-dialog
    v-model="visible"
    :show-close="true"
    width="1060px"
    top="4vh"
    class="official-changelog-dialog"
    :destroy-on-close="false"
    append-to-body
    @close="handleClose"
  >
    <div class="changelog-container">
      <!-- 页面顶部导航区 -->
      <div class="header-area">
        <div class="header-content">
          <div class="eyebrow">
            <span class="eyebrow-dot"></span>
            <span>产品动态</span>
          </div>
          <h1>更新日志</h1>
          <p class="page-subtitle">持续演进的产品功能、深度优化的交互体验与关键缺陷修复记录。</p>
        </div>
        <div class="header-actions">
          <button class="text-btn" type="button" @click="handleMarkAllRead">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="20 6 9 17 4 12" />
            </svg>
            全部已读
          </button>
        </div>
      </div>

      <!-- 最新版本高保真横幅 -->
      <div v-if="latestRelease" class="latest-card">
        <div class="latest-info">
          <div class="latest-topline">
            <span class="pill latest">最新版本</span>
            <span class="pill stable">{{ latestRelease.status === "CANDIDATE" ? "候选 · 未发布" : latestRelease.status === "PREVIEW" ? "预览版" : "已发布记录" }}</span>
            <span class="latest-version">{{ latestRelease.version }}</span>
            <span class="latest-date">{{ latestRelease.releaseDate || latestRelease.date }}</span>
          </div>
          <h2>{{ latestRelease.title }}</h2>
          <p>{{ latestRelease.summary }}</p>
        </div>
        <div class="latest-stats">
          <div class="stat">
            <strong>{{ latestStats.features }}</strong>
            <span>新功能</span>
          </div>
          <div class="stat">
            <strong>{{ latestStats.improves }}</strong>
            <span>项优化</span>
          </div>
          <div class="stat">
            <strong>{{ latestStats.fixes }}</strong>
            <span>问题修复</span>
          </div>
        </div>
      </div>

      <!-- 工具栏：分类标签与搜索 -->
      <div class="toolbar">
        <div class="tabs">
          <button
            type="button"
            class="tab"
            :class="{ active: selectedCategory === 'all' }"
            @click="selectedCategory = 'all'"
          >
            全部更新 ({{ allChangesCount }})
          </button>
          <button
            type="button"
            class="tab"
            :class="{ active: selectedCategory === 'feature' }"
            @click="selectedCategory = 'feature'"
          >
            新功能 ({{ categoryCounts.feature }})
          </button>
          <button
            type="button"
            class="tab"
            :class="{ active: selectedCategory === 'improve' }"
            @click="selectedCategory = 'improve'"
          >
            体验优化 ({{ categoryCounts.improve }})
          </button>
          <button
            type="button"
            class="tab"
            :class="{ active: selectedCategory === 'fix' }"
            @click="selectedCategory = 'fix'"
          >
            问题修复 ({{ categoryCounts.fix }})
          </button>
          <button
            type="button"
            class="tab"
            :class="{ active: selectedCategory === 'security' }"
            @click="selectedCategory = 'security'"
          >
            安全更新 ({{ categoryCounts.security }})
          </button>
        </div>

        <div class="search">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="8" />
            <line x1="21" y1="21" x2="16.65" y2="16.65" />
          </svg>
          <input
            v-model="searchQuery"
            type="text"
            placeholder="搜索版本号、功能或修复内容..."
          />
          <button
            v-if="searchQuery"
            type="button"
            class="search-clear"
            @click="searchQuery = ''"
          >
            &times;
          </button>
        </div>
      </div>

      <!-- 主双列网格布局 -->
      <div class="layout-grid">
        <!-- 左侧时间轴卡片列表 -->
        <div class="timeline">
          <div
            v-for="rel in filteredReleases"
            :key="rel.version"
            class="release"
            :class="{ highlight: rel.isLatest || rel.version === latestRelease?.version }"
          >
            <div class="release-header">
              <div>
                <div class="release-title-row">
                  <h3 class="version">{{ rel.version }}</h3>
                  <span class="release-name">{{ rel.title }}</span>
                  <span v-if="rel.isLatest || rel.version === latestRelease?.version" class="pill latest">最新</span>
                  <span v-else class="pill stable">已发布记录</span>
                </div>
                <div class="release-meta">
                  <span>
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <rect x="3" y="4" width="18" height="18" rx="2" ry="2" />
                      <line x1="16" y1="2" x2="16" y2="6" />
                      <line x1="8" y1="2" x2="8" y2="6" />
                      <line x1="3" y1="10" x2="21" y2="10" />
                    </svg>
                    {{ rel.releaseDate || rel.date }}
                  </span>
                  <span>
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <circle cx="12" cy="12" r="10" />
                      <polyline points="12 6 12 12 16 14" />
                    </svg>
                    约 {{ calculateReadTime(rel.items) }} 分钟阅读
                  </span>
                  <span>共 {{ rel.items.length }} 项更新</span>
                </div>
              </div>
            </div>

            <p class="release-summary">{{ rel.summary }}</p>

            <!-- 变更条目网格 -->
            <div class="changes">
              <div
                v-for="(item, idx) in getDisplayItems(rel)"
                :key="idx"
                class="change"
                :class="resolveType(item)"
              >
                <div class="change-icon">
                  <svg v-if="resolveType(item) === 'feature'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M12 5v14M5 12h14" />
                  </svg>
                  <svg v-else-if="resolveType(item) === 'improve'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M23 6l-9.5 9.5-5-5L1 18" />
                    <path d="M17 6h6v6" />
                  </svg>
                  <svg v-else-if="resolveType(item) === 'fix'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
                    <polyline points="22 4 12 14.01 9 11.01" />
                  </svg>
                  <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
                  </svg>
                </div>
                <div class="change-body">
                  <h4>
                    {{ item.title }}
                    <span class="change-label">{{ resolveTypeLabel(item) }}</span>
                  </h4>
                  <p>{{ item.detail }}</p>
                </div>
              </div>
            </div>

            <!-- 展开更多条目按钮 -->
            <button
              v-if="hasMoreItems(rel)"
              type="button"
              class="expand-btn"
              :aria-expanded="isExpanded(rel.version)"
              @click="toggleExpand(rel.version)"
            >
              <span>{{ isExpanded(rel.version) ? '收起更多项' : `查看全部 ${rel.items.length} 项更新` }}</span>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="6 9 12 15 18 9" />
              </svg>
            </button>
          </div>

          <!-- 空结果提示 -->
          <div v-if="filteredReleases.length === 0" class="empty">
            <div class="empty-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="11" cy="11" r="8" />
                <line x1="21" y1="21" x2="16.65" y2="16.65" />
              </svg>
            </div>
            <h3>未找到相关更新记录</h3>
            <p>请尝试调整搜索关键词或切换顶部的类型筛选标签。</p>
            <button type="button" class="text-btn" style="margin-top: 14px" @click="resetFilters">
              清除筛选条件
            </button>
          </div>
        </div>

        <!-- 右侧侧边栏面板 -->
        <div class="side-panel">
          <!-- 当前版本状态 -->
          <div class="panel-card">
            <h4 class="panel-title">当前版本状态</h4>
            <div class="current-version">
              <strong>{{ latestRelease?.version || 'V0.3.10' }}</strong>
              <span>Hermes PRD 工作台</span>
            </div>
            <div class="status-row">
              <span class="status">
                <span class="status-dot"></span>
                运行正常
              </span>
              <span class="panel-note">已接入最新基准</span>
            </div>
          </div>

          <!-- 图例看板 -->
          <div class="panel-card">
            <h4 class="panel-title">更新类型图例</h4>
            <div class="legend">
              <div class="legend-item">
                <span class="legend-dot feature"></span>
                <span>新功能 (Feature)</span>
              </div>
              <div class="legend-item">
                <span class="legend-dot improve"></span>
                <span>体验优化 (Improvement)</span>
              </div>
              <div class="legend-item">
                <span class="legend-dot fix"></span>
                <span>问题修复 (Bug Fix)</span>
              </div>
              <div class="legend-item">
                <span class="legend-dot security"></span>
                <span>安全更新 (Security)</span>
              </div>
            </div>
          </div>

          <!-- 更新通知卡片 -->
          <div class="panel-card">
            <h4 class="panel-title">更新通知</h4>
            <p class="subscribe-copy">新版本上线时将在工作台顶栏智能提醒，重要更新即刻感知。</p>
            <button
              type="button"
              class="subscribe-btn"
              :class="{ subscribed: isSubscribed }"
              @click="toggleSubscribe"
            >
              {{ isSubscribed ? '已开启更新提醒' : '开启桌面通知' }}
            </button>
          </div>
        </div>
      </div>

      <!-- 底部版权与免责 -->
      <div class="footer-note">
        {{ footerNote }}
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import { getPublishedUpdateLogs, type UpdateLogEntry, type UpdateLogItem } from "../api";
import {
  CURRENT_UPDATE_LOG,
  UPDATE_LOG_FOOTER,
  updateLogSeenKey
} from "../updateLog";

const props = defineProps<{
  modelValue: boolean;
  userId?: string | null;
}>();

const emit = defineEmits<{
  (e: "update:modelValue", val: boolean): void;
  (e: "seen"): void;
}>();

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit("update:modelValue", val)
});

const footerNote = UPDATE_LOG_FOOTER;

// 本地静态备选全量列表（离线容灾兜底）
const fallbackReleases: UpdateLogEntry[] = [
  CURRENT_UPDATE_LOG,
];

const releases = ref<UpdateLogEntry[]>([...fallbackReleases]);
const latestRelease = ref<UpdateLogEntry | null>(CURRENT_UPDATE_LOG);
const expandedVersions = ref<Record<string, boolean>>({});
const selectedCategory = ref<"all" | "feature" | "improve" | "fix" | "security">("all");
const searchQuery = ref("");
const isSubscribed = ref(true);

// 加载后端数据库中的更新日志
async function loadUpdateLogs() {
  try {
    const res = await getPublishedUpdateLogs();
    if (res && res.history && res.history.length > 0) {
      releases.value = res.history;
      latestRelease.value = res.latest || res.history[0];
    } else {
      releases.value = [...fallbackReleases];
      latestRelease.value = CURRENT_UPDATE_LOG;
    }
  } catch {
    // 降级使用本地静态数据
    releases.value = [...fallbackReleases];
    latestRelease.value = CURRENT_UPDATE_LOG;
  }
}

watch(
  () => props.modelValue,
  (val) => {
    if (val) {
      loadUpdateLogs();
    }
  },
  { immediate: true }
);

function handleClose() {
  visible.value = false;
}

function handleMarkAllRead() {
  try {
    localStorage.setItem(updateLogSeenKey(props.userId), CURRENT_UPDATE_LOG.version);
    emit("seen");
    ElMessage.success("已标记全部更新为已读");
  } catch {
    // ignore storage issue
  }
}

function resetFilters() {
  searchQuery.value = "";
  selectedCategory.value = "all";
}

function toggleSubscribe() {
  isSubscribed.value = !isSubscribed.value;
  ElMessage.success(isSubscribed.value ? "已开启版本更新通知" : "已关闭版本更新通知");
}

function toggleExpand(ver: string) {
  expandedVersions.value[ver] = !expandedVersions.value[ver];
}

function isExpanded(ver: string): boolean {
  return !!expandedVersions.value[ver];
}

function resolveType(item: UpdateLogItem): "feature" | "improve" | "fix" | "security" {
  if (item.type && ["feature", "improve", "fix", "security"].includes(item.type)) {
    return item.type as "feature" | "improve" | "fix" | "security";
  }
  const text = (item.title + " " + item.detail).toLowerCase();
  if (text.includes("安全") || text.includes("拦截") || text.includes("权限") || text.includes("沙箱")) {
    return "security";
  }
  if (text.includes("修复") || text.includes("解决") || text.includes("杜绝") || text.includes("缺陷") || text.includes("失败")) {
    return "fix";
  }
  if (text.includes("优化") || text.includes("提升") || text.includes("重塑") || text.includes("舒展") || text.includes("平滑")) {
    return "improve";
  }
  if (item.tone === "amber") return "fix";
  if (item.tone === "blue") return "improve";
  return "feature";
}

function resolveTypeLabel(item: UpdateLogItem): string {
  const t = resolveType(item);
  switch (t) {
    case "feature":
      return "新功能";
    case "improve":
      return "体验优化";
    case "fix":
      return "问题修复";
    case "security":
      return "安全更新";
  }
}

function calculateReadTime(items: UpdateLogItem[]): number {
  return Math.max(1, Math.round((items.length * 40) / 60));
}

// 统计最新版本三项计数
const latestStats = computed(() => {
  const items = latestRelease.value?.items || [];
  let features = 0;
  let improves = 0;
  let fixes = 0;
  items.forEach((it) => {
    const t = resolveType(it);
    if (t === "feature") features++;
    else if (t === "improve") improves++;
    else if (t === "fix" || t === "security") fixes++;
  });
  return { features, improves, fixes };
});

// 全量变更条目计数
const allChangesCount = computed(() => {
  return releases.value.reduce((sum, r) => sum + (r.items?.length || 0), 0);
});

// 各分类计数
const categoryCounts = computed(() => {
  const counts = { feature: 0, improve: 0, fix: 0, security: 0 };
  releases.value.forEach((r) => {
    (r.items || []).forEach((it) => {
      const t = resolveType(it);
      if (counts[t] !== undefined) {
        counts[t]++;
      }
    });
  });
  return counts;
});

// 筛选匹配的版本列表
const filteredReleases = computed(() => {
  const q = searchQuery.value.trim().toLowerCase();
  const cat = selectedCategory.value;

  return releases.value
    .map((r) => {
      let filteredItems = (r.items || []).filter((it) => {
        if (cat !== "all" && resolveType(it) !== cat) {
          return false;
        }
        if (!q) return true;
        return (
          it.title.toLowerCase().includes(q) ||
          it.detail.toLowerCase().includes(q) ||
          r.version.toLowerCase().includes(q)
        );
      });

      // 如果搜索匹配的是版本标题或概述，但在特定分类下无条目时，若为all则保留全部条目
      if (filteredItems.length === 0 && q) {
        const matchesRelease =
          r.version.toLowerCase().includes(q) ||
          r.title.toLowerCase().includes(q) ||
          r.summary.toLowerCase().includes(q);
        if (matchesRelease) {
          filteredItems = (r.items || []).filter((it) => {
            return cat === "all" || resolveType(it) === cat;
          });
        }
      }

      return {
        ...r,
        filteredItems
      };
    })
    .filter((r) => r.filteredItems.length > 0);
});

function getDisplayItems(rel: any): UpdateLogItem[] {
  const items = rel.filteredItems || rel.items || [];
  if (isExpanded(rel.version) || items.length <= 3) {
    return items;
  }
  return items.slice(0, 3);
}

function hasMoreItems(rel: any): boolean {
  const items = rel.filteredItems || rel.items || [];
  return items.length > 3;
}
</script>

<style scoped>
/* 严格还原 the reference layout 的核心设计语言与变量 */
.changelog-container {
  --primary: #2563eb;
  --primary-hover: #1d4ed8;
  --primary-soft: #eff6ff;
  --primary-line: #bfdbfe;
  --green: #067647;
  --green-soft: #ecfdf3;
  --amber: #b54708;
  --amber-soft: #fffaeb;
  --red: #b42318;
  --red-soft: #fef3f2;
  --violet: #6941c6;
  --violet-soft: #f4f3ff;
  --surface: #ffffff;
  --surface-soft: #f8fafc;
  --surface-strong: #eef3f9;
  --text: #172033;
  --muted: #667085;
  --subtle: #98a2b3;
  --border: #e4e9f0;
  --border-strong: #d6dde8;
  --shadow-sm: 0 1px 2px rgba(16, 24, 40, 0.04);
  --shadow-md: 0 10px 28px rgba(16, 24, 40, 0.08);
  --radius: 14px;

  color: var(--text);
  font-family: Inter, "PingFang SC", "Microsoft YaHei", system-ui, -apple-system, sans-serif;
  line-height: 1.6;
  max-height: 82vh;
  overflow-y: auto;
  padding: 8px 12px 24px;
}

/* 顶部区域 */
.header-area {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 22px;
}

.eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 3px 10px;
  border-radius: 99px;
  background: var(--surface-strong);
  color: var(--primary);
  font-size: 0.76rem;
  font-weight: 700;
  letter-spacing: 0.04em;
  margin-bottom: 8px;
}

.eyebrow-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--primary);
  box-shadow: 0 0 0 4px var(--primary-soft);
  animation: pulse 2s infinite ease-in-out;
}

@keyframes pulse {
  0% { box-shadow: 0 0 0 2px var(--primary-soft); }
  50% { box-shadow: 0 0 0 5px rgba(37, 99, 235, 0.25); }
  100% { box-shadow: 0 0 0 2px var(--primary-soft); }
}

h1 {
  margin: 0;
  color: var(--text);
  font-size: 1.75rem;
  line-height: 1.3;
  letter-spacing: -0.025em;
  font-weight: 800;
}

.page-subtitle {
  max-width: 620px;
  margin: 6px 0 0;
  color: var(--muted);
  font-size: 0.92rem;
}

.text-btn {
  min-height: 36px;
  padding: 0 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  border: 1px solid var(--border-strong);
  border-radius: 8px;
  background: var(--surface);
  color: var(--text);
  font-size: 0.84rem;
  font-weight: 600;
  box-shadow: var(--shadow-sm);
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.18s ease;
}

.text-btn:hover {
  border-color: var(--primary);
  color: var(--primary);
}

.text-btn svg {
  width: 15px;
  height: 15px;
}

/* 最新版本高保真横幅 */
.latest-card {
  position: relative;
  overflow: hidden;
  padding: 22px 24px;
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 20px;
  align-items: center;
  border: 1px solid var(--primary-line);
  border-radius: 14px;
  background: var(--surface);
  box-shadow: var(--shadow-md);
  margin-bottom: 20px;
}

.latest-card::before {
  content: "";
  position: absolute;
  inset: 0 auto 0 0;
  width: 4px;
  background: var(--primary);
}

.latest-topline {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}

.pill {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 1px 8px;
  border-radius: 99px;
  font-size: 0.74rem;
  font-weight: 700;
}

.pill.latest {
  color: #fff;
  background: var(--primary);
}

.pill.stable {
  color: var(--green);
  background: var(--green-soft);
}

.latest-version {
  font-size: 0.88rem;
  font-weight: 700;
  color: var(--text);
}

.latest-date {
  color: var(--subtle);
  font-size: 0.8rem;
}

.latest-card h2 {
  margin: 0;
  font-size: 1.25rem;
  line-height: 1.4;
  letter-spacing: -0.015em;
  font-weight: 700;
}

.latest-card p {
  max-width: 680px;
  margin: 6px 0 0;
  color: var(--muted);
  font-size: 0.88rem;
  line-height: 1.55;
}

.latest-stats {
  display: flex;
  gap: 8px;
}

.stat {
  min-width: 72px;
  padding: 8px 10px;
  border: 1px solid var(--border);
  border-radius: 9px;
  background: var(--surface-soft);
  text-align: center;
}

.stat strong {
  display: block;
  color: var(--text);
  font-size: 1.15rem;
  line-height: 1.2;
}

.stat span {
  color: var(--muted);
  font-size: 0.72rem;
}

/* 工具栏与筛选器 */
.toolbar {
  margin: 18px 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.tabs {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px;
  border: 1px solid var(--border);
  border-radius: 9px;
  background: var(--surface);
  overflow-x: auto;
}

.tab {
  min-height: 32px;
  padding: 0 11px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--muted);
  font-size: 0.82rem;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.18s ease;
}

.tab:hover {
  color: var(--text);
}

.tab.active {
  color: var(--primary);
  background: var(--primary-soft);
}

.search {
  position: relative;
  width: min(280px, 100%);
}

.search svg {
  position: absolute;
  top: 50%;
  left: 11px;
  width: 16px;
  height: 16px;
  color: var(--subtle);
  transform: translateY(-50%);
  pointer-events: none;
}

.search input {
  width: 100%;
  height: 38px;
  padding: 0 32px 0 34px;
  border: 1px solid var(--border-strong);
  border-radius: 9px;
  outline: none;
  background: var(--surface);
  color: var(--text);
  font-size: 0.84rem;
  box-shadow: var(--shadow-sm);
  transition: all 0.18s ease;
}

.search input:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px var(--primary-soft);
}

.search-clear {
  position: absolute;
  top: 50%;
  right: 8px;
  width: 22px;
  height: 22px;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--muted);
  cursor: pointer;
  transform: translateY(-50%);
  font-size: 16px;
  line-height: 1;
}

/* 主双列网格 */
.layout-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 240px;
  gap: 20px;
  align-items: start;
}

/* 时间轴卡片 */
.timeline {
  position: relative;
}

.release {
  position: relative;
  margin-bottom: 16px;
  padding: 20px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--surface);
  box-shadow: var(--shadow-sm);
  transition: all 0.18s ease;
}

.release:hover {
  border-color: var(--border-strong);
  box-shadow: 0 6px 20px rgba(16, 24, 40, 0.05);
}

.release.highlight {
  border-color: var(--primary-line);
}

.release-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.release-title-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.version {
  margin: 0;
  font-size: 1.1rem;
  letter-spacing: -0.015em;
  font-weight: 700;
  color: var(--text);
}

.release-name {
  color: var(--muted);
  font-size: 0.88rem;
  font-weight: 600;
}

.release-meta {
  margin-top: 5px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  color: var(--subtle);
  font-size: 0.78rem;
}

.release-meta span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.release-meta svg {
  width: 14px;
  height: 14px;
}

.release-summary {
  margin: 14px 0 16px;
  color: var(--muted);
  font-size: 0.88rem;
  line-height: 1.6;
}

.changes {
  display: grid;
  gap: 12px;
}

.change {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  gap: 10px;
  align-items: start;
}

.change-icon {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border-radius: 7px;
  margin-top: 1px;
}

.change-icon svg {
  width: 15px;
  height: 15px;
}

.change.feature .change-icon {
  color: var(--primary);
  background: var(--primary-soft);
}

.change.improve .change-icon {
  color: var(--violet);
  background: var(--violet-soft);
}

.change.fix .change-icon {
  color: var(--amber);
  background: var(--amber-soft);
}

.change.security .change-icon {
  color: var(--red);
  background: var(--red-soft);
}

.change h4 {
  margin: 0 0 2px;
  color: var(--text);
  font-size: 0.88rem;
  line-height: 1.45;
  font-weight: 650;
}

.change p {
  margin: 0;
  color: var(--muted);
  font-size: 0.82rem;
  line-height: 1.55;
}

.change-label {
  display: inline-flex;
  margin-left: 6px;
  vertical-align: 1px;
  color: var(--subtle);
  font-size: 0.7rem;
  font-weight: 600;
}

.expand-btn {
  width: 100%;
  margin-top: 14px;
  padding-top: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  border: 0;
  border-top: 1px solid var(--border);
  background: transparent;
  color: var(--primary);
  font-size: 0.82rem;
  font-weight: 650;
  cursor: pointer;
  transition: color 0.18s ease;
}

.expand-btn:hover {
  color: var(--primary-hover);
}

.expand-btn svg {
  width: 15px;
  height: 15px;
  transition: transform 0.18s ease;
}

.expand-btn[aria-expanded="true"] svg {
  transform: rotate(180deg);
}

/* 空状态 */
.empty {
  padding: 48px 24px;
  border: 1px dashed var(--border-strong);
  border-radius: var(--radius);
  background: var(--surface);
  text-align: center;
}

.empty-icon {
  width: 40px;
  height: 40px;
  margin: 0 auto 10px;
  display: grid;
  place-items: center;
  border-radius: 10px;
  background: var(--surface-strong);
  color: var(--muted);
}

.empty-icon svg {
  width: 20px;
  height: 20px;
}

.empty h3 {
  margin: 0;
  font-size: 0.95rem;
  color: var(--text);
}

.empty p {
  margin: 6px 0 0;
  color: var(--muted);
  font-size: 0.84rem;
}

/* 侧边面板 */
.side-panel {
  position: sticky;
  top: 8px;
  display: grid;
  gap: 14px;
}

.panel-card {
  padding: 16px;
  border: 1px solid var(--border);
  border-radius: 11px;
  background: var(--surface);
  box-shadow: var(--shadow-sm);
}

.panel-title {
  margin: 0 0 12px;
  font-size: 0.82rem;
  font-weight: 700;
  color: var(--text);
}

.current-version {
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border);
}

.current-version strong {
  display: block;
  font-size: 1.35rem;
  letter-spacing: -0.02em;
  color: var(--text);
}

.current-version span {
  color: var(--muted);
  font-size: 0.78rem;
}

.status-row {
  margin-top: 10px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 0.78rem;
}

.status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--green);
  font-weight: 700;
}

.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--green);
}

.panel-note {
  color: var(--subtle);
}

.legend {
  display: grid;
  gap: 9px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--muted);
  font-size: 0.78rem;
}

.legend-dot {
  width: 8px;
  height: 8px;
  border-radius: 2px;
}

.legend-dot.feature {
  background: var(--primary);
}

.legend-dot.improve {
  background: var(--violet);
}

.legend-dot.fix {
  background: var(--amber);
}

.legend-dot.security {
  background: var(--red);
}

.subscribe-copy {
  margin: -2px 0 12px;
  color: var(--muted);
  font-size: 0.78rem;
  line-height: 1.5;
}

.subscribe-btn {
  width: 100%;
  min-height: 36px;
  border: 0;
  border-radius: 8px;
  background: var(--primary);
  color: #fff;
  font-size: 0.82rem;
  font-weight: 650;
  cursor: pointer;
  transition: all 0.18s ease;
}

.subscribe-btn:hover {
  background: var(--primary-hover);
}

.subscribe-btn.subscribed {
  background: var(--green);
}

.footer-note {
  margin-top: 24px;
  color: var(--subtle);
  font-size: 0.76rem;
  text-align: center;
}
</style>
