(() => {
  const root = document.documentElement;
  const systemTheme = window.matchMedia('(prefers-color-scheme: dark)');
  const themeButton = document.querySelector('.theme-toggle');
  const currentTheme = () => root.dataset.theme || (systemTheme.matches ? 'dark' : 'light');
  const updateThemeLabel = () => {
    const label = `Switch to ${currentTheme() === 'dark' ? 'light' : 'dark'} theme`;
    themeButton.setAttribute('aria-label', label);
    themeButton.title = label;
  };
  themeButton.hidden = false;
  updateThemeLabel();
  systemTheme.addEventListener('change', updateThemeLabel);
  themeButton.addEventListener('click', () => {
    const next = currentTheme() === 'dark' ? 'light' : 'dark';
    root.dataset.theme = next;
    try { localStorage.setItem('wicketlens-theme', next); } catch (_) { /* The toggle still works without storage. */ }
    updateThemeLabel();
  });

  const componentButtons = [...document.querySelectorAll('[data-component]')];
  componentButtons.forEach(button => button.addEventListener('click', () => {
    const selected = button.dataset.component;
    componentButtons.forEach(item => item.setAttribute('aria-pressed', String(item === button)));
    document.querySelectorAll('[data-bind]').forEach(token => token.classList.toggle('is-active', token.dataset.bind === selected));
    document.querySelector('#example-path').textContent = selected === 'form' ? 'form' : 'form:name';
    document.querySelector('#example-type').textContent = selected === 'form' ? 'Form ↔ <form>' : 'TextField ↔ <input>';
  }));

  const screenshots = {
    overview: { file: 'component-hierarchy.png', width: 2880, height: 1440, alt: 'Actual WicketLens sidebar showing matched form fields and their Java source in IntelliJ IDEA', caption: 'An actual plugin view: fields and initialization setup in one component hierarchy.' },
    unpaired: { file: 'unpaired-ids.png', width: 2560, height: 800, alt: 'Actual WicketLens Unpaired filter showing Java-only and markup-only IDs beside HTML editor feedback', caption: 'Keep the parent context while reviewing possible Java-only and markup-only IDs.' },
    repeaters: { file: 'queue-and-repeaters.png', width: 2880, height: 1440, alt: 'Actual WicketLens hierarchy showing queued placement and logical repeater item templates', caption: 'Supported queues and repeaters appear with logical templates, without inventing runtime rows.' },
  };
  const tabs = [...document.querySelectorAll('[data-shot]')];
  const activate = tab => {
    const shot = screenshots[tab.dataset.shot];
    tabs.forEach(item => {
      item.setAttribute('aria-selected', String(item === tab));
      item.tabIndex = item === tab ? 0 : -1;
    });
    const image = document.querySelector('#product-image');
    image.src = `assets/screenshots/${shot.file}`;
    image.alt = shot.alt;
    image.width = shot.width;
    image.height = shot.height;
    document.querySelector('#shot-caption').textContent = shot.caption;
    document.querySelector('#screenshot-panel').setAttribute('aria-labelledby', tab.id);
    document.querySelector('#screenshot-link').href = image.src;
    document.querySelector('#full-size-link').href = image.src;
  };
  tabs.forEach((tab, index) => {
    tab.addEventListener('click', () => activate(tab));
    tab.addEventListener('keydown', event => {
      let next;
      if (event.key === 'ArrowRight') next = tabs[(index + 1) % tabs.length];
      if (event.key === 'ArrowLeft') next = tabs[(index + tabs.length - 1) % tabs.length];
      if (event.key === 'Home') next = tabs[0];
      if (event.key === 'End') next = tabs.at(-1);
      if (!next) return;
      event.preventDefault();
      activate(next);
      next.focus();
    });
  });

  const copyButton = document.querySelector('#copy-build');
  const copyStatus = document.querySelector('#copy-status');
  if (navigator.clipboard && window.isSecureContext) {
    copyButton.hidden = false;
    copyButton.addEventListener('click', async () => {
      try {
        await navigator.clipboard.writeText(document.querySelector('#build-command').textContent.trim());
        copyButton.textContent = 'Copied!';
        copyStatus.textContent = 'Build commands copied to clipboard.';
        window.setTimeout(() => { copyButton.textContent = 'Copy commands'; }, 2200);
      } catch (_) {
        copyStatus.textContent = 'Clipboard unavailable. Select and copy the commands directly.';
        copyButton.textContent = 'Select commands below';
      }
    });
  }
})();
