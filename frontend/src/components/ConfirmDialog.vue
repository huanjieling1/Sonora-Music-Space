<script setup>
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { AlertTriangle, Trash2, X } from 'lucide-vue-next'
import { useConfirmation } from '../services/confirm'

const { confirmation, confirm, cancel } = useConfirmation()
const cancelButton = ref(null)

watch(confirmation, async (value) => {
  if (!value) return
  await nextTick()
  cancelButton.value?.focus()
})

function handleKeydown(event) {
  if (event.key === 'Escape' && confirmation.value) cancel()
}

window.addEventListener('keydown', handleKeydown)
onBeforeUnmount(() => window.removeEventListener('keydown', handleKeydown))
</script>

<template>
  <Teleport to="body">
    <Transition name="confirm-fade">
      <div v-if="confirmation" class="confirm-backdrop" role="presentation" @mousedown.self="cancel">
        <section
          class="confirm-dialog"
          :class="`tone-${confirmation.tone}`"
          role="alertdialog"
          aria-modal="true"
          aria-labelledby="confirm-dialog-title"
          aria-describedby="confirm-dialog-description"
        >
          <button class="confirm-close" type="button" aria-label="关闭确认弹窗" @click="cancel"><X :size="17" /></button>

          <div class="confirm-icon" aria-hidden="true"><Trash2 :size="22" /></div>
          <p class="confirm-eyebrow">{{ confirmation.eyebrow }}</p>
          <h2 id="confirm-dialog-title">{{ confirmation.title }}</h2>
          <p id="confirm-dialog-description" class="confirm-message">{{ confirmation.message }}</p>

          <div v-if="confirmation.subject" class="confirm-subject">
            <span>{{ confirmation.subject.slice(0, 1) }}</span>
            <strong>{{ confirmation.subject }}</strong>
          </div>

          <div class="confirm-hint"><AlertTriangle :size="14" /><span>{{ confirmation.hint }}</span></div>

          <footer>
            <button ref="cancelButton" class="confirm-cancel" type="button" @click="cancel">{{ confirmation.cancelText }}</button>
            <button class="confirm-submit" type="button" @click="confirm"><Trash2 :size="15" />{{ confirmation.confirmText }}</button>
          </footer>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.confirm-backdrop {
  position: fixed;
  z-index: 1000;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(5, 6, 10, 0.72);
  backdrop-filter: blur(10px) saturate(80%);
}

.confirm-dialog {
  position: relative;
  width: min(420px, calc(100vw - 32px));
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.11);
  border-radius: 24px;
  padding: 30px;
  background:
    radial-gradient(circle at 15% 0%, rgba(255, 111, 103, 0.12), transparent 36%),
    radial-gradient(circle at 100% 100%, rgba(184, 255, 84, 0.055), transparent 35%),
    #181820;
  color: #f4f3f7;
  box-shadow: 0 32px 90px rgba(0, 0, 0, 0.58), 0 0 0 1px rgba(0, 0, 0, 0.22);
}

.confirm-dialog::before {
  position: absolute;
  top: 0;
  left: 30px;
  width: 52px;
  height: 2px;
  border-radius: 999px;
  background: linear-gradient(90deg, #ff756e, #b8ff54);
  content: '';
}

.confirm-close {
  position: absolute;
  top: 17px;
  right: 17px;
  display: grid;
  width: 32px;
  height: 32px;
  place-items: center;
  border: 0;
  border-radius: 9px;
  background: transparent;
  color: #797d89;
}

.confirm-close:hover { background: rgba(255, 255, 255, 0.06); color: white; }
.confirm-icon { display: grid; width: 48px; height: 48px; place-items: center; border: 1px solid rgba(255, 117, 110, 0.22); border-radius: 15px; background: rgba(255, 117, 110, 0.1); color: #ff8c85; }
.confirm-eyebrow { margin: 20px 0 8px; color: #ff8c85; font-size: 10px; font-weight: 800; letter-spacing: 0.16em; text-transform: uppercase; }
.confirm-dialog h2 { margin: 0; font-size: 23px; letter-spacing: -0.035em; }
.confirm-message { margin: 11px 0 0; color: #9295a1; font-size: 13px; line-height: 1.7; }

.confirm-subject {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 11px;
  margin-top: 19px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 13px;
  padding: 10px 12px;
  background: rgba(255, 255, 255, 0.035);
}

.confirm-subject span { display: grid; width: 31px; height: 31px; flex: 0 0 31px; place-items: center; border-radius: 9px; background: linear-gradient(145deg, #343143, #242633); color: #b8ff54; font-size: 12px; font-weight: 800; }
.confirm-subject strong { overflow: hidden; color: #dfe0e6; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.confirm-hint { display: flex; align-items: center; gap: 7px; margin-top: 15px; color: #737783; font-size: 10px; }

.confirm-dialog footer { display: flex; justify-content: flex-end; gap: 9px; margin-top: 26px; }
.confirm-dialog footer button { display: flex; min-width: 104px; height: 42px; align-items: center; justify-content: center; gap: 7px; border-radius: 12px; padding: 0 17px; font-size: 12px; font-weight: 750; transition: transform 0.16s ease, border-color 0.16s ease, background 0.16s ease; }
.confirm-dialog footer button:hover { transform: translateY(-1px); }
.confirm-dialog footer button:focus-visible { outline: 2px solid rgba(184, 255, 84, 0.7); outline-offset: 2px; }
.confirm-cancel { border: 1px solid rgba(255, 255, 255, 0.1); background: rgba(255, 255, 255, 0.045); color: #c5c7cf; }
.confirm-cancel:hover { border-color: rgba(255, 255, 255, 0.18); background: rgba(255, 255, 255, 0.075); }
.confirm-submit { border: 1px solid rgba(255, 117, 110, 0.15); background: #ff756e; color: #1a0d0d; box-shadow: 0 8px 24px rgba(255, 98, 91, 0.18); }
.confirm-submit:hover { background: #ff8982; }

.confirm-fade-enter-active,
.confirm-fade-leave-active { transition: opacity 0.2s ease; }
.confirm-fade-enter-active .confirm-dialog,
.confirm-fade-leave-active .confirm-dialog { transition: transform 0.28s cubic-bezier(0.22, 1, 0.36, 1), opacity 0.2s ease; }
.confirm-fade-enter-from,
.confirm-fade-leave-to { opacity: 0; }
.confirm-fade-enter-from .confirm-dialog { opacity: 0; transform: translateY(14px) scale(0.97); }
.confirm-fade-leave-to .confirm-dialog { opacity: 0; transform: translateY(7px) scale(0.985); }

@media (max-width: 520px) {
  .confirm-dialog { border-radius: 20px; padding: 24px; }
  .confirm-dialog footer { display: grid; grid-template-columns: 1fr 1fr; }
  .confirm-dialog footer button { min-width: 0; }
}

@media (prefers-reduced-motion: reduce) {
  .confirm-fade-enter-active,
  .confirm-fade-leave-active,
  .confirm-fade-enter-active .confirm-dialog,
  .confirm-fade-leave-active .confirm-dialog { transition: none; }
}
</style>

