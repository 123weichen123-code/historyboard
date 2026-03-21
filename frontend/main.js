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
  quickQueries: [],
  timeline: [],
};

async function getJson(url) {
  const res = await fetch(url);
  if (!res.ok) {
    throw new Error(`请求失败: ${res.status}`);
  }
  return await res.json();
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
      renderResultList(state.results, fallbackUsed);
      loadDetail(event.id);
    });

    resultList.appendChild(card);
  });
}

function renderDetail(detail) {
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

function renderTimeStrip(events) {
  timeStrip.innerHTML = "";
  stripMeta.textContent = `${events.length} 个中国历史节点（第一版可持续扩展）`;

  events.forEach((event) => {
    const item = document.createElement("button");
    item.type = "button";
    item.className = "strip-item";
    item.innerHTML = `
      <span class="strip-year">${event.period}</span>
      <strong>${event.title}</strong>
      <span>${event.dynasty || ""}</span>
    `;

    item.addEventListener("click", () => {
      searchInput.value = event.title;
      runSearch(event.title);
    });
    timeStrip.appendChild(item);
  });
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
    }
    if (state.results.length && !state.results.some((event) => event.id === state.selectedId)) {
      state.selectedId = state.results[0].id;
    }

    renderResultList(state.results, Boolean(payload.fallback_used));
    renderSuggestionChips(payload.suggestions || state.quickQueries);

    const selected = state.results.find((event) => event.id === state.selectedId);
    if (selected) {
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
  renderResultList(state.results, Boolean(defaults.fallback_used));

  if (state.selectedId) {
    await loadDetail(state.selectedId);
  }
}

async function loadTimeline() {
  const payload = await getJson("/api/history/timeline");
  state.timeline = payload.events || [];
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
