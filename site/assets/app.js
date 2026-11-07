// EcoWi site: the live widget preview and the download button. Works without it too.
(() => {
  const lang = document.documentElement.lang || "en";

  // ---- Widget preview: the same colour bands and caption format as the app. ----
  const stage = document.querySelector(".stage");
  if (stage) {
    const slider = stage.querySelector("#soc");
    const socs = stage.querySelectorAll("[data-soc]");
    const captions = stage.querySelectorAll("[data-caption]");
    const CAPACITY_WH = 1024; // DELTA 3 Classic
    const LOAD_W = 245; // the AC load shown in the preview
    const now = new Intl.DateTimeFormat(lang, { hour: "numeric", minute: "2-digit" }).format(new Date());

    // core/LoadResolver.kt BatteryBand: <=10 critical, <=25 low, <=50 medium, else high.
    const band = (soc) => (soc <= 10 ? "critical" : soc <= 25 ? "low" : soc <= 50 ? "medium" : "high");

    const remaining = (soc) => {
      const minutes = Math.round((soc / 100) * (CAPACITY_WH / LOAD_W) * 60);
      if (minutes <= 0) return stage.dataset.empty;
      if (minutes < 60) return stage.dataset.m.replace("%m", minutes);
      return stage.dataset.hm.replace("%h", Math.floor(minutes / 60)).replace("%m", minutes % 60);
    };

    const update = () => {
      const soc = Number(slider.value);
      const left = remaining(soc);
      stage.style.setProperty("--soc", soc);
      stage.dataset.band = band(soc);
      socs.forEach((el) => { el.textContent = `${soc}%`; });
      captions.forEach((el) => { el.textContent = `${left} · ${now}`; });
      slider.setAttribute("aria-valuetext", `${soc}%, ${left}`);
    };

    slider.addEventListener("input", update);
    stage.querySelectorAll("[data-set-theme]").forEach((button) => {
      button.addEventListener("click", () => {
        stage.dataset.theme = button.dataset.setTheme;
        stage.querySelectorAll("[data-set-theme]").forEach((other) => {
          other.setAttribute("aria-pressed", String(other === button));
        });
      });
    });
    update();
  }

  // ---- Download: link straight to the newest APK once GitHub says which one it is. ----
  const download = document.getElementById("download");
  if (download && "fetch" in window) {
    fetch("https://api.github.com/repos/FranciscoFrank/EcoWi/releases/latest", {
      headers: { Accept: "application/vnd.github+json" },
    })
      .then((response) => (response.ok ? response.json() : null))
      .then((release) => {
        const apk = release && (release.assets || []).find((asset) => asset.name.endsWith(".apk"));
        if (!apk) return; // no release yet: the button keeps pointing at the releases page
        const size = new Intl.NumberFormat(lang, { maximumFractionDigits: 1 }).format(apk.size / 1048576);
        download.href = apk.browser_download_url;
        download.textContent = download.dataset.label
          .replace("%v", release.tag_name.replace(/^v/, ""))
          .replace("%s", `${size} ${download.dataset.mb}`);
      })
      .catch(() => {});
  }
})();
