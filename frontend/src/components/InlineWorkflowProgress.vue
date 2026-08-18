<script setup>
import { computed, ref } from 'vue'
import { Check, ChevronDown, Circle, ListTodo, LoaderCircle, RotateCcw, TriangleAlert, X } from 'lucide-vue-next'

const props = defineProps({ actions: { type: Array, default: () => [] } })
const expanded = ref(true)

const workflow = computed(() => {
  for (let index = props.actions.length - 1; index >= 0; index -= 1) {
    const action = props.actions[index]
    if (action?.type === 'SHOW_WORKFLOW_PROGRESS' && action.workflow) return action.workflow
  }
  return null
})

const completed = computed(() => workflow.value?.tasks?.filter(task => (
  task.status === 'COMPLETED' || task.status === 'SKIPPED'
)).length || 0)

function iconFor(status) {
  if (status === 'COMPLETED') return Check
  if (status === 'RUNNING' || status === 'VERIFYING') return LoaderCircle
  if (status === 'RETRYING') return RotateCcw
  if (status === 'FAILED') return X
  if (status === 'SKIPPED') return Check
  return Circle
}
</script>

<template>
  <section v-if="workflow" class="workflow-card" :class="workflow.status.toLowerCase()">
    <button class="workflow-header" type="button" @click="expanded = !expanded">
      <span class="workflow-mark"><ListTodo :size="16" /></span>
      <span class="workflow-title">
        <small>SUPERVISOR WORKFLOW</small>
        <strong>{{ workflow.goal }}</strong>
      </span>
      <span class="workflow-count">{{ completed }}/{{ workflow.tasks?.length || 0 }}</span>
      <ChevronDown class="workflow-chevron" :class="{ open: expanded }" :size="16" />
    </button>
    <div v-if="expanded" class="workflow-tasks">
      <div v-for="task in workflow.tasks" :key="task.id" class="workflow-task" :class="task.status.toLowerCase()">
        <span class="task-icon">
          <component :is="iconFor(task.status)" :size="13" />
        </span>
        <span class="task-copy">
          <strong>{{ task.title }}</strong>
          <small v-if="task.message">{{ task.message }}</small>
          <small v-else>{{ task.assignedAgent }}</small>
        </span>
        <span v-if="task.status === 'RETRYING'" class="task-attempt">重试 {{ task.attempts }}/{{ task.maxAttempts }}</span>
      </div>
      <div v-if="workflow.status === 'PARTIAL' || workflow.status === 'FAILED'" class="workflow-warning">
        <TriangleAlert :size="13" /> 部分任务未达到验收条件，已停止继续重试。
      </div>
    </div>
  </section>
</template>

<style scoped>
.workflow-card{width:min(720px,100%);margin:0 0 12px;overflow:hidden;border:1px solid rgba(168,151,255,.18);border-radius:15px;background:linear-gradient(145deg,rgba(31,30,48,.92),rgba(20,22,29,.96));box-shadow:0 16px 45px rgba(0,0,0,.12)}
.workflow-card.completed{border-color:rgba(116,225,185,.17)}.workflow-card.partial,.workflow-card.failed{border-color:rgba(255,174,113,.22)}
.workflow-header{display:grid;width:100%;grid-template-columns:34px minmax(0,1fr) auto 20px;align-items:center;gap:10px;border:0;padding:13px 14px;background:transparent;color:#e6e4ef;text-align:left}
.workflow-mark{display:grid;width:32px;height:32px;place-items:center;border-radius:10px;background:rgba(154,132,255,.13);color:#b7a8ff}.workflow-title{display:grid;gap:3px}.workflow-title small{color:#777b8b;font-size:8px;font-weight:800;letter-spacing:.13em}.workflow-title strong{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:11px}.workflow-count{color:#8f93a1;font-size:9px}.workflow-chevron{color:#686d7d;transition:transform .18s}.workflow-chevron.open{transform:rotate(180deg)}
.workflow-tasks{display:grid;gap:2px;border-top:1px solid rgba(255,255,255,.055);padding:9px 13px 12px}.workflow-task{display:grid;grid-template-columns:23px minmax(0,1fr) auto;align-items:center;gap:7px;min-height:36px;border-radius:9px;padding:5px 7px;color:#8c909e}.task-icon{display:grid;width:21px;height:21px;place-items:center;border-radius:50%;background:rgba(255,255,255,.04)}.task-copy{display:grid;gap:2px}.task-copy strong{color:#b7bac5;font-size:10px;font-weight:650}.task-copy small{color:#626776;font-size:8px}.workflow-task.completed .task-icon{background:rgba(91,220,168,.1);color:#70ddb4}.workflow-task.completed .task-copy strong{color:#d1d4dc}.workflow-task.running .task-icon,.workflow-task.verifying .task-icon{color:#b7ff5d}.workflow-task.running .task-icon svg,.workflow-task.verifying .task-icon svg{animation:workflow-spin 1s linear infinite}.workflow-task.retrying .task-icon{color:#f1ba73}.workflow-task.failed .task-icon{background:rgba(255,112,112,.1);color:#f39494}.workflow-task.skipped{opacity:.58}.task-attempt{color:#cf9d65;font-size:8px}.workflow-warning{display:flex;align-items:center;gap:6px;margin-top:5px;border-radius:8px;padding:7px 9px;background:rgba(255,160,99,.07);color:#d9a170;font-size:8px}@keyframes workflow-spin{to{transform:rotate(360deg)}}
</style>
