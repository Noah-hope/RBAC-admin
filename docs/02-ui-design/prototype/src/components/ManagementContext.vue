<script setup lang="ts">
import { computed } from 'vue';
import { companies } from '../data';
import {
  allowedSystems,
  companyName,
  currentManagement,
  data,
  deptName,
  isGlobal,
  page,
  selectedManagement,
} from '../store';
import UiIcon from './UiIcon.vue';
const platform = computed(() => ['systems', 'administrators'].includes(page.value));
const memberships = computed(() => data.management.filter((m) => m.accounts.includes('A-01')));
</script>
<template>
  <section class="management-context" aria-label="当前办理范围">
    <UiIcon name="ShieldCheck" :size="20" />
    <div>
      <small>{{ platform ? '平台级管理' : '本次办理范围' }}</small
      ><strong>{{ platform ? '全部业务系统及后台配置' : companyName }}</strong>
    </div>
    <template v-if="!platform">
      <span class="context-divider" />
      <label v-if="!isGlobal && memberships.length > 1" class="context-choice"
        >管理任命<select v-model="selectedManagement" aria-label="当前管理任命">
          <option v-for="m in memberships" :key="m.id" :value="m.id">
            {{ companies.find((c) => c.id === m.company)?.name }} / {{ deptName(m.root) }} /
            {{ m.systems.join('、') }}
          </option>
        </select></label
      >
      <span v-else>{{
        isGlobal ? '当前公司全部部门' : `${deptName(currentManagement?.root ?? '')}及下级部门`
      }}</span>
      <div class="context-systems">
        <span v-for="id in allowedSystems" :key="id" class="small-tag">{{
          data.systems.find((s) => s.id === id)?.short
        }}</span
        ><span v-if="!allowedSystems.length">无可管理系统</span>
      </div>
    </template>
  </section>
</template>
