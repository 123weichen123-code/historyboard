const searchInput = document.getElementById("searchInput");
const searchBtn = document.getElementById("searchBtn");
const randomBtn = document.getElementById("randomBtn");
const suggestionsEl = document.getElementById("suggestions");
const resultList = document.getElementById("resultList");
const resultMeta = document.getElementById("resultMeta");
const detailCard = document.getElementById("detailCard");
const worldTimeline = document.getElementById("worldTimeline");
const timeStrip = document.getElementById("timeStrip");
const stripMeta = document.getElementById("stripMeta");

const state = {
  query: "",
  results: [],
  selectedId: "",
  selectedTitle: "",
  quickQueries: [],
  timeline: [],
  timelineDensity: 1,
};

const TIMELINE_DENSITY = {
  0.75: 210,
  1: 280,
  1.4: 360,
};

async function getJson(url) {
  const res = await fetch(url);
  if (!res.ok) {
    throw new Error(`请求失败: ${res.status}`);
  }
  return await res.json();
}

function normalizeYear(value) {
  if (typeof value === "number" && Number.isFinite(value)) {
    return value;
  }
  return null;
}

function getEventStartYear(event) {
  return normalizeYear(event.start_year ?? event.startYear ?? event.year);
}

function getEventEndYear(event) {
  return normalizeYear(event.end_year ?? event.endYear ?? event.year);
}

function getEventPeriodLabel(event) {
  return event.period || "年份待补充";
}

function getTimelineEventLabel(event) {
  return event.dynasty || event.category || "历史事件";
}

function renderSuggestionChips(items) {
  suggestionsEl.innerHTML = "";
  const chips = items.slice(0, 12);
  chips.forEach((item) => {
    const chip = document.createElement("button");
    chip.type = "button";
    chip.className = "chip";
    chip.textContent = item;
    chip.addEventListener("click", () => {
      searchInput.value = item;
      runSearch(item);
    });
    suggestionsEl.appendChild(chip);
  });
}

function renderResultList(results, fallbackUsed) {
  resultList.innerHTML = "";

  if (!results.length) {
    resultMeta.textContent = "无匹配结果";
    resultList.innerHTML = '<p class="empty-text">暂无结果，试试人物名、年份或事件别名。</p>';
    return;
  }

  resultMeta.textContent = fallbackUsed
    ? `未精确命中，已为你推荐 ${results.length} 个高相关事件`
    : `找到 ${results.length} 条相关事件`;

  results.forEach((event, index) => {
    const card = document.createElement("article");
    card.className = `result-item ${event.id === state.selectedId ? "active" : ""}`;
    card.innerHTML = `
      <div class="result-top">
        <span class="rank">#${index + 1}</span>
        <span class="dynasty">${event.dynasty || "历史事件"}</span>
      </div>
      <h3>${event.title}</h3>
      <p class="period">${event.period}</p>
      <p class="summary">${event.summary}</p>
      <div class="tag-row">${(event.tags || []).slice(0, 3).map((tag) => `<span class="tiny-tag">${tag}</span>`).join("")}</div>
    `;

    card.addEventListener("click", () => {
      state.selectedId = event.id;
      state.selectedTitle = event.title || "";
      renderResultList(state.results, fallbackUsed);
      renderTimeStrip(state.timeline);
      loadDetail(event.id);
    });

    resultList.appendChild(card);
  });
}

function renderDetail(detail) {
  state.selectedTitle = detail.title || state.selectedTitle;
  renderTimeStrip(state.timeline);

  const sourceList = (detail.sources || [])
    .slice(0, 5)
    .map((source) => {
      const name = source.source_name || source.sourceName || "来源";
      const title = source.title || "";
      const url = source.url || "";
      if (url) {
        return `<li><a href="${url}" target="_blank" rel="noopener">${name}: ${title || url}</a></li>`;
      }
      return `<li>${name}: ${title || "待补充"}</li>`;
    })
    .join("");

  detailCard.classList.remove("empty");
  detailCard.innerHTML = `
    <header>
      <p class="label">${detail.dynasty || "历史事件"} · ${detail.category || "综合"}</p>
      <h3>${detail.title}</h3>
      <p class="period">${detail.period} · ${detail.location || "地点待补充"}</p>
    </header>
    <p>${detail.summary}</p>
    <p><strong>历史影响：</strong>${detail.impact || "待补充"}</p>
    <p><strong>关键人物：</strong>${(detail.figures || []).join(" / ") || "待补充"}</p>
    <p><strong>关联标签：</strong>${(detail.tags || []).join(" / ") || "待补充"}</p>
    <p><strong>审校状态：</strong>${detail.audit_status || detail.auditStatus || "UNVERIFIED"}${detail.last_verified_at || detail.lastVerifiedAt ? ` · ${detail.last_verified_at || detail.lastVerifiedAt}` : ""}</p>
    ${sourceList ? `<p><strong>引用来源：</strong></p><ul>${sourceList}</ul>` : ""}
  `;
}

function renderWorldTimeline(items) {
  worldTimeline.innerHTML = "";
  if (!items || !items.length) {
    worldTimeline.innerHTML = '<p class="empty-text">暂无可比对的世界事件。</p>';
    return;
  }

  items.forEach((node) => {
    const card = document.createElement("article");
    card.className = "world-node";
    card.innerHTML = `
      <p class="label">${node.region}</p>
      <h4>${node.title}</h4>
      <p class="period">${node.period}</p>
      <p>${node.summary}</p>
    `;
    worldTimeline.appendChild(card);
  });
}

function buildTimelineScale(events) {
  const years = events
    .flatMap((event) => [getEventStartYear(event), getEventEndYear(event)])
    .filter((value) => typeof value === "number");

  if (!years.length) {
    return null;
  }

  const min = Math.min(...years);
  const max = Math.max(...years);
  const span = Math.max(max - min, 1);

  return { min, max, span };
}

function getYearTickStep(span) {
  if (span > 3000) return 500;
  if (span > 1500) return 200;
  if (span > 800) return 100;
  if (span > 300) return 50;
  if (span > 120) return 20;
  return 10;
}

function createTimelineHeader(events) {
  const header = document.createElement("div");
  header.className = "timeline-toolbar";
  header.innerHTML = `
    <div>
      <p class="timeline-kicker">可视化时间轴</p>
      <p class="timeline-helper">沿一条连续时间轴查看关键节点；点击节点即可跳转对应事件。</p>
    </div>
    <div class="timeline-controls" role="group" aria-label="时间轴密度切换">
      <button type="button" class="ghost timeline-density-btn" data-density="0.75">紧凑</button>
      <button type="button" class="ghost timeline-density-btn active" data-density="1">标准</button>
      <button type="button" class="ghost timeline-density-btn" data-density="1.4">展开</button>
    </div>
  `;

  header.querySelectorAll(".timeline-density-btn").forEach((button) => {
    const density = Number(button.dataset.density || 1);
    if (density === state.timelineDensity) {
      button.classList.add("active");
    }
    button.addEventListener("click", () => {
      state.timelineDensity = density;
      renderTimeStrip(events);
    });
  });

  return header;
}

function renderTimelineFallback(events) {
  const grid = document.createElement("div");
  grid.className = "time-strip-grid";

  events.forEach((event) => {
    const item = document.createElement("button");
    item.type = "button";
    item.className = "strip-item";
    item.innerHTML = `
      <span class="strip-year">${getEventPeriodLabel(event)}</span>
      <strong>${event.title}</strong>
      <span>${event.dynasty || ""}</span>
    `;
    item.addEventListener("click", () => {
      searchInput.value = event.title;
      runSearch(event.title);
    });
    grid.appendChild(item);
  });

  timeStrip.appendChild(grid);
}

function renderTimelineAxis(events, scale) {
  const lane = document.createElement("div");
  lane.className = "timeline-axis-scroll";

  const rail = document.createElement("div");
  rail.className = "timeline-axis";
  rail.style.width = `${Math.max(events.length * TIMELINE_DENSITY[state.timelineDensity], 860)}px`;

  const ticks = document.createElement("div");
  ticks.className = "timeline-ticks";
  const tickStep = getYearTickStep(scale.span);
  const firstTick = Math.ceil(scale.min / tickStep) * tickStep;
  for (let year = firstTick; year <= scale.max; year += tickStep) {
    const tick = document.createElement("div");
    tick.className = "timeline-tick";
    tick.style.left = `${((year - scale.min) / scale.span) * 100}%`;
    tick.innerHTML = `<span>${year < 0 ? `${Math.abs(year)} BCE` : `${year}`}</span>`;
    ticks.appendChild(tick);
  }
  rail.appendChild(ticks);

  events.forEach((event, index) => {
    const startYear = getEventStartYear(event);
    const fallbackPosition = events.length === 1 ? 50 : (index / (events.length - 1)) * 100;
    const position = typeof startYear === "number" ? ((startYear - scale.min) / scale.span) * 100 : fallbackPosition;
    const node = document.createElement("button");
    const isActive = state.selectedTitle && state.selectedTitle === event.title;
    node.type = "button";
    node.className = `timeline-node ${index % 2 === 0 ? "upper" : "lower"} ${isActive ? "active" : ""}`;
    node.style.left = `${Math.min(Math.max(position, 0), 100)}%`;
    node.innerHTML = `
      <span class="timeline-node-dot"></span>
      <span class="timeline-node-card">
        <span class="timeline-node-era">${getTimelineEventLabel(event)}</span>
        <strong>${event.title}</strong>
        <span class="timeline-node-period">${getEventPeriodLabel(event)}</span>
      </span>
    `;
    node.addEventListener("click", () => {
      searchInput.value = event.title;
      runSearch(event.title);
    });
    rail.appendChild(node);
  });

  lane.appendChild(rail);
  timeStrip.appendChild(lane);

  const activeNode = rail.querySelector(".timeline-node.active");
  if (activeNode) {
    requestAnimationFrame(() => {
      activeNode.scrollIntoView({ behavior: "smooth", block: "nearest", inline: "center" });
    });
  }
}

function renderTimeStrip(events) {
  timeStrip.innerHTML = "";

  if (!events.length) {
    stripMeta.textContent = "暂无时间轴数据";
    timeStrip.innerHTML = '<p class="empty-text">暂无可展示的历史节点。</p>';
    return;
  }

  stripMeta.textContent = `${events.length} 个中国历史节点，可按时间连续浏览`;
  timeStrip.appendChild(createTimelineHeader(events));

  const scale = buildTimelineScale(events);
  if (!scale) {
    renderTimelineFallback(events);
    return;
  }

  renderTimelineAxis(events, scale);
}

async function loadDetail(eventId) {
  try {
    const detail = await getJson(`/api/history/events/${eventId}`);
    renderDetail(detail);
    renderWorldTimeline(detail.world_context || []);
  } catch (error) {
    detailCard.classList.remove("empty");
    detailCard.innerHTML = `<p class="empty-text">详情加载失败：${error.message}</p>`;
    renderWorldTimeline([]);
  }
}

async function runSearch(rawQuery) {
  const query = (rawQuery ?? searchInput.value).trim();
  state.query = query;

  try {
    const payload = await getJson(`/api/history/search?q=${encodeURIComponent(query)}&limit=12`);
    state.results = payload.results || [];
    if (!state.selectedId && state.results.length) {
      state.selectedId = state.results[0].id;
      state.selectedTitle = state.results[0].title || "";
    }
    if (state.results.length && !state.results.some((event) => event.id === state.selectedId)) {
      state.selectedId = state.results[0].id;
      state.selectedTitle = state.results[0].title || "";
    }

    renderResultList(state.results, Boolean(payload.fallback_used));
    renderSuggestionChips(payload.suggestions || state.quickQueries);

    const selected = state.results.find((event) => event.id === state.selectedId);
    if (selected) {
      state.selectedTitle = selected.title || "";
      renderTimeStrip(state.timeline);
      await loadDetail(selected.id);
    } else {
      detailCard.classList.add("empty");
      detailCard.innerHTML = "请先在左侧选择一个历史事件。";
      renderWorldTimeline([]);
    }
  } catch (error) {
    resultMeta.textContent = "加载失败";
    resultList.innerHTML = `<p class="empty-text">${error.message}</p>`;
  }
}

async function loadDiscover() {
  const discover = await getJson("/api/history/discover");
  state.quickQueries = discover.quick_queries || [];
  renderSuggestionChips(state.quickQueries);

  const defaults = discover.default_results || {};
  state.results = defaults.results || [];
  state.selectedId = state.results[0]?.id || "";
  state.selectedTitle = state.results[0]?.title || "";
  renderResultList(state.results, Boolean(defaults.fallback_used));

  if (state.selectedId) {
    await loadDetail(state.selectedId);
  }
}

async function loadTimeline() {
  const payload = await getJson("/api/history/timeline");
  state.timeline = (payload.events || []).slice().sort((a, b) => {
    const yearA = getEventStartYear(a);
    const yearB = getEventStartYear(b);
    if (typeof yearA === "number" && typeof yearB === "number") {
      return yearA - yearB;
    }
    if (typeof yearA === "number") {
      return -1;
    }
    if (typeof yearB === "number") {
      return 1;
    }
    return String(a.period || a.title).localeCompare(String(b.period || b.title), "zh-CN");
  });
  renderTimeStrip(state.timeline);
}

function pickRandomQuery() {
  const pool = state.quickQueries.length ? state.quickQueries : ["秦始皇", "鸦片战争", "辛亥革命", "改革开放"];
  const index = Math.floor(Math.random() * pool.length);
  const query = pool[index];
  searchInput.value = query;
  runSearch(query);
}

searchBtn.addEventListener("click", () => runSearch());
randomBtn.addEventListener("click", pickRandomQuery);
searchInput.addEventListener("keydown", (event) => {
  if (event.key === "Enter") {
    runSearch();
  }
});

(async function boot() {
  try {
    await Promise.all([loadDiscover(), loadTimeline()]);
  } catch (error) {
    resultMeta.textContent = "初始化失败";
    resultList.innerHTML = `<p class="empty-text">${error.message}</p>`;
  }
})();
