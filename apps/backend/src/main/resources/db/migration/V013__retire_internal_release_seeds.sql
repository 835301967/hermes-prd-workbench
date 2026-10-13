-- Retire only the historical system seed records from V010.
-- They describe an internal lineage, not the unreleased public candidate.
-- Keep identities for migration traceability; do not delete user-authored entries.
UPDATE prd_update_log
SET title = '历史预置记录（不发布）',
    summary = '此条为历史迁移预置记录，不代表当前开源候选的能力或公开发布状态。请阅读当前候选说明和发布门禁。',
    highlights_json = '[]',
    items_json = '[]',
    is_latest = 0,
    status = 'DRAFT'
WHERE created_by = 'system'
  AND id IN ('log-v038', 'log-v037', 'log-v036', 'log-v035', 'log-v034',
             'log-v033', 'log-v032', 'log-v031', 'log-v030');
