<script setup lang="ts">
import UiDialog from './UiDialog.vue';

defineProps<{ open: boolean }>();
defineEmits<{ close: [] }>();

const terms = [
  {
    name: '总管理员',
    category: '后台身份',
    text: '维护平台目录、后台账号和管理员范围，可以配置公司级共享角色及部门角色。角色权限配置和一般授权变更仍需经过 OA 批准并执行。',
  },
  {
    name: '权限管理员',
    category: '也称部门管理员',
    text: '按总管理员指定的公司、指定部门和系统办理业务。可以维护范围内部门角色，为合格员工申请分配角色，以及关闭原部门权限。',
  },
  {
    name: '公司级共享角色',
    category: '业务角色',
    text: '同一公司、同一业务系统内，多个部门可重复使用的功能权限组合。仅总管理员配置，各部门管理员可申请分配；员工不会自动获得。',
  },
  {
    name: '部门角色',
    category: '业务角色',
    text: '归属一个明确部门和业务系统。总管理员或获准范围内的权限管理员可配置；员工须在角色所属部门有效任职，才能申请分配。',
  },
  {
    name: '部门有效任职',
    category: '分配资格',
    text: '员工当前实际在指定部门任职，且该任职未结束。例如财务部角色要求在财务部有效任职，其他部门的任职不满足条件。',
  },
  {
    name: '授权来源部门',
    category: '员工授权依据',
    text: '员工某次获得角色时记录的实际任职部门。公司级共享角色同样记录来源；关闭 A 部门来源后，B 部门的独立有效来源继续保留。',
  },
];
</script>

<template>
  <UiDialog
    :open="open"
    title="术语说明"
    subtitle="理解后台身份、业务角色与授权范围"
    drawer
    @close="$emit('close')"
  >
    <p class="terminology-intro">
      员工通过业务角色获得功能权限；管理员通过后台账号及任命管理这些授权。普通员工不登录权限中心。
    </p>
    <dl class="terminology-list">
      <div v-for="term in terms" :key="term.name">
        <dt>
          {{ term.name }}<small>{{ term.category }}</small>
        </dt>
        <dd>{{ term.text }}</dd>
      </div>
    </dl>
    <h3 class="section-title mt-7">三类停用与关权操作</h3>
    <div class="terminology-actions">
      <article>
        <strong>关闭原部门权限</strong>
        <p>立即关闭获准部门、系统内的授权来源，保留其他独立来源，无需等待 OA。</p>
      </article>
      <article>
        <strong>全局停用员工</strong>
        <p>仅总管理员办理。全部业务授权和管理员任命失效；恢复员工身份后需重新授权和任命。</p>
      </article>
      <article>
        <strong>停用后台账号</strong>
        <p>仅总管理员办理。禁止该账号登录权限中心并使旧会话失效，员工业务授权保留。</p>
      </article>
    </div>
    <p class="terminology-footnote">
      OA 批准后仍需执行成功才改变权限。公司与部门用于管理边界和授权来源，本平台仅管理功能权限。
    </p>
    <template #footer>
      <button class="button primary" @click="$emit('close')">了解了</button>
    </template>
  </UiDialog>
</template>
