const REACTIONS = ['♥', '✓', '★', '!', '?'];

const initialState = () => ({
  token: localStorage.getItem('relay.token'),
  me: null,
  workspaces: [],
  workspace: null,
  channels: [],
  people: [],
  online: new Set(),
  channel: null,
  messages: [],
  nextCursor: null,
  panel: null,
  thread: [],
  results: [],
  query: '',
  typing: new Map(),
  stomp: null,
  subs: new Map(),
  timer: null
});

const state = initialState();

const $ = selector => document.querySelector(selector);

function h(tag, props = {}, ...kids) {
  const el = document.createElement(tag);
  for (const [key, value] of Object.entries(props)) {
    if (key === 'class') el.className = value;
    else if (key.startsWith('on')) el.addEventListener(key.slice(2), value);
    else if (value !== false && value != null) el.setAttribute(key, value === true ? '' : value);
  }
  for (const kid of kids.flat()) {
    if (kid != null && kid !== false) el.append(kid.nodeType ? kid : document.createTextNode(kid));
  }
  return el;
}

const initials = name => name.split(/\s+/).filter(Boolean).slice(0, 2).map(word => word[0]).join('').toUpperCase();
const timeOf = iso => new Date(iso).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });

function dayLabel(iso) {
  const date = new Date(iso);
  const today = new Date();
  const yesterday = new Date(today.getTime() - 86400000);
  if (date.toDateString() === today.toDateString()) return 'Today';
  if (date.toDateString() === yesterday.toDateString()) return 'Yesterday';
  return date.toLocaleDateString([], { weekday: 'long', month: 'long', day: 'numeric' });
}

function toast(message) {
  const el = h('div', { class: 'toast', role: 'status' }, message);
  document.body.append(el);
  setTimeout(() => el.remove(), 3200);
}

async function api(path, options = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (state.token) headers.Authorization = 'Bearer ' + state.token;
  const response = await fetch(path, {
    method: options.method || 'GET',
    headers,
    body: options.body ? JSON.stringify(options.body) : undefined
  });
  if (response.status === 401 && state.token) {
    signOut();
    throw new Error('Your session expired. Sign in again.');
  }
  const text = await response.text();
  const data = text ? JSON.parse(text) : null;
  if (!response.ok) throw new Error((data && data.error) || 'Something went wrong. Try again.');
  return data;
}

function ask(title, fields, submit = 'Continue', note = '') {
  return new Promise(resolve => {
    const dialog = h('dialog', { class: 'modal' });
    const controls = fields.map(field => {
      if (field.type === 'checkbox') {
        return h('label', { class: 'check' }, h('input', { type: 'checkbox', name: field.name }), field.label);
      }
      const input = field.type === 'textarea'
        ? h('textarea', { name: field.name, rows: 4 }, field.value || '')
        : h('input', { name: field.name, placeholder: field.placeholder || '', value: field.value || '', autocomplete: 'off' });
      return h('label', {}, h('span', {}, field.label), input);
    });
    const form = h('form', { method: 'dialog' },
      h('h2', {}, title),
      note ? h('p', { class: 'note' }, note) : null,
      controls,
      h('div', { class: 'row end' },
        h('button', { type: 'button', class: 'ghost', onclick: () => dialog.close() }, 'Cancel'),
        h('button', { class: 'solid' }, submit)));
    let result = null;
    form.addEventListener('submit', () => {
      const data = new FormData(form);
      result = Object.fromEntries(fields.map(field => [
        field.name,
        field.type === 'checkbox' ? data.has(field.name) : String(data.get(field.name) || '').trim()
      ]));
    });
    dialog.addEventListener('close', () => {
      dialog.remove();
      resolve(result);
    });
    dialog.append(form);
    document.body.append(dialog);
    dialog.showModal();
  });
}

function renderAuth(mode = 'login') {
  const registering = mode === 'register';
  const error = h('p', { class: 'form-error', role: 'alert' });
  const form = h('form', { class: 'auth-form' },
    registering ? h('label', {}, h('span', {}, 'Name'), h('input', { name: 'displayName', autocomplete: 'name', required: true })) : null,
    h('label', {}, h('span', {}, 'Email'), h('input', { name: 'email', type: 'email', autocomplete: 'email', required: true })),
    h('label', {}, h('span', {}, 'Password'),
      h('input', { name: 'password', type: 'password', required: true, minlength: registering ? 8 : 1, autocomplete: registering ? 'new-password' : 'current-password' })),
    error,
    h('button', { class: 'solid wide' }, registering ? 'Create account' : 'Sign in'));
  form.addEventListener('submit', async event => {
    event.preventDefault();
    error.textContent = '';
    try {
      const body = Object.fromEntries(new FormData(form));
      const result = await api(registering ? '/api/auth/register' : '/api/auth/login', { method: 'POST', body });
      state.token = result.token;
      localStorage.setItem('relay.token', result.token);
      await boot();
    } catch (failure) {
      error.textContent = failure.message;
    }
  });
  $('#root').replaceChildren(h('main', { class: 'auth' },
    h('h1', { class: 'wordmark' }, 'Relay'),
    h('p', { class: 'lede' }, registering ? 'Create an account to start a workspace.' : 'Sign in to your workspace.'),
    form,
    h('button', { class: 'link', onclick: () => renderAuth(registering ? 'login' : 'register') },
      registering ? 'Have an account? Sign in' : 'New here? Create an account')));
}

function signOut() {
  localStorage.removeItem('relay.token');
  clearInterval(state.timer);
  if (state.stomp) state.stomp.deactivate();
  Object.assign(state, initialState());
  renderAuth();
}

function buildShell() {
  $('#root').replaceChildren(h('div', { class: 'app' },
    h('nav', { class: 'rail', id: 'rail', 'aria-label': 'Workspaces' }),
    h('aside', { class: 'side', id: 'side' }),
    h('main', { class: 'main' },
      h('header', { class: 'top', id: 'top' }),
      h('div', { class: 'feed', id: 'feed' }),
      h('div', { class: 'typing', id: 'typing', 'aria-live': 'polite' }),
      composer('main')),
    h('aside', { class: 'panel', id: 'panel', hidden: true })));
}

function composer(target) {
  const input = h('textarea', {
    rows: 1,
    placeholder: target === 'thread' ? 'Reply in thread' : 'Write a message',
    'aria-label': target === 'thread' ? 'Reply in thread' : 'Write a message'
  });
  const form = h('form', { class: 'composer' }, input, h('button', { class: 'solid' }, 'Send'));
  let lastTyping = 0;
  const resize = () => {
    input.style.height = 'auto';
    input.style.height = Math.min(input.scrollHeight, 160) + 'px';
  };
  const submit = () => {
    const content = input.value.trim();
    if (!content || !state.channel) return;
    input.value = '';
    resize();
    send(content, target === 'thread' ? state.thread[0].id : null).catch(failure => toast(failure.message));
  };
  form.addEventListener('submit', event => {
    event.preventDefault();
    submit();
  });
  input.addEventListener('keydown', event => {
    if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
      event.preventDefault();
      submit();
    }
  });
  input.addEventListener('input', () => {
    resize();
    const now = Date.now();
    if (target === 'main' && state.channel && state.stomp && state.stomp.connected && now - lastTyping > 2000) {
      lastTyping = now;
      state.stomp.publish({ destination: `/app/channels/${state.channel.id}/typing` });
    }
  });
  return form;
}

async function send(content, parentId) {
  const body = { content, parentId };
  if (state.stomp && state.stomp.connected) {
    state.stomp.publish({
      destination: `/app/channels/${state.channel.id}/messages`,
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify(body)
    });
    return;
  }
  const message = await api(`/api/channels/${state.channel.id}/messages`, { method: 'POST', body });
  onCreated(state.channel, true, message);
}

const channelTitle = channel => {
  if (channel.type !== 'DM') return channel.name;
  const person = state.people.find(p => p.id === channel.dmUserId);
  return person ? person.displayName : 'Direct message';
};

function renderRail() {
  $('#rail').replaceChildren(
    ...state.workspaces.map(workspace => h('button', {
      class: 'ws-btn' + (state.workspace && workspace.id === state.workspace.id ? ' active' : ''),
      title: workspace.name,
      'aria-label': workspace.name,
      onclick: () => selectWorkspace(workspace.id).catch(failure => toast(failure.message))
    }, initials(workspace.name))),
    h('button', { class: 'ws-btn add', title: 'Add workspace', 'aria-label': 'Add workspace', onclick: addWorkspace }, '+'));
}

const dot = on => h('span', { class: 'dot' + (on ? ' on' : ''), role: 'img', 'aria-label': on ? 'Online' : 'Offline' });

function item(active, unread, onclick, lead, label) {
  return h('button', { class: 'item' + (active ? ' active' : '') + (unread ? ' unread' : ''), onclick },
    lead,
    h('span', { class: 'label' }, label),
    unread ? h('span', { class: 'badge' }, unread > 99 ? '99+' : unread) : null);
}

function renderSide() {
  const side = $('#side');
  const footer = h('div', { class: 'me' }, dot(true), h('span', {}, state.me.displayName), h('button', { class: 'ghost', onclick: signOut }, 'Sign out'));
  const workspace = state.workspace;
  if (!workspace) {
    side.replaceChildren(h('div', { class: 'scroll' }), footer);
    return;
  }
  const activeId = state.channel ? state.channel.id : null;
  const dmByUser = new Map(state.channels.filter(c => c.type === 'DM').map(c => [c.dmUserId, c]));
  side.replaceChildren(
    h('div', { class: 'ws' },
      h('h2', {}, workspace.name),
      h('button', { class: 'ghost', title: 'Copy invite code', onclick: copyInvite }, 'Invite')),
    h('div', { class: 'scroll' },
      h('div', { class: 'group' },
        h('div', { class: 'group-head' }, h('span', {}, 'Channels'), h('button', { class: 'icon', title: 'New channel', 'aria-label': 'New channel', onclick: newChannel }, '+')),
        state.channels.filter(c => c.type !== 'DM').map(c =>
          item(c.id === activeId, c.unread, () => selectChannel(c.id), h('span', { class: 'hash' }, c.type === 'PRIVATE' ? '◦' : '#'), c.name))),
      h('div', { class: 'group' },
        h('div', { class: 'group-head' }, h('span', {}, 'Direct messages')),
        state.people.filter(p => p.id !== state.me.id).map(p => {
          const dm = dmByUser.get(p.id);
          return item(dm && dm.id === activeId, dm ? dm.unread : 0, () => openDm(p.id), dot(state.online.has(p.id)), p.displayName);
        }))),
    footer);
}

function renderTop() {
  const channel = state.channel;
  const parts = [];
  if (channel) {
    parts.push(h('div', { class: 'title' },
      h('h1', {}, (channel.type === 'DM' ? '' : '# ') + channelTitle(channel)),
      channel.type === 'PRIVATE' ? h('span', { class: 'pill' }, 'Private') : null));
  } else {
    parts.push(h('div', { class: 'title' }));
  }
  if (state.workspace) {
    parts.push(h('form', { class: 'search', onsubmit: runSearch },
      h('input', { name: 'q', type: 'search', placeholder: 'Search messages', 'aria-label': 'Search messages', value: state.query, autocomplete: 'off' })));
  }
  $('#top').replaceChildren(...parts);
}

function emptyState() {
  const hasWorkspace = Boolean(state.workspace);
  return h('div', { class: 'blank' },
    h('h2', {}, hasWorkspace ? 'Pick a conversation' : 'No workspace yet'),
    h('p', {}, hasWorkspace ? 'Choose a channel or a person from the sidebar.' : 'Create a workspace, or join one with an invite code.'),
    hasWorkspace ? null : h('button', { class: 'solid', onclick: addWorkspace }, 'Add workspace'));
}

function messageEl(message, compact, inThread) {
  if (message.deleted) return h('article', { class: 'msg deleted' }, h('p', {}, 'This message was deleted.'));
  const mine = message.author.id === state.me.id;
  const head = compact ? null : h('div', { class: 'meta' },
    h('strong', {}, message.author.displayName),
    h('time', { datetime: message.createdAt }, timeOf(message.createdAt)));
  const tools = h('div', { class: 'tools' },
    REACTIONS.map(emoji => h('button', { class: 'tool', title: 'React with ' + emoji, onclick: () => react(message, emoji) }, emoji)),
    inThread ? null : h('button', { class: 'tool', onclick: () => openThread(message) }, 'Reply'),
    mine ? h('button', { class: 'tool', onclick: () => edit(message) }, 'Edit') : null,
    mine ? h('button', { class: 'tool', onclick: () => remove(message) }, 'Delete') : null);
  const chips = message.reactions.length
    ? h('div', { class: 'chips' }, message.reactions.map(reaction =>
      h('button', {
        class: 'chip' + (reaction.userIds.includes(state.me.id) ? ' on' : ''),
        onclick: () => react(message, reaction.emoji)
      }, reaction.emoji, h('span', {}, reaction.count))))
    : null;
  const replies = !inThread && message.replyCount > 0
    ? h('button', { class: 'replies', onclick: () => openThread(message) }, message.replyCount === 1 ? '1 reply' : `${message.replyCount} replies`)
    : null;
  return h('article', { class: 'msg' + (compact ? ' compact' : '') },
    head,
    h('p', { class: 'body' }, message.content, message.editedAt ? h('span', { class: 'edited' }, ' (edited)') : null),
    chips,
    replies,
    tools);
}

function build(list, inThread) {
  const nodes = [];
  let previous = null;
  let day = '';
  for (const message of list) {
    const stamp = new Date(message.createdAt).toDateString();
    if (!inThread && stamp !== day) {
      nodes.push(h('div', { class: 'day' }, h('span', {}, dayLabel(message.createdAt))));
      day = stamp;
      previous = null;
    }
    const compact = Boolean(previous) && !previous.deleted && !message.deleted
      && previous.author.id === message.author.id
      && new Date(message.createdAt) - new Date(previous.createdAt) < 300000;
    nodes.push(messageEl(message, compact, inThread));
    previous = message;
  }
  return nodes;
}

function renderFeed(stick = false) {
  const feed = $('#feed');
  if (!state.channel) {
    feed.replaceChildren(emptyState());
    return;
  }
  const nearBottom = stick || feed.scrollHeight - feed.scrollTop - feed.clientHeight < 140;
  const nodes = [];
  if (state.nextCursor) nodes.push(h('button', { class: 'older', onclick: loadOlder }, 'Load earlier messages'));
  if (!state.messages.length) {
    nodes.push(h('p', { class: 'empty' }, `This is the start of ${channelTitle(state.channel)}. Send the first message.`));
  }
  nodes.push(...build(state.messages, false));
  feed.replaceChildren(...nodes);
  if (nearBottom) feed.scrollTop = feed.scrollHeight;
}

function renderTyping() {
  const now = Date.now();
  const names = [...state.typing.values()].filter(entry => entry.until > now).map(entry => entry.name);
  let text = '';
  if (names.length === 1) text = `${names[0]} is typing…`;
  else if (names.length === 2) text = `${names[0]} and ${names[1]} are typing…`;
  else if (names.length > 2) text = 'Several people are typing…';
  $('#typing').textContent = text;
}

function mountPanel(title) {
  const panel = $('#panel');
  panel.hidden = false;
  const nodes = [
    h('header', { class: 'panel-head' },
      h('h2', {}, title),
      h('button', { class: 'icon', 'aria-label': 'Close panel', onclick: closePanel }, '×')),
    h('div', { class: 'panel-body', id: 'panelBody' })
  ];
  if (state.panel === 'thread') nodes.push(composer('thread'));
  panel.replaceChildren(...nodes);
}

function closePanel() {
  state.panel = null;
  state.thread = [];
  const panel = $('#panel');
  if (!panel) return;
  panel.hidden = true;
  panel.replaceChildren();
}

function resultEl(message) {
  const channel = state.channels.find(c => c.id === message.channelId);
  return h('button', { class: 'result', onclick: () => selectChannel(message.channelId) },
    h('span', { class: 'result-meta' }, h('strong', {}, channel ? channelTitle(channel) : 'Unknown channel'), h('span', {}, message.author.displayName)),
    h('span', { class: 'result-body' }, message.content));
}

function renderPanelBody() {
  const body = $('#panelBody');
  if (!body) return;
  if (state.panel === 'thread') {
    const nearBottom = body.scrollHeight - body.scrollTop - body.clientHeight < 120;
    body.replaceChildren(...build(state.thread, true));
    if (nearBottom) body.scrollTop = body.scrollHeight;
  } else if (state.panel === 'search') {
    body.replaceChildren(...(state.results.length
      ? state.results.map(resultEl)
      : [h('p', { class: 'empty' }, 'No messages match your search.')]));
  }
}

async function openThread(message) {
  state.panel = 'thread';
  state.thread = [];
  mountPanel('Thread');
  try {
    state.thread = await api(`/api/messages/${message.id}/thread`);
    renderPanelBody();
    const field = $('#panel textarea');
    if (field) field.focus();
  } catch (failure) {
    toast(failure.message);
  }
}

async function runSearch(event) {
  event.preventDefault();
  const query = new FormData(event.target).get('q').trim();
  state.query = query;
  if (!query) {
    closePanel();
    return;
  }
  state.panel = 'search';
  state.results = [];
  mountPanel('Search results');
  try {
    state.results = await api(`/api/workspaces/${state.workspace.id}/search?q=${encodeURIComponent(query)}`);
    renderPanelBody();
  } catch (failure) {
    toast(failure.message);
  }
}

async function loadWorkspaces(selectId) {
  state.workspaces = await api('/api/workspaces');
  const target = state.workspaces.find(w => w.id === selectId) || state.workspaces[0];
  if (target) {
    await selectWorkspace(target.id);
  } else {
    state.workspace = null;
    renderRail();
    renderSide();
    renderTop();
    renderFeed();
  }
}

async function refreshPresence() {
  await api('/api/presence/heartbeat', { method: 'POST' });
  const ids = await api(`/api/workspaces/${state.workspace.id}/presence`);
  state.online = new Set(ids);
}

async function selectWorkspace(id) {
  state.workspace = state.workspaces.find(w => w.id === id);
  state.channel = null;
  state.messages = [];
  state.query = '';
  closePanel();
  renderRail();
  const [channels, people] = await Promise.all([
    api(`/api/workspaces/${id}/channels`),
    api(`/api/workspaces/${id}/people`)
  ]);
  state.channels = channels;
  state.people = people;
  await refreshPresence().catch(() => {});
  connectSocket();
  syncSubscriptions();
  renderSide();
  renderTop();
  const first = channels.find(c => c.type !== 'DM') || channels[0];
  if (first) await selectChannel(first.id);
  else renderFeed();
}

async function selectChannel(id) {
  const channel = state.channels.find(c => c.id === id);
  if (!channel) return;
  closePanel();
  state.channel = channel;
  state.typing.clear();
  const page = await api(`/api/channels/${id}/messages?limit=50`);
  if (!state.channel || state.channel.id !== id) return;
  state.messages = page.items;
  state.nextCursor = page.nextCursor;
  channel.unread = 0;
  api(`/api/channels/${id}/read`, { method: 'POST' }).catch(() => {});
  renderSide();
  renderTop();
  renderFeed(true);
  renderTyping();
  const field = $('.main .composer textarea');
  if (field) field.focus();
}

async function loadOlder() {
  const feed = $('#feed');
  const page = await api(`/api/channels/${state.channel.id}/messages?limit=50&before=${state.nextCursor}`);
  const previousHeight = feed.scrollHeight;
  state.messages = [...page.items, ...state.messages];
  state.nextCursor = page.nextCursor;
  renderFeed(false);
  feed.scrollTop += feed.scrollHeight - previousHeight;
}

async function addWorkspace() {
  const values = await ask('Add workspace', [
    { name: 'name', label: 'Create a workspace', placeholder: 'Workspace name' },
    { name: 'code', label: 'Or join with an invite code', placeholder: 'Invite code' }
  ]);
  if (!values || (!values.name && !values.code)) return;
  try {
    const workspace = values.code
      ? await api('/api/workspaces/join', { method: 'POST', body: { inviteCode: values.code } })
      : await api('/api/workspaces', { method: 'POST', body: { name: values.name } });
    await loadWorkspaces(workspace.id);
  } catch (failure) {
    toast(failure.message);
  }
}

async function newChannel() {
  const values = await ask('New channel', [
    { name: 'name', label: 'Name', placeholder: 'launch-plans' },
    { name: 'privateChannel', label: 'Make this channel private', type: 'checkbox' }
  ], 'Create channel');
  if (!values || !values.name) return;
  try {
    const channel = await api(`/api/workspaces/${state.workspace.id}/channels`, {
      method: 'POST',
      body: { name: values.name, privateChannel: values.privateChannel }
    });
    state.channels.push(channel);
    state.channels.sort((a, b) => a.name.localeCompare(b.name));
    syncSubscriptions();
    await selectChannel(channel.id);
  } catch (failure) {
    toast(failure.message);
  }
}

async function openDm(userId) {
  try {
    const channel = await api(`/api/workspaces/${state.workspace.id}/dms`, { method: 'POST', body: { userId } });
    if (!state.channels.some(c => c.id === channel.id)) {
      state.channels.push(channel);
      syncSubscriptions();
    }
    await selectChannel(channel.id);
  } catch (failure) {
    toast(failure.message);
  }
}

function copyInvite() {
  navigator.clipboard.writeText(state.workspace.inviteCode)
    .then(() => toast('Invite code copied'))
    .catch(() => toast('Invite code: ' + state.workspace.inviteCode));
}

async function react(message, emoji) {
  try {
    await api(`/api/messages/${message.id}/reactions`, { method: 'POST', body: { emoji } });
  } catch (failure) {
    toast(failure.message);
  }
}

async function edit(message) {
  const values = await ask('Edit message', [{ name: 'content', label: 'Message', type: 'textarea', value: message.content }], 'Save changes');
  if (!values || !values.content || values.content === message.content) return;
  try {
    await api(`/api/messages/${message.id}`, { method: 'PATCH', body: { content: values.content } });
  } catch (failure) {
    toast(failure.message);
  }
}

async function remove(message) {
  const confirmed = await ask('Delete message', [], 'Delete message', 'This removes the message for everyone in the channel.');
  if (!confirmed) return;
  try {
    await api(`/api/messages/${message.id}`, { method: 'DELETE' });
  } catch (failure) {
    toast(failure.message);
  }
}

function replaceMessage(list, updated) {
  const index = list.findIndex(m => m.id === updated.id);
  if (index >= 0) list[index] = updated;
}

function onCreated(channel, active, message) {
  const mine = message.author.id === state.me.id;
  if (!active) {
    if (!mine) {
      channel.unread += 1;
      renderSide();
    }
    return;
  }
  if (message.parentId == null) {
    if (!state.messages.some(m => m.id === message.id)) state.messages.push(message);
  } else {
    const parent = state.messages.find(m => m.id === message.parentId);
    if (parent) parent.replyCount += 1;
    if (state.panel === 'thread' && state.thread.length && state.thread[0].id === message.parentId
      && !state.thread.some(m => m.id === message.id)) {
      state.thread.push(message);
      state.thread[0].replyCount += 1;
    }
  }
  state.typing.delete(message.author.id);
  renderFeed(mine);
  renderPanelBody();
  renderTyping();
  api(`/api/channels/${channel.id}/read`, { method: 'POST' }).catch(() => {});
}

function onChanged(updated) {
  replaceMessage(state.messages, updated);
  replaceMessage(state.thread, updated);
  renderFeed();
  renderPanelBody();
}

function onReaction(update) {
  for (const list of [state.messages, state.thread]) {
    const target = list.find(m => m.id === update.messageId);
    if (target) target.reactions = update.reactions;
  }
  renderFeed();
  renderPanelBody();
}

function onTyping(person) {
  if (person.userId === state.me.id) return;
  state.typing.set(person.userId, { name: person.displayName, until: Date.now() + 3000 });
  renderTyping();
  setTimeout(renderTyping, 3100);
}

function onEvent(event) {
  const channel = state.channels.find(c => c.id === event.channelId);
  if (!channel) return;
  const active = Boolean(state.channel) && state.channel.id === event.channelId;
  if (event.type === 'message.created') onCreated(channel, active, event.data);
  else if (!active) return;
  else if (event.type === 'message.updated' || event.type === 'message.deleted') onChanged(event.data);
  else if (event.type === 'reaction.updated') onReaction(event.data);
  else if (event.type === 'typing') onTyping(event.data);
}

function connectSocket() {
  if (state.stomp) return;
  const client = new StompJs.Client({
    brokerURL: `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws`,
    connectHeaders: { Authorization: 'Bearer ' + state.token },
    reconnectDelay: 3000
  });
  client.onConnect = () => {
    state.subs.clear();
    syncSubscriptions();
  };
  client.activate();
  state.stomp = client;
}

function syncSubscriptions() {
  const client = state.stomp;
  if (!client || !client.connected) return;
  const wanted = new Set(state.channels.map(c => c.id));
  for (const [id, subscription] of state.subs) {
    if (!wanted.has(id)) {
      subscription.unsubscribe();
      state.subs.delete(id);
    }
  }
  for (const id of wanted) {
    if (!state.subs.has(id)) {
      state.subs.set(id, client.subscribe(`/topic/channel.${id}`, frame => onEvent(JSON.parse(frame.body))));
    }
  }
}

async function tick() {
  if (!state.workspace) return;
  try {
    await refreshPresence();
    const channels = await api(`/api/workspaces/${state.workspace.id}/channels`);
    const activeId = state.channel ? state.channel.id : null;
    state.channels = channels.map(c => (c.id === activeId ? { ...c, unread: 0 } : c));
    if (activeId) state.channel = state.channels.find(c => c.id === activeId) || state.channel;
    syncSubscriptions();
    renderSide();
  } catch (failure) {
    return;
  }
}

async function boot() {
  if (!state.token) {
    renderAuth();
    return;
  }
  try {
    state.me = await api('/api/me');
  } catch (failure) {
    renderAuth();
    return;
  }
  buildShell();
  renderRail();
  renderSide();
  renderTop();
  renderFeed();
  clearInterval(state.timer);
  state.timer = setInterval(tick, 20000);
  try {
    await loadWorkspaces();
  } catch (failure) {
    toast(failure.message);
  }
}

document.addEventListener('keydown', event => {
  if (event.key === 'Escape' && state.panel && !document.querySelector('dialog[open]')) closePanel();
});

boot();
