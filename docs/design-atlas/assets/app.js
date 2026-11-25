(() => {
  'use strict';
  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => [...r.querySelectorAll(s)];

  /* 1. 章节面板秒切（彻底避免长滚动跳偏） */
  const panels = $$('main > section.panel');
  const tocLinks = $$('.toc a');
  function navigate() {
    const raw = location.hash.slice(1) || 'overview';
    const targetId = raw.startsWith('sc-') ? 'cases' : (panels.some(p => p.id === raw) ? raw : 'overview');
    panels.forEach(p => { p.hidden = p.id !== targetId; });
    tocLinks.forEach(a => a.classList.toggle('active', a.hash === '#' + raw || (!raw.startsWith('sc-') && a.hash === '#' + targetId)));
    if (raw.startsWith('sc-')) {
      const el = document.getElementById(raw);
      if (el) el.scrollIntoView({ behavior: 'auto', block: 'start' });
    } else {
      window.scrollTo(0, 0);
    }
    requestAnimationFrame(() => viewers.forEach(v => { if (!v.fig.closest('[hidden]') && v.fitted) v.fit(); }));
  }
  window.addEventListener('hashchange', navigate);

  /* 2. 图形查看器：以“适应宽度 = 100%”为直观基准，支持指针缩放、拖拽、双击与全屏 */
  const viewers = new Map();
  class Viewer {
    constructor(fig) {
      this.fig = fig;
      this.stage = $('.viewer-stage', fig);
      this.canvas = $('.viewer-canvas', fig);
      this.img = $('img', fig);
      this.out = $('.zoom', fig);
      this.zoom = 1;
      this.fitted = true;
      $$('[data-act]', fig).forEach(b => b.addEventListener('click', () => this.act(b.dataset.act)));
      this.bindPointer();
      this.stage.addEventListener('wheel', e => {
        if (!(e.ctrlKey || e.metaKey)) return;
        e.preventDefault();
        const r = this.stage.getBoundingClientRect();
        this.zoomBy(e.deltaY < 0 ? 1.18 : 0.85, e.clientX - r.left, e.clientY - r.top);
      }, { passive: false });
      this.stage.addEventListener('keydown', e => {
        if (e.key === '+' || e.key === '=') { this.zoomBy(1.25); e.preventDefault(); }
        else if (e.key === '-') { this.zoomBy(0.8); e.preventDefault(); }
        else if (e.key === '0') { this.fit(); e.preventDefault(); }
        else if (e.key.toLowerCase() === 'f') { this.toggleFull(); e.preventDefault(); }
      });
    }
    setSrc(src) {
      if (this.img.getAttribute('src') === src) return;
      this.img.src = src;
      this.fit();
    }
    apply(nextZoom, ax, ay) {
      const old = this.zoom;
      nextZoom = Math.max(0.5, Math.min(4.0, Math.round(nextZoom * 100) / 100));
      const sw = this.stage.clientWidth || 1;
      const oldW = this.canvas.offsetWidth || sw * old;
      const newW = sw * nextZoom;
      const rx = ax !== undefined ? (this.stage.scrollLeft + ax) / oldW : null;
      const ry = ay !== undefined ? (this.stage.scrollTop + ay) / (this.canvas.offsetHeight || 1) : null;
      this.zoom = nextZoom;
      this.canvas.style.width = (nextZoom * 100) + '%';
      if (rx !== null) {
        this.stage.scrollLeft = rx * newW - ax;
        this.stage.scrollTop = ry * (this.canvas.offsetHeight || 1) - ay;
      }
      if (this.out) this.out.textContent = Math.round(nextZoom * 100) + '%';
    }
    fit() {
      this.fitted = true;
      this.apply(1);
      this.stage.scrollLeft = 0;
      this.stage.scrollTop = 0;
    }
    zoomBy(f, ax, ay) {
      this.fitted = false;
      if (ax === undefined) { ax = this.stage.clientWidth / 2; ay = this.stage.clientHeight / 2; }
      this.apply(this.zoom * f, ax, ay);
    }
    toggleFull(force) {
      const on = force ?? !this.fig.classList.contains('is-full');
      this.fig.classList.toggle('is-full', on);
      document.body.classList.toggle('has-full', on || !!$('.compare.is-full'));
      const lbl = $('[data-act="full"] .full-label', this.fig);
      if (lbl) lbl.textContent = on ? '退出' : '全屏';
      if (on) this.stage.focus({ preventScroll: true });
      requestAnimationFrame(() => this.fit());
    }
    act(a) {
      if (a === 'in') this.zoomBy(1.25);
      else if (a === 'out') this.zoomBy(0.8);
      else if (a === 'fit') this.fit();
      else if (a === 'full') this.toggleFull();
    }
    bindPointer() {
      let st = null;
      this.stage.addEventListener('pointerdown', e => {
        if (e.button !== 0) return;
        st = { x: e.clientX, y: e.clientY, l: this.stage.scrollLeft, t: this.stage.scrollTop, id: e.pointerId, moved: false };
      });
      this.stage.addEventListener('pointermove', e => {
        if (!st || e.pointerId !== st.id) return;
        const dx = e.clientX - st.x, dy = e.clientY - st.y;
        if (!st.moved && Math.hypot(dx, dy) < 4) return;
        if (!st.moved) { st.moved = true; this.stage.classList.add('dragging'); this.stage.setPointerCapture(e.pointerId); }
        this.stage.scrollLeft = st.l - dx;
        this.stage.scrollTop = st.t - dy;
      });
      const end = () => { st = null; this.stage.classList.remove('dragging'); };
      this.stage.addEventListener('pointerup', end);
      this.stage.addEventListener('pointercancel', end);
      this.stage.addEventListener('dblclick', e => {
        const r = this.stage.getBoundingClientRect();
        this.zoomBy(1.5, e.clientX - r.left, e.clientY - r.top);
      });
      this.stage.addEventListener('dragstart', e => e.preventDefault());
    }
  }
  $$('.viewer').forEach(f => viewers.set(f.id, new Viewer(f)));

  /* 3. 对照组件：图类型、布局与全屏对照 */
  for (const cmp of $$('[data-compare]')) {
    const grid = $('.compare-grid', cmp), figs = $$('.viewer', cmp);
    const kindBtns = $$('[data-kind]', cmp);
    const layoutBtns = $$('.compare-bar button[data-layout]', cmp);
    const fullBtn = $('[data-compare-full]', cmp);

    const setKind = kind => {
      kindBtns.forEach(b => b.setAttribute('aria-checked', String(b.dataset.kind === kind)));
      for (const f of figs) {
        const src = f.dataset[kind + 'Src'];
        if (!src) continue;
        $('.viewer-title h3', f).textContent = f.dataset[kind + 'Title'];
        $('.viewer-title p', f).textContent = f.dataset[kind + 'Note'];
        $('[data-role="raw"]', f).href = src;
        $('img', f).alt = f.dataset[kind + 'Title'];
        viewers.get(f.id).setSrc(src);
      }
    };
    const setLayout = layout => {
      grid.dataset.layout = layout;
      layoutBtns.forEach(b => b.setAttribute('aria-checked', String(b.dataset.layout === layout)));
      figs.forEach(f => { f.hidden = !(layout === 'both' || layout === 'stack' || f.dataset.slot === layout); });
      requestAnimationFrame(() => figs.forEach(f => { if (!f.hidden) viewers.get(f.id).fit(); }));
    };
    const setFull = on => {
      cmp.classList.toggle('is-full', on);
      document.body.classList.toggle('has-full', on);
      $('.full-label', fullBtn).textContent = on ? '退出对照（Esc）' : '全屏对照';
      if (on && grid.dataset.layout === 'stack') setLayout('both');
      requestAnimationFrame(() => figs.forEach(f => { if (!f.hidden) viewers.get(f.id).fit(); }));
    };
    kindBtns.forEach(b => b.addEventListener('click', () => setKind(b.dataset.kind)));
    layoutBtns.forEach(b => b.addEventListener('click', () => setLayout(b.dataset.layout)));
    fullBtn?.addEventListener('click', () => setFull(!cmp.classList.contains('is-full')));
    cmp.exitFull = () => setFull(false);
    setLayout(grid.dataset.layout);
  }
  addEventListener('keydown', e => {
    if (e.key !== 'Escape') return;
    viewers.forEach(v => { if (v.fig.classList.contains('is-full')) v.toggleFull(false); });
    $$('.compare.is-full').forEach(c => c.exitFull());
  });

  /* 4. 点击复制源码路径 */
  const toast = $('#toast');
  let toastTimer;
  function showToast(msg) {
    if (!toast) return;
    toast.textContent = msg;
    toast.classList.add('show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => toast.classList.remove('show'), 1400);
  }
  $$('.copyable').forEach(el => {
    el.addEventListener('click', async () => {
      const txt = el.dataset.copy || el.textContent;
      try { await navigator.clipboard.writeText(txt); } catch (e) {}
      el.classList.add('copied');
      setTimeout(() => el.classList.remove('copied'), 900);
      showToast('已复制路径：' + txt);
    });
  });

  /* 5. 数据字典检索 */
  const dicts = $$('.dictionary'), dictSearch = $('#dict-search'), dictCount = $('#dict-count');
  const filterDict = () => {
    const q = (dictSearch?.value || '').trim().toLowerCase();
    let n = 0;
    dicts.forEach(d => {
      const show = !q || d.textContent.toLowerCase().includes(q);
      d.hidden = !show;
      if (show) n++;
      if (q && show) d.open = true;
    });
    if (dictCount) dictCount.textContent = `${n} / ${dicts.length} 张表`;
  };
  dictSearch?.addEventListener('input', filterDict);
  $$('[data-dict]').forEach(b => b.addEventListener('click', () => dicts.forEach(d => { if (!d.hidden) d.open = b.dataset.dict === 'open'; })));
  filterDict();

  /* 6. OpenAPI 检索与高亮 */
  const endpoints = $$('.endpoint');
  if (endpoints.length) {
    const search = $('#api-search'), tagSel = $('#api-tag'), count = $('#api-count'), empty = $('#api-empty');
    const methodBtns = $$('.method-chips [data-method]'), expandBtn = $('#api-expand');
    const state = { methods: new Set(), tag: 'all', q: '' };
    for (const e of endpoints) for (const el of $$('summary > code, .op-summary', e)) el.dataset.raw = el.textContent;
    const escHtml = t => t.replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
    const mark = (el, q) => {
      const raw = el.dataset.raw, i = q ? raw.toLowerCase().indexOf(q) : -1;
      if (i < 0) { el.textContent = raw; return; }
      el.innerHTML = escHtml(raw.slice(0, i)) + '<mark>' + escHtml(raw.slice(i, i + q.length)) + '</mark>' + escHtml(raw.slice(i + q.length));
    };
    const visible = () => endpoints.filter(e => !e.hidden);
    const syncExpand = () => { expandBtn.textContent = visible().some(e => !e.open) ? '展开结果' : '收起结果'; };
    const apply = () => {
      let n = 0;
      for (const e of endpoints) {
        const show = (!state.methods.size || state.methods.has(e.dataset.method))
          && (state.tag === 'all' || e.dataset.tag === state.tag)
          && (!state.q || e.dataset.search.includes(state.q));
        e.hidden = !show;
        if (show) { n++; $$('summary > code, .op-summary', e).forEach(el => mark(el, state.q)); }
      }
      count.textContent = `${n} / ${endpoints.length}`;
      empty.hidden = n > 0;
      tagSel.value = state.tag;
      methodBtns.forEach(b => b.setAttribute('aria-pressed', String(state.methods.has(b.dataset.method))));
      syncExpand();
    };
    methodBtns.forEach(b => b.addEventListener('click', () => {
      const m = b.dataset.method;
      state.methods.has(m) ? state.methods.delete(m) : state.methods.add(m);
      apply();
    }));
    tagSel.addEventListener('change', () => { state.tag = tagSel.value; apply(); });
    search.addEventListener('input', () => { state.q = search.value.trim().toLowerCase(); apply(); });
    expandBtn.addEventListener('click', () => {
      const open = visible().some(e => !e.open);
      visible().forEach(e => { e.open = open; });
      syncExpand();
    });
    $('#api-reset').addEventListener('click', () => {
      state.methods.clear(); state.tag = 'all'; state.q = ''; search.value = ''; apply();
    });
    endpoints.forEach(e => e.addEventListener('toggle', syncExpand));
    addEventListener('keydown', e => {
      if (e.key === '/' && !/^(INPUT|TEXTAREA|SELECT)$/.test(document.activeElement?.tagName) && !$('#api').hidden) {
        e.preventDefault(); search.focus();
      }
    });
    apply();
  }

  navigate();
})();
