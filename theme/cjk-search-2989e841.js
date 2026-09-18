/*
 * 中文搜尋補丁 —— mdBook 內建搜尋對中文完全無效的解法。
 *
 * 【問題】mdBook 用 elasticlunr 建索引，而它的分詞器是「以空白切詞」。
 *         中文句子沒有空白，整段會變成一個超長的詞，於是搜「進階」找不到
 *         「所有科技機器都必須在進階工作台上製作」——
 *         實測索引裡第一層中文節點是 0 個，也就是中文從頭到尾沒被建進索引。
 *
 * 【解法】不動索引。mdBook 已經把每一頁的原文存在 documentStore 裡送到瀏覽器，
 *         所以中文查詢時改用最直接的子字串比對，跨全書所有段落找。
 *         非中文查詢一律讓 mdBook 自己處理，行為不變。
 */
(function () {
  'use strict';
  var TAG = '[cjk-search]';
  console.log(TAG, '已載入 v3');
  var CJK = /[㐀-鿿豈-﫿぀-ヿ가-힯]/;
  var MAX = 30;              // 跟 mdBook 的 limit_results 一致
  var TEASER = 60;           // 摘要取幾個字（中文一個字就是一個字，不用像英文算詞）

  function esc(s) {
    return String(s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }

  /** 在原文裡切出命中處前後的一段，並把關鍵字標起來。 */
  function teaser(body, q) {
    var i = body.indexOf(q);
    if (i < 0) return esc(body.slice(0, TEASER));
    var from = Math.max(0, i - Math.floor(TEASER / 3));
    var seg = body.slice(from, from + TEASER);
    var out = esc(seg).split(esc(q)).join('<em>' + esc(q) + '</em>');
    return (from > 0 ? '…' : '') + out + '…';
  }

  function run(query) {
    var s = window.search;
    if (!s || !s.index || !s.index.documentStore || !s.doc_urls) {
      console.warn(TAG, '索引還沒載入 —— window.search =', s && Object.keys(s));
      return false;
    }
    console.log(TAG, '查詢', JSON.stringify(query));
    var docs = s.index.documentStore.docs;
    var urls = s.doc_urls;
    var results = document.getElementById('mdbook-searchresults');
    var header = document.getElementById('mdbook-searchresults-header');
    var outer = document.getElementById('mdbook-searchresults-outer');
    if (!results || !header || !outer) {
      console.warn(TAG, '找不到結果區 DOM', !!results, !!header, !!outer);
      return false;
    }

    var q = query.trim();
    var hits = [];
    for (var k in docs) {
      if (!Object.prototype.hasOwnProperty.call(docs, k)) continue;
      var d = docs[k];
      var title = d.title || '';
      var body = d.body || '';
      var crumbs = d.breadcrumbs || '';
      // 標題命中排前面 —— 找「進階」時，標題就叫「進階工作台」的那頁最該在最上面
      var score = title.indexOf(q) >= 0 ? 0 : (crumbs.indexOf(q) >= 0 ? 1 : (body.indexOf(q) >= 0 ? 2 : -1));
      if (score < 0) continue;
      hits.push({ score: score, url: urls[k] || '', title: title, body: body, crumbs: crumbs });
      }
    hits.sort(function (a, b) { return a.score - b.score; });
    console.log(TAG, '命中', hits.length, '筆');

    results.innerHTML = '';
    if (!hits.length) {
      header.textContent = '找不到「' + q + '」';
      outer.classList.remove('hidden');  // ★ mdBook 用 hidden 控制顯隱，不是 visible
      return true;
    }
    var shown = hits.slice(0, MAX);
    header.textContent = '「' + q + '」找到 ' + hits.length + ' 筆'
      + (hits.length > MAX ? '（顯示前 ' + MAX + ' 筆）' : '');
    // mdBook 的搜尋結果連結要帶 ?search= 參數，點進去該頁才會把關鍵字標記出來
    var suffix = '?' + 'search' + '=' + encodeURIComponent(q);
    // ★ 索引裡存的 url 是「相對於站台根目錄」的（例如 getting-started/first-steps.html），
    //   所以一定要接 path_to_root，否則在子目錄的頁面上搜尋時，瀏覽器會把它接在
    //   當前目錄後面 → getting-started/getting-started/... → 404 一下再彈回來。
    //   原版 searcher.js 寫的就是 path_to_root + url[0]，這支補丁當初漏了這一段。
    var root = (typeof path_to_root === 'string') ? path_to_root : '';
    shown.forEach(function (h) {
      var li = document.createElement('li');
      var hash = h.url.indexOf('#');
      var base = root + h.url;
      var href = hash >= 0 ? base.slice(0, base.indexOf('#')) + suffix + base.slice(base.indexOf('#')) : base + suffix;
      li.innerHTML = '<a href="' + esc(href) + '">' + esc(h.crumbs || h.title) + '</a>'
        + '<span class="teaser">' + teaser(h.body || h.title, q) + '</span>';
      results.appendChild(li);
    });
    outer.classList.remove('hidden');  // ★ mdBook 用 hidden 控制顯隱，不是 visible
    return true;
  }

  function hook() {
    var bar = document.getElementById('mdbook-searchbar');
    if (!bar) { console.warn(TAG, '找不到 #mdbook-searchbar —— 沒掛上監聽'); return; }
    console.log(TAG, '監聽已掛上');
    var timer = null;
    var handler = function () {
      var q = bar.value || '';
      if (!CJK.test(q)) return;      // 英數字交還給 mdBook，行為完全不變
      console.log(TAG, '偵測到中文查詢', JSON.stringify(q));
      clearTimeout(timer);
      // 延一下再跑：mdBook 自己的處理器也綁在 input 上，我們要蓋在它後面
      timer = setTimeout(function () { run(q); }, 60);
    };
    bar.addEventListener('input', handler);
    bar.addEventListener('keyup', handler);
    // 從網址帶 ?search=中文 進來時也要生效
    if (CJK.test(bar.value || '')) setTimeout(function () { run(bar.value); }, 200);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', hook);
  } else {
    hook();
  }
})();
